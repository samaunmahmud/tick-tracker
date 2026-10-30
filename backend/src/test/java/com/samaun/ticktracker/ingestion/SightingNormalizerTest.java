package com.samaun.ticktracker.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samaun.ticktracker.config.ClockConfig;
import com.samaun.ticktracker.ingestion.NormalizationResult.Accepted;
import com.samaun.ticktracker.ingestion.NormalizationResult.Rejected;
import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SightingNormalizerTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ClockConfig.UK);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SightingNormalizer normalizer = new SightingNormalizer(objectMapper, NOW);

    private static ExternalSighting sighting(String id, String date, String location, String species, String latinName) {
        return new ExternalSighting(id, date, location, species, latinName);
    }

    private static ExternalSighting validSighting() {
        return sighting("02WNholuSg6ndCk4c1dA", "2022-08-01T06:40:31", "Manchester", "Marsh tick", "Ixodes apronophorus");
    }

    private Accepted accepted(ExternalSighting raw) {
        NormalizationResult result = normalizer.normalize(raw);
        assertThat(result).isInstanceOf(Accepted.class);
        return (Accepted) result;
    }

    private String rejectionReason(ExternalSighting raw) {
        NormalizationResult result = normalizer.normalize(raw);
        assertThat(result).isInstanceOf(Rejected.class);
        return ((Rejected) result).reason();
    }

    @Test
    void acceptsCleanRecordWithoutCorrections() {
        Accepted result = accepted(validSighting());

        NormalizedSighting normalized = result.sighting();
        assertThat(normalized.externalId()).isEqualTo("02WNholuSg6ndCk4c1dA");
        assertThat(normalized.sightedAt()).isEqualTo(LocalDateTime.of(2022, 8, 1, 6, 40, 31));
        assertThat(normalized.city()).isEqualTo(UkCity.MANCHESTER);
        assertThat(normalized.species()).isEqualTo(TickSpecies.MARSH_TICK);
        assertThat(normalized.contentHash()).hasSize(64);
        assertThat(result.wasCorrected()).isFalse();
    }

    @Test
    void rejectsMissingDate() {
        assertThat(rejectionReason(sighting("a", "  ", "Manchester", "Marsh tick", "Ixodes apronophorus")))
                .isEqualTo("Missing date");
    }

    @Test
    void rejectsUnreadableDate() {
        assertThat(rejectionReason(sighting("a", "01/08/2022", "Manchester", "Marsh tick", "Ixodes apronophorus")))
                .contains("Unreadable date '01/08/2022'");
    }

    @Test
    void rejectsDateInTheFuture() {
        assertThat(rejectionReason(sighting("a", "2026-10-30T13:00:01", "Manchester", "Marsh tick", "Ixodes apronophorus")))
                .contains("in the future");
    }

    @Test
    void acceptsDateThatIsExactlyNow() {
        Accepted result = accepted(sighting("a", "2026-10-30T12:00:00", "Manchester", "Marsh tick", "Ixodes apronophorus"));

        assertThat(result.sighting().sightedAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 12, 0, 0));
    }

    @Test
    void usesStartOfDayForDateWithoutTime() {
        Accepted result = accepted(sighting("a", "2022-08-01", "Manchester", "Marsh tick", "Ixodes apronophorus"));

        assertThat(result.sighting().sightedAt()).isEqualTo(LocalDateTime.of(2022, 8, 1, 0, 0, 0));
        assertThat(result.corrections()).singleElement().asString().contains("start of day");
    }

    @Test
    void convertsDateWithOffsetToUkTime() {
        Accepted summer = accepted(sighting("a", "2022-08-01T06:40:31Z", "Manchester", "Marsh tick", "Ixodes apronophorus"));
        Accepted winter = accepted(sighting("b", "2022-01-15T09:00:00+02:00", "Manchester", "Marsh tick", "Ixodes apronophorus"));

        assertThat(summer.sighting().sightedAt()).isEqualTo(LocalDateTime.of(2022, 8, 1, 7, 40, 31));
        assertThat(winter.sighting().sightedAt()).isEqualTo(LocalDateTime.of(2022, 1, 15, 7, 0, 0));
        assertThat(summer.corrections()).singleElement().asString().contains("UK time");
    }

    @Test
    void dropsFractionsOfASecond() {
        Accepted result = accepted(sighting("a", "2022-08-01T06:40:31.987", "Manchester", "Marsh tick", "Ixodes apronophorus"));

        assertThat(result.sighting().sightedAt()).isEqualTo(LocalDateTime.of(2022, 8, 1, 6, 40, 31));
    }

    @Test
    void rejectsMissingLocation() {
        assertThat(rejectionReason(sighting("a", "2022-08-01T06:40:31", null, "Marsh tick", "Ixodes apronophorus")))
                .isEqualTo("Missing location");
    }

    @Test
    void rejectsUnknownCity() {
        assertThat(rejectionReason(sighting("a", "2022-08-01T06:40:31", "Atlantis", "Marsh tick", "Ixodes apronophorus")))
                .isEqualTo("Unknown city 'Atlantis'");
    }

    @Test
    void fixesCityCaseAndWhitespace() {
        Accepted result = accepted(sighting("a", "2022-08-01T06:40:31", "  manchester ", "Marsh tick", "Ixodes apronophorus"));

        assertThat(result.sighting().city()).isEqualTo(UkCity.MANCHESTER);
        assertThat(result.corrections()).singleElement().asString().contains("'Manchester'");
    }

    @Test
    void acceptsNewcastleUponTyneAsNewcastle() {
        Accepted result = accepted(sighting("a", "2022-08-01T06:40:31", "Newcastle upon Tyne", "Marsh tick", "Ixodes apronophorus"));

        assertThat(result.sighting().city()).isEqualTo(UkCity.NEWCASTLE);
        assertThat(result.wasCorrected()).isTrue();
    }

    @Test
    void fillsMissingLatinNameFromSpecies() {
        Accepted result = accepted(sighting("a", "2022-08-01T06:40:31", "Manchester", "Marsh tick", null));

        assertThat(result.sighting().species()).isEqualTo(TickSpecies.MARSH_TICK);
        assertThat(result.corrections()).singleElement().asString().contains("latin name");
    }

    @Test
    void fillsMissingSpeciesFromLatinName() {
        Accepted result = accepted(sighting("a", "2022-08-01T06:40:31", "Manchester", "", "Ixodes arboricola"));

        assertThat(result.sighting().species()).isEqualTo(TickSpecies.TREE_HOLE_TICK);
        assertThat(result.corrections()).singleElement().asString().contains("missing species");
    }

    @Test
    void fixesSpeciesCase() {
        Accepted result = accepted(sighting("a", "2022-08-01T06:40:31", "Manchester", "MARSH TICK", "Ixodes apronophorus"));

        assertThat(result.sighting().species()).isEqualTo(TickSpecies.MARSH_TICK);
        assertThat(result.corrections()).hasSize(1);
    }

    @Test
    void rejectsSpeciesThatDisagreesWithLatinName() {
        assertThat(rejectionReason(sighting("a", "2022-08-01T06:40:31", "Manchester", "Marsh tick", "Ixodes arboricola")))
                .isEqualTo("Species 'Marsh tick' does not match latin name 'Ixodes arboricola'");
    }

    @Test
    void rejectsUnknownSpecies() {
        assertThat(rejectionReason(sighting("a", "2022-08-01T06:40:31", "Manchester", "Deer tick", null)))
                .isEqualTo("Unknown species 'Deer tick'");
    }

    @Test
    void rejectsUnknownLatinNameEvenWhenSpeciesIsKnown() {
        assertThat(rejectionReason(sighting("a", "2022-08-01T06:40:31", "Manchester", "Marsh tick", "Ixodes madeupus")))
                .isEqualTo("Unknown latin name 'Ixodes madeupus'");
    }

    @Test
    void rejectsRecordWithNoSpeciesAtAll() {
        assertThat(rejectionReason(sighting("a", "2022-08-01T06:40:31", "Manchester", null, " ")))
                .isEqualTo("Missing species and latin name");
    }

    @Test
    void allowsMissingIdBecauseContentHashStillCatchesDuplicates() {
        Accepted result = accepted(sighting(" ", "2022-08-01T06:40:31", "Manchester", "Marsh tick", "Ixodes apronophorus"));

        assertThat(result.sighting().externalId()).isNull();
        assertThat(result.sighting().contentHash()).hasSize(64);
    }

    @Test
    void rejectsIdTooLongForTheDatabase() {
        String longId = "x".repeat(SightingNormalizer.MAX_EXTERNAL_ID_LENGTH + 1);

        assertThat(rejectionReason(sighting(longId, "2022-08-01T06:40:31", "Manchester", "Marsh tick", "Ixodes apronophorus")))
                .contains("longer than 64");
    }

    @Test
    void shortensVeryLongValuesInTheReason() {
        String reason = rejectionReason(sighting("a", "2022-08-01T06:40:31", "y".repeat(5000), "Marsh tick", null));

        assertThat(reason).hasSizeLessThan(100).endsWith("...'");
    }

    @Test
    void givesTheSameHashForTheSameSightingWrittenDifferently() {
        String clean = accepted(validSighting()).sighting().contentHash();
        String messy = accepted(sighting("other-id", "2022-08-01T06:40:31.000", " MANCHESTER", "marsh tick", null))
                .sighting().contentHash();

        assertThat(messy).isEqualTo(clean);
    }

    @Test
    void givesDifferentHashesForDifferentSightings() {
        String marsh = accepted(validSighting()).sighting().contentHash();
        String treeHole = accepted(sighting("a", "2022-08-01T06:40:31", "Manchester", "Tree-hole tick", null))
                .sighting().contentHash();

        assertThat(treeHole).isNotEqualTo(marsh);
    }

    @Test
    void acceptsValidJsonRecordAndIgnoresExtraFields() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"id":"a1","date":"2022-08-01T06:40:31","location":"Manchester",
                 "species":"Marsh tick","latinName":"Ixodes apronophorus","newField":"ignored"}
                """);

        assertThat(normalizer.normalize(raw)).isInstanceOf(Accepted.class);
    }

    @Test
    void rejectsJsonRecordWithFieldOfWrongType() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"id":"a1","date":{"day":1},"location":"Manchester","species":"Marsh tick","latinName":"Ixodes apronophorus"}
                """);

        NormalizationResult result = normalizer.normalize(raw);

        assertThat(result).isEqualTo(new Rejected("Field 'date' has the wrong type"));
    }

    @Test
    void rejectsJsonValueThatIsNotAnObject() throws Exception {
        assertThat(normalizer.normalize(objectMapper.readTree("42")))
                .isEqualTo(new Rejected("Record is not a JSON object"));
        assertThat(normalizer.normalize((JsonNode) null))
                .isEqualTo(new Rejected("Record is not a JSON object"));
    }
}
