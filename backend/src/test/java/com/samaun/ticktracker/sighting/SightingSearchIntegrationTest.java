package com.samaun.ticktracker.sighting;

import com.samaun.ticktracker.common.PageResponse;
import com.samaun.ticktracker.ingestion.ElancoClient;
import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SightingSearchIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    private ElancoClient elancoClient;

    @Autowired
    private SightingRepository repository;

    @Autowired
    private SightingService service;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        Instant now = Instant.parse("2026-10-30T12:00:00Z");
        repository.saveAll(List.of(
                Sighting.fromUserReport(LocalDateTime.of(2024, 1, 1, 0, 0, 0), UkCity.LEEDS, TickSpecies.MARSH_TICK, null, now),
                Sighting.fromUserReport(LocalDateTime.of(2024, 1, 31, 23, 59, 59), UkCity.SHEFFIELD, TickSpecies.MARSH_TICK, null, now),
                Sighting.fromUserReport(LocalDateTime.of(2024, 2, 1, 0, 0, 0), UkCity.LEEDS, TickSpecies.TREE_HOLE_TICK, null, now),
                Sighting.fromUserReport(LocalDateTime.of(2023, 6, 15, 12, 0, 0), UkCity.LONDON, TickSpecies.MARSH_TICK, null, now)));
    }

    private List<String> cities(PageResponse<SightingResponse> page) {
        return page.content().stream().map(SightingResponse::city).toList();
    }

    private SightingSearchParams params(String from, String to, String city, String region, String species, String sort) {
        return new SightingSearchParams(from, to, city, region, species, sort);
    }

    @Test
    void dateOnlyBoundsIncludeTheWholeOfBothDays() {
        PageResponse<SightingResponse> january = service.search(params("2024-01-01", "2024-01-31", null, null, null, "oldest"), 0, 20);

        assertThat(cities(january)).containsExactly("Leeds", "Sheffield");
    }

    @Test
    void filtersByRegionAndSpeciesTogether() {
        PageResponse<SightingResponse> result = service.search(
                params(null, null, null, "yorkshire and the humber", "Ixodes apronophorus", null), 0, 20);

        assertThat(cities(result)).containsExactly("Sheffield", "Leeds");
    }

    @Test
    void filtersByCity() {
        assertThat(cities(service.search(params(null, null, "LONDON", null, null, null), 0, 20)))
                .containsExactly("London");
    }

    @Test
    void sortsNewestFirstAndPages() {
        PageResponse<SightingResponse> firstPage = service.search(params(null, null, null, null, null, null), 0, 3);
        PageResponse<SightingResponse> secondPage = service.search(params(null, null, null, null, null, null), 1, 3);

        assertThat(firstPage.content()).extracting(SightingResponse::sightedAt)
                .containsExactly(LocalDateTime.of(2024, 2, 1, 0, 0), LocalDateTime.of(2024, 1, 31, 23, 59, 59),
                        LocalDateTime.of(2024, 1, 1, 0, 0));
        assertThat(cities(secondPage)).containsExactly("London");
        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }
}
