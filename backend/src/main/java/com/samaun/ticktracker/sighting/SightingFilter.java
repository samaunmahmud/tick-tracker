package com.samaun.ticktracker.sighting;

import com.samaun.ticktracker.common.ApiError.FieldError;
import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

public record SightingFilter(
        LocalDateTime from,
        LocalDateTime to,
        String city,
        String region,
        String species
) {
    private static final String DATE_FORMAT_HINT = "must be a date (2024-01-31) or date-time (2024-01-31T10:00:00)";
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    public static SightingFilter parse(SightingFilterParams params, List<FieldError> errors) {
        LocalDateTime from = parseBound("from", params.from(), LocalTime.MIDNIGHT, errors);
        LocalDateTime to = parseBound("to", params.to(), END_OF_DAY, errors);
        if (from != null && to != null && from.isAfter(to)) {
            errors.add(new FieldError("from", "must not be after 'to'"));
        }

        String city = null;
        if (isPresent(params.city())) {
            city = resolveCity("city", params.city(), errors).map(UkCity::displayName).orElse(null);
        }
        String region = null;
        if (isPresent(params.region())) {
            region = UkCity.findRegion(params.region()).orElse(null);
            if (region == null) {
                errors.add(new FieldError("region", "unknown region '" + params.region() + "'"));
            }
        }
        String species = null;
        if (isPresent(params.species())) {
            species = resolveSpecies("species", params.species(), errors).map(TickSpecies::commonName).orElse(null);
        }
        return new SightingFilter(from, to, city, region, species);
    }

    static Optional<UkCity> resolveCity(String field, String value, List<FieldError> errors) {
        Optional<UkCity> city = UkCity.fromName(value);
        if (city.isEmpty()) {
            errors.add(new FieldError(field, "unknown city '" + value + "'"));
        }
        return city;
    }

    static Optional<TickSpecies> resolveSpecies(String field, String value, List<FieldError> errors) {
        Optional<TickSpecies> species = TickSpecies.fromCommonName(value).or(() -> TickSpecies.fromLatinName(value));
        if (species.isEmpty()) {
            errors.add(new FieldError(field, "unknown species '" + value + "'"));
        }
        return species;
    }

    static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDateTime parseBound(String field, String value, LocalTime timeForDateOnly,
                                            List<FieldError> errors) {
        if (!isPresent(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException notADateTime) {
            try {
                return LocalDate.parse(trimmed).atTime(timeForDateOnly);
            } catch (DateTimeParseException notADate) {
                errors.add(new FieldError(field, DATE_FORMAT_HINT));
                return null;
            }
        }
    }
}
