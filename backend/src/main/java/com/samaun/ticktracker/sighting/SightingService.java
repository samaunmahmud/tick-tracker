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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class SightingService {

    private final SightingRepository repository;
    private final Clock clock;

    public SightingService(SightingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResponse<SightingResponse> search(SightingFilterParams params, String sort, int page, int size) {
        List<FieldError> errors = new ArrayList<>();
        SightingFilter filter = SightingFilter.parse(params, errors);
        Sort.Direction direction = parseSortDirection(sort, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        List<Specification<Sighting>> conditions = new ArrayList<>();
        if (filter.from() != null) {
            conditions.add(SightingSpecifications.sightedOnOrAfter(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add(SightingSpecifications.sightedOnOrBefore(filter.to()));
        }
        if (filter.city() != null) {
            conditions.add(SightingSpecifications.inCity(filter.city()));
        }
        if (filter.region() != null) {
            conditions.add(SightingSpecifications.inRegion(filter.region()));
        }
        if (filter.species() != null) {
            conditions.add(SightingSpecifications.ofSpecies(filter.species()));
        }

        Sort order = Sort.by(direction, "sightedAt").and(Sort.by(direction, "id"));
        return PageResponse.from(repository.findAll(Specification.allOf(conditions), PageRequest.of(page, size, order))
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
        Optional<UkCity> city = SightingFilter.resolveCity("city", request.city(), errors);
        Optional<TickSpecies> species = SightingFilter.resolveSpecies("species", request.species(), errors);

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        String notes = SightingFilter.isPresent(request.notes()) ? request.notes().trim() : null;
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

    private Sort.Direction parseSortDirection(String sort, List<FieldError> errors) {
        if (!SightingFilter.isPresent(sort)) {
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
}
