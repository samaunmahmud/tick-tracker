package com.samaun.ticktracker.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samaun.ticktracker.sighting.SightingRepository;
import com.samaun.ticktracker.sync.RejectedRecordRepository;
import com.samaun.ticktracker.sync.SyncRun;
import com.samaun.ticktracker.sync.SyncRunRepository;
import com.samaun.ticktracker.sync.SyncStatus;
import com.samaun.ticktracker.sync.TriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class IngestionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    private ElancoClient elancoClient;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private SightingRepository sightingRepository;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Autowired
    private RejectedRecordRepository rejectedRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        rejectedRecordRepository.deleteAll();
        syncRunRepository.deleteAll();
        sightingRepository.deleteAll();
    }

    private List<JsonNode> feed() throws Exception {
        return List.of(
                objectMapper.readTree("""
                        {"id":"a1","date":"2022-08-01T06:40:31","location":"Manchester","species":"Marsh tick","latinName":"Ixodes apronophorus"}"""),
                objectMapper.readTree("""
                        {"id":"a2","date":"2023-05-10T10:00:00","location":"london","species":"Tree-hole tick"}"""),
                objectMapper.readTree("""
                        {"id":"bad1","date":"2022-08-01T06:40:31","location":"Atlantis","species":"Marsh tick"}"""));
    }

    @Test
    void firstSyncStoresDataAndSecondSyncOnlyFindsDuplicates() throws Exception {
        when(elancoClient.fetchAllSightings()).thenReturn(feed());

        SyncRun first = ingestionService.runSync(TriggerType.MANUAL);
        SyncRun second = ingestionService.runSync(TriggerType.MANUAL);

        assertThat(first.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(first.getInserted()).isEqualTo(2);
        assertThat(first.getRejected()).isEqualTo(1);
        assertThat(first.getCorrected()).isEqualTo(1);

        assertThat(second.getInserted()).isZero();
        assertThat(second.getDuplicates()).isEqualTo(2);

        assertThat(sightingRepository.count()).isEqualTo(2);
        assertThat(syncRunRepository.findTop20ByOrderByStartedAtDesc()).hasSize(2);
        assertThat(rejectedRecordRepository.findAll())
                .extracting(reject -> reject.getSyncRunId())
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }
}
