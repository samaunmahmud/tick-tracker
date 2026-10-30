package com.samaun.ticktracker.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samaun.ticktracker.config.ClockConfig;
import com.samaun.ticktracker.ingestion.NormalizationResult.Accepted;
import com.samaun.ticktracker.ingestion.NormalizationResult.Rejected;
import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;
import com.samaun.ticktracker.sighting.ContentHash;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class SightingNormalizer {

    static final int MAX_EXTERNAL_ID_LENGTH = 64;
    private static final int MAX_QUOTED_LENGTH = 60;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SightingNormalizer(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public NormalizationResult normalize(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            return new Rejected("Record is not a JSON object");
        }
        ExternalSighting sighting;
        try {
            sighting = objectMapper.treeToValue(raw, ExternalSighting.class);
        } catch (JsonMappingException e) {
            return new Rejected(e.getPath().isEmpty()
                    ? "Record has fields of the wrong type"
                    : "Field '" + e.getPath().get(0).getFieldName() + "' has the wrong type");
        } catch (JsonProcessingException e) {
            return new Rejected("Record could not be read");
        }
        return normalize(sighting);
    }

    public NormalizationResult normalize(ExternalSighting raw) {
        List<String> corrections = new ArrayList<>();
        try {
            String externalId = parseId(raw.id());
            LocalDateTime sightedAt = parseDate(raw.date(), corrections);
            UkCity city = resolveCity(raw.location(), corrections);
            TickSpecies species = resolveSpecies(raw.species(), raw.latinName(), corrections);

            NormalizedSighting sighting = new NormalizedSighting(
                    externalId, sightedAt, city, species, ContentHash.of(sightedAt, city, species));
            return new Accepted(sighting, List.copyOf(corrections));
        } catch (InvalidRecordException e) {
            return new Rejected(e.getMessage());
        }
    }

    private String parseId(String id) {
        if (isBlank(id)) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw new InvalidRecordException("Id is longer than " + MAX_EXTERNAL_ID_LENGTH + " characters");
        }
        return trimmed;
    }

    private LocalDateTime parseDate(String date, List<String> corrections) {
        if (isBlank(date)) {
            throw new InvalidRecordException("Missing date");
        }
        String value = date.trim();
        LocalDateTime sightedAt = parseLocalDateTime(value)
                .or(() -> parseWithOffset(value, corrections))
                .or(() -> parseDateOnly(value, corrections))
                .orElseThrow(() -> new InvalidRecordException("Unreadable date " + quote(value)))
                .truncatedTo(ChronoUnit.SECONDS);

        if (sightedAt.isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRecordException("Date " + quote(value) + " is in the future");
        }
        return sightedAt;
    }

    private Optional<LocalDateTime> parseLocalDateTime(String value) {
        try {
            return Optional.of(LocalDateTime.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> parseWithOffset(String value, List<String> corrections) {
        try {
            LocalDateTime ukTime = OffsetDateTime.parse(value)
                    .atZoneSameInstant(ClockConfig.UK)
                    .toLocalDateTime();
            corrections.add("Converted date " + quote(value) + " to UK time");
            return Optional.of(ukTime);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> parseDateOnly(String value, List<String> corrections) {
        try {
            LocalDateTime startOfDay = LocalDate.parse(value).atStartOfDay();
            corrections.add("Date " + quote(value) + " had no time, used start of day");
            return Optional.of(startOfDay);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private UkCity resolveCity(String location, List<String> corrections) {
        if (isBlank(location)) {
            throw new InvalidRecordException("Missing location");
        }
        UkCity city = UkCity.fromName(location)
                .orElseThrow(() -> new InvalidRecordException("Unknown city " + quote(location)));
        if (!location.equals(city.displayName())) {
            corrections.add("Location " + quote(location) + " changed to " + quote(city.displayName()));
        }
        return city;
    }

    private TickSpecies resolveSpecies(String commonName, String latinName, List<String> corrections) {
        boolean hasCommonName = !isBlank(commonName);
        boolean hasLatinName = !isBlank(latinName);

        if (!hasCommonName && !hasLatinName) {
            throw new InvalidRecordException("Missing species and latin name");
        }

        Optional<TickSpecies> byCommonName = hasCommonName ? TickSpecies.fromCommonName(commonName) : Optional.empty();
        Optional<TickSpecies> byLatinName = hasLatinName ? TickSpecies.fromLatinName(latinName) : Optional.empty();

        if (hasCommonName && byCommonName.isEmpty()) {
            throw new InvalidRecordException("Unknown species " + quote(commonName));
        }
        if (hasLatinName && byLatinName.isEmpty()) {
            throw new InvalidRecordException("Unknown latin name " + quote(latinName));
        }

        if (hasCommonName && hasLatinName) {
            TickSpecies species = byCommonName.get();
            if (species != byLatinName.get()) {
                throw new InvalidRecordException("Species " + quote(commonName)
                        + " does not match latin name " + quote(latinName));
            }
            noteCaseFix(commonName, species.commonName(), corrections);
            noteCaseFix(latinName, species.latinName(), corrections);
            return species;
        }

        if (hasCommonName) {
            TickSpecies species = byCommonName.get();
            noteCaseFix(commonName, species.commonName(), corrections);
            corrections.add("Filled in missing latin name " + quote(species.latinName()));
            return species;
        }

        TickSpecies species = byLatinName.get();
        noteCaseFix(latinName, species.latinName(), corrections);
        corrections.add("Filled in missing species " + quote(species.commonName()));
        return species;
    }

    private void noteCaseFix(String original, String canonical, List<String> corrections) {
        if (!original.equals(canonical)) {
            corrections.add(quote(original) + " changed to " + quote(canonical));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String quote(String value) {
        String shown = value.length() > MAX_QUOTED_LENGTH ? value.substring(0, MAX_QUOTED_LENGTH) + "..." : value;
        return "'" + shown + "'";
    }

    private static class InvalidRecordException extends RuntimeException {
        InvalidRecordException(String reason) {
            super(reason);
        }
    }
}
