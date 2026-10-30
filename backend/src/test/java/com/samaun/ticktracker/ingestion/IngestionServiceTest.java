package com.samaun.ticktracker.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samaun.ticktracker.common.SyncInProgressException;
import com.samaun.ticktracker.config.ClockConfig;
import com.samaun.ticktracker.sighting.Sighting;
import com.samaun.ticktracker.sighting.SightingRepository;
import com.samaun.ticktracker.sync.RejectedRecord;
import com.samaun.ticktracker.sync.RejectedRecordRepository;
import com.samaun.ticktracker.sync.SyncRun;
import com.samaun.ticktracker.sync.SyncRunRepository;
import com.samaun.ticktracker.sync.SyncStatus;
import com.samaun.ticktracker.sync.TriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ClockConfig.UK);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ElancoClient elancoClient = mock(ElancoClient.class);
    private final SightingRepository sightingRepository = mock(SightingRepository.class);
    private final SyncRunRepository syncRunRepository = mock(SyncRunRepository.class);
    private final RejectedRecordRepository rejectedRecordRepository = mock(RejectedRecordRepository.class);

    private final List<Sighting> savedSightings = new ArrayList<>();
    private final List<RejectedRecord> savedRejects = new ArrayList<>();

    private IngestionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionService(elancoClient, new SightingNormalizer(objectMapper, NOW),
                sightingRepository, syncRunRepository, rejectedRecordRepository, NOW);

        when(syncRunRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sightingRepository.saveAll(anyList())).thenAnswer(call -> {
            savedSightings.addAll(call.getArgument(0));
            return call.getArgument(0);
        });
        when(rejectedRecordRepository.saveAll(anyList())).thenAnswer(call -> {
            savedRejects.addAll(call.getArgument(0));
            return call.getArgument(0);
        });
    }

    private ObjectNode record(String id, String date, String location, String species) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("date", date);
        node.put("location", location);
        node.put("species", species);
        return node;
    }

    private ObjectNode validRecord(String id, String date) {
        return record(id, date, "Manchester", "Marsh tick").put("latinName", "Ixodes apronophorus");
    }

    @Test
    void insertsValidRecordsAndMarksRunSuccessful() {
        when(elancoClient.fetchAllSightings()).thenReturn(List.of(
                validRecord("a1", "2022-08-01T06:40:31"),
                validRecord("a2", "2023-05-10T10:00:00")));

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(run.getTriggerType()).isEqualTo(TriggerType.MANUAL);
        assertThat(run.getFetched()).isEqualTo(2);
        assertThat(run.getInserted()).isEqualTo(2);
        assertThat(run.getFinishedAt()).isEqualTo(NOW.instant());
        assertThat(savedSightings).extracting(Sighting::getExternalId).containsExactly("a1", "a2");

        Sighting first = savedSightings.get(0);
        assertThat(first.getSightedAt()).isEqualTo(LocalDateTime.of(2022, 8, 1, 6, 40, 31));
        assertThat(first.getRegion()).isEqualTo("North West");
        assertThat(first.getLatinName()).isEqualTo("Ixodes apronophorus");
    }

    @Test
    void skipsRecordsWithTheSameIdInOneBatch() {
        when(elancoClient.fetchAllSightings()).thenReturn(List.of(
                validRecord("a1", "2022-08-01T06:40:31"),
                validRecord("a1", "2023-05-10T10:00:00")));

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getInserted()).isEqualTo(1);
        assertThat(run.getDuplicates()).isEqualTo(1);
    }

    @Test
    void skipsRecordsWithTheSameContentButDifferentIdsInOneBatch() {
        when(elancoClient.fetchAllSightings()).thenReturn(List.of(
                validRecord("a1", "2022-08-01T06:40:31"),
                record("a2", "2022-08-01T06:40:31", " manchester ", "MARSH TICK")));

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getInserted()).isEqualTo(1);
        assertThat(run.getDuplicates()).isEqualTo(1);
        assertThat(savedSightings).extracting(Sighting::getExternalId).containsExactly("a1");
    }

    @Test
    void skipsRecordsAlreadyInTheDatabase() {
        ObjectNode knownById = validRecord("a1", "2022-08-01T06:40:31");
        ObjectNode knownByContent = validRecord("a2", "2023-05-10T10:00:00");
        ObjectNode brandNew = validRecord("a3", "2024-01-01T09:00:00");
        when(elancoClient.fetchAllSightings()).thenReturn(List.of(knownById, knownByContent, brandNew));

        String knownHash = ((NormalizationResult.Accepted) new SightingNormalizer(objectMapper, NOW)
                .normalize(knownByContent)).sighting().contentHash();
        when(sightingRepository.findExistingExternalIds(anyList())).thenReturn(Set.of("a1"));
        when(sightingRepository.findExistingContentHashes(anyList())).thenReturn(Set.of(knownHash));

        SyncRun run = service.runSync(TriggerType.SCHEDULED);

        assertThat(run.getDuplicates()).isEqualTo(2);
        assertThat(run.getInserted()).isEqualTo(1);
        assertThat(savedSightings).extracting(Sighting::getExternalId).containsExactly("a3");
    }

    @Test
    void savesRejectedRecordsWithRawJsonAndReason() {
        ObjectNode unknownCity = record("bad1", "2022-08-01T06:40:31", "Atlantis", "Marsh tick");
        when(elancoClient.fetchAllSightings()).thenReturn(List.of(
                unknownCity, validRecord("a1", "2022-08-01T06:40:31")));

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getRejected()).isEqualTo(1);
        assertThat(run.getInserted()).isEqualTo(1);
        assertThat(savedRejects).singleElement().satisfies(reject -> {
            assertThat(reject.getExternalId()).isEqualTo("bad1");
            assertThat(reject.getReason()).isEqualTo("Unknown city 'Atlantis'");
            assertThat(reject.getRawJson()).isEqualTo(unknownCity.toString());
        });
    }

    @Test
    void savesRecordsThatAreNotObjectsAsRejected() {
        JsonNode notAnObject = objectMapper.getNodeFactory().textNode("oops");
        when(elancoClient.fetchAllSightings()).thenReturn(List.of(notAnObject));

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getRejected()).isEqualTo(1);
        assertThat(savedRejects).singleElement().satisfies(reject -> {
            assertThat(reject.getExternalId()).isNull();
            assertThat(reject.getRawJson()).isEqualTo("\"oops\"");
        });
    }

    @Test
    void countsRecordsThatNeededCorrecting() {
        when(elancoClient.fetchAllSightings()).thenReturn(List.of(
                record("a1", "2022-08-01T06:40:31", "london", "Marsh tick"),
                validRecord("a2", "2023-05-10T10:00:00")));

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getCorrected()).isEqualTo(1);
        assertThat(savedSightings.get(0).getCity()).isEqualTo("London");
    }

    @Test
    void recordsFailedRunAndKeepsExistingDataWhenApiFails() {
        when(elancoClient.fetchAllSightings()).thenThrow(new ExternalApiException("Elanco API returned HTTP 503"));

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("Elanco API returned HTTP 503");
        assertThat(run.getFinishedAt()).isEqualTo(NOW.instant());
        verify(sightingRepository, never()).saveAll(anyList());
        verify(syncRunRepository, times(2)).save(run);
    }

    @Test
    void recordsFailedRunWhenSavingFails() {
        when(elancoClient.fetchAllSightings()).thenReturn(List.of(validRecord("a1", "2022-08-01T06:40:31")));
        when(sightingRepository.saveAll(anyList())).thenThrow(new IllegalStateException("database is down"));

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("Unexpected error: database is down");
    }

    @Test
    void refusesToStartASecondSyncWhileOneIsRunning() {
        when(elancoClient.fetchAllSightings()).thenAnswer(call -> {
            assertThatThrownBy(() -> service.runSync(TriggerType.SCHEDULED))
                    .isInstanceOf(SyncInProgressException.class);
            return List.of();
        });

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void allowsANewSyncAfterAFailedOne() {
        when(elancoClient.fetchAllSightings())
                .thenThrow(new ExternalApiException("down"))
                .thenReturn(List.of());

        assertThat(service.runSync(TriggerType.MANUAL).getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(service.runSync(TriggerType.MANUAL).getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void looksUpExistingRecordsAndInsertsInChunks() {
        LocalDateTime start = LocalDateTime.of(2020, 1, 1, 0, 0);
        List<JsonNode> records = IntStream.range(0, 1200)
                .mapToObj(i -> (JsonNode) validRecord("id" + i, start.plusMinutes(i).toString() + ":00"))
                .toList();
        when(elancoClient.fetchAllSightings()).thenReturn(records);

        SyncRun run = service.runSync(TriggerType.MANUAL);

        assertThat(run.getInserted()).isEqualTo(1200);
        verify(sightingRepository, times(3)).findExistingExternalIds(anyList());
        verify(sightingRepository, times(3)).findExistingContentHashes(anyList());
        verify(sightingRepository, times(3)).saveAll(anyList());
    }
}
