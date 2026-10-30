package com.samaun.ticktracker.sighting;

import com.samaun.ticktracker.common.ApiError.FieldError;
import com.samaun.ticktracker.common.DuplicateSightingException;
import com.samaun.ticktracker.common.InvalidRequestException;
import com.samaun.ticktracker.common.NotFoundException;
import com.samaun.ticktracker.common.PageResponse;
import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class SightingService {

    private static final String DATE_FORMAT_HINT = "must be a date (2024-01-31) or date-time (2024-01-31T10:00:00)";
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final SightingRepository repository;
    private final Clock clock;

    public SightingService(SightingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResponse<SightingResponse> search(SightingSearchParams params, int page, int size) {
        List<FieldError> errors = new ArrayList<>();
        List<Specification<Sighting>> filters = new ArrayList<>();

        Optional<LocalDateTime> from = parseBound("from", params.from(), LocalTime.MIDNIGHT, errors);
        Optional<LocalDateTime> to = parseBound("to", params.to(), END_OF_DAY, errors);
        if (from.isPresent() && to.isPresent() && from.get().isAfter(to.get())) {
            errors.add(new FieldError("from", "must not be after 'to'"));
        }
        from.ifPresent(value -> filters.add(SightingSpecifications.sightedOnOrAfter(value)));
        to.ifPresent(value -> filters.add(SightingSpecifications.sightedOnOrBefore(value)));

        if (isPresent(params.city())) {
            resolveCity("city", params.city(), errors)
                    .ifPresent(city -> filters.add(SightingSpecifications.inCity(city.displayName())));
        }
        if (isPresent(params.region())) {
            UkCity.findRegion(params.region()).ifPresentOrElse(
                    region -> filters.add(SightingSpecifications.inRegion(region)),
                    () -> errors.add(new FieldError("region", "unknown region '" + params.region() + "'")));
        }
        if (isPresent(params.species())) {
            resolveSpecies("species", params.species(), errors)
                    .ifPresent(species -> filters.add(SightingSpecifications.ofSpecies(species.commonName())));
        }
        Sort.Direction direction = parseSortDirection(params.sort(), errors);

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        Sort sort = Sort.by(direction, "sightedAt").and(Sort.by(direction, "id"));
        return PageResponse.from(repository.findAll(Specification.allOf(filters), PageRequest.of(page, size, sort))
                .map(SightingResponse::from));
    }

    public SightingResponse findById(UUID id) {
        return repository.findById(id)
                .map(SightingResponse::from)
                .orElseThrow(() -> new NotFoundException("No sighting with id " + id));
    }

    public SightingResponse report(SightingReportRequest request) {
        List<FieldError> errors = new ArrayList<>();
        LocalDateTime sightedAt = request.sightedAt().truncatedTo(ChronoUnit.SECONDS);
        if (sightedAt.isAfter(LocalDateTime.now(clock))) {
            errors.add(new FieldError("sightedAt", "must not be in the future"));
        }
        Optional<UkCity> city = resolveCity("city", request.city(), errors);
        Optional<TickSpecies> species = resolveSpecies("species", request.species(), errors);

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        String notes = isPresent(request.notes()) ? request.notes().trim() : null;
        Sighting sighting = Sighting.fromUserReport(sightedAt, city.get(), species.get(), notes, clock.instant());

        if (repository.existsByContentHash(sighting.getContentHash())) {
            throw new DuplicateSightingException();
        }
        try {
            return SightingResponse.from(repository.save(sighting));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateSightingException();
        }
    }

    private Optional<LocalDateTime> parseBound(String field, String value, LocalTime timeForDateOnly,
                                               List<FieldError> errors) {
        if (!isPresent(value)) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        try {
            return Optional.of(LocalDateTime.parse(trimmed));
        } catch (DateTimeParseException notADateTime) {
            try {
                return Optional.of(LocalDate.parse(trimmed).atTime(timeForDateOnly));
            } catch (DateTimeParseException notADate) {
                errors.add(new FieldError(field, DATE_FORMAT_HINT));
                return Optional.empty();
            }
        }
    }

    private Optional<UkCity> resolveCity(String field, String value, List<FieldError> errors) {
        Optional<UkCity> city = UkCity.fromName(value);
        if (city.isEmpty()) {
            errors.add(new FieldError(field, "unknown city '" + value + "'"));
        }
        return city;
    }

    private Optional<TickSpecies> resolveSpecies(String field, String value, List<FieldError> errors) {
        Optional<TickSpecies> species = TickSpecies.fromCommonName(value).or(() -> TickSpecies.fromLatinName(value));
        if (species.isEmpty()) {
            errors.add(new FieldError(field, "unknown species '" + value + "'"));
        }
        return species;
    }

    private Sort.Direction parseSortDirection(String sort, List<FieldError> errors) {
        if (!isPresent(sort)) {
            return Sort.Direction.DESC;
        }
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "newest" -> Sort.Direction.DESC;
            case "oldest" -> Sort.Direction.ASC;
            default -> {
                errors.add(new FieldError("sort", "must be 'newest' or 'oldest'"));
                yield Sort.Direction.DESC;
            }
        };
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
