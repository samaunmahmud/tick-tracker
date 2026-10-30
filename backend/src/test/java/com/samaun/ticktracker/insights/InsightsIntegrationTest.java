package com.samaun.ticktracker.insights;

import com.samaun.ticktracker.common.InvalidRequestException;
import com.samaun.ticktracker.ingestion.ElancoClient;
import com.samaun.ticktracker.ingestion.NormalizedSighting;
import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;
import com.samaun.ticktracker.sighting.ContentHash;
import com.samaun.ticktracker.sighting.Sighting;
import com.samaun.ticktracker.sighting.SightingFilterParams;
import com.samaun.ticktracker.sighting.SightingRepository;
import com.samaun.ticktracker.sighting.SightingSource;
import com.samaun.ticktracker.sync.RejectedRecordRepository;
import com.samaun.ticktracker.sync.SyncRun;
import com.samaun.ticktracker.sync.SyncRunRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class InsightsIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final SightingFilterParams NO_FILTER = new SightingFilterParams(null, null, null, null, null);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    private ElancoClient elancoClient;

    @Autowired
    private InsightsService insights;

    @Autowired
    private SightingRepository sightingRepository;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Autowired
    private RejectedRecordRepository rejectedRecordRepository;

    @BeforeEach
    void seed() {
        rejectedRecordRepository.deleteAll();
        syncRunRepository.deleteAll();
        sightingRepository.deleteAll();
        sightingRepository.saveAll(List.of(
                fromElanco("e1", LocalDateTime.of(2023, 12, 31, 18, 0), UkCity.LONDON, TickSpecies.MARSH_TICK),
                fromUser(LocalDateTime.of(2024, 1, 5, 9, 30), UkCity.LEEDS, TickSpecies.MARSH_TICK),
                fromUser(LocalDateTime.of(2024, 1, 20, 14, 0), UkCity.LEEDS, TickSpecies.MARSH_TICK),
                fromUser(LocalDateTime.of(2024, 3, 10, 8, 15), UkCity.SHEFFIELD, TickSpecies.TREE_HOLE_TICK)));
    }

    private static Sighting fromUser(LocalDateTime sightedAt, UkCity city, TickSpecies species) {
        return Sighting.fromUserReport(sightedAt, city, species, null, NOW);
    }

    private static Sighting fromElanco(String id, LocalDateTime sightedAt, UkCity city, TickSpecies species) {
        return Sighting.fromElanco(
                new NormalizedSighting(id, sightedAt, city, species, ContentHash.of(sightedAt, city, species)), NOW);
    }

    private static SightingFilterParams region(String region) {
        return new SightingFilterParams(null, null, null, region, null);
    }

    @Test
    void summaryCountsEverythingAndZeroFillsSources() {
        InsightsSummary summary = insights.summary(NO_FILTER);

        assertThat(summary.totalSightings()).isEqualTo(4);
        assertThat(summary.firstSighting()).isEqualTo(LocalDateTime.of(2023, 12, 31, 18, 0));
        assertThat(summary.lastSighting()).isEqualTo(LocalDateTime.of(2024, 3, 10, 8, 15));
        assertThat(summary.bySource())
                .containsEntry(SightingSource.ELANCO_API, 1L)
                .containsEntry(SightingSource.USER_REPORT, 3L);
        assertThat(summary.lastSuccessfulSync()).isNull();
    }

    @Test
    void summaryShowsLastSuccessfulSyncOnly() {
        Instant succeededAt = Instant.parse("2026-10-30T10:00:00Z");
        SyncRun succeeded = SyncRun.start(TriggerType.MANUAL, succeededAt.minusSeconds(5));
        succeeded.succeed(succeededAt);
        SyncRun failedLater = SyncRun.start(TriggerType.SCHEDULED, succeededAt.plusSeconds(60));
        failedLater.fail("down", succeededAt.plusSeconds(120));
        syncRunRepository.saveAll(List.of(succeeded, failedLater));

        assertThat(insights.summary(NO_FILTER).lastSuccessfulSync()).isEqualTo(succeededAt);
    }

    @Test
    void summaryOfAnEmptyFilterHasZeroTotalAndNoDates() {
        InsightsSummary summary = insights.summary(new SightingFilterParams(null, null, "Cardiff", null, null));

        assertThat(summary.totalSightings()).isZero();
        assertThat(summary.firstSighting()).isNull();
        assertThat(summary.bySource()).containsEntry(SightingSource.ELANCO_API, 0L);
    }

    @Test
    void countsByRegionLargestFirst() {
        assertThat(insights.countByRegion(NO_FILTER)).containsExactly(
                new CountByKey("Yorkshire and the Humber", 3),
                new CountByKey("London", 1));
    }

    @Test
    void countsByCityWithASpeciesFilter() {
        SightingFilterParams marshTicks = new SightingFilterParams(null, null, null, null, "ixodes apronophorus");

        assertThat(insights.countByCity(marshTicks)).containsExactly(
                new CountByKey("Leeds", 2),
                new CountByKey("London", 1));
    }

    @Test
    void countsBySpeciesWithADateFilter() {
        SightingFilterParams year2024 = new SightingFilterParams("2024-01-01", "2024-12-31", null, null, null);

        assertThat(insights.countBySpecies(year2024)).containsExactly(
                new CountByKey("Marsh tick", 2),
                new CountByKey("Tree-hole tick", 1));
    }

    @Test
    void monthlyTrendFillsMonthsWithNoSightings() {
        assertThat(insights.trends(NO_FILTER, "month"))
                .extracting(TrendPoint::periodStart, TrendPoint::count)
                .containsExactly(
                        tuple(LocalDate.of(2023, 12, 1), 1L),
                        tuple(LocalDate.of(2024, 1, 1), 2L),
                        tuple(LocalDate.of(2024, 2, 1), 0L),
                        tuple(LocalDate.of(2024, 3, 1), 1L));
    }

    @Test
    void weeklyTrendStartsOnMondaysAndHasNoGaps() {
        List<TrendPoint> weeks = insights.trends(NO_FILTER, "WEEK");

        assertThat(weeks.get(0)).isEqualTo(new TrendPoint(LocalDate.of(2023, 12, 25), 1));
        assertThat(weeks.get(weeks.size() - 1)).isEqualTo(new TrendPoint(LocalDate.of(2024, 3, 4), 1));
        assertThat(weeks).hasSize(11);
        for (int i = 1; i < weeks.size(); i++) {
            assertThat(ChronoUnit.DAYS.between(weeks.get(i - 1).periodStart(), weeks.get(i).periodStart())).isEqualTo(7);
        }
        assertThat(weeks).extracting(TrendPoint::count).containsOnly(0L, 1L);
        assertThat(weeks.stream().mapToLong(TrendPoint::count).sum()).isEqualTo(4);
    }

    @Test
    void yearlyTrend() {
        assertThat(insights.trends(NO_FILTER, "year"))
                .extracting(TrendPoint::periodStart, TrendPoint::count)
                .containsExactly(tuple(LocalDate.of(2023, 1, 1), 1L), tuple(LocalDate.of(2024, 1, 1), 3L));
    }

    @Test
    void trendRespectsFilters() {
        assertThat(insights.trends(region("london"), "month"))
                .containsExactly(new TrendPoint(LocalDate.of(2023, 12, 1), 1));
    }

    @Test
    void trendIsEmptyWhenNothingMatches() {
        assertThat(insights.trends(region("Wales"), "month")).isEmpty();
    }

    @Test
    void trendRejectsUnknownInterval() {
        assertThatThrownBy(() -> insights.trends(NO_FILTER, "fortnight"))
                .isInstanceOfSatisfying(InvalidRequestException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(error -> error.field()).containsExactly("interval"));
    }

    @Test
    void seasonalAlwaysHasTwelveMonths() {
        List<SeasonalPoint> seasons = insights.seasonal(NO_FILTER);

        assertThat(seasons).hasSize(12);
        assertThat(seasons.get(0)).isEqualTo(new SeasonalPoint(1, "January", 2));
        assertThat(seasons.get(1)).isEqualTo(new SeasonalPoint(2, "February", 0));
        assertThat(seasons.get(2)).isEqualTo(new SeasonalPoint(3, "March", 1));
        assertThat(seasons.get(11)).isEqualTo(new SeasonalPoint(12, "December", 1));
    }

    @Test
    void seasonalIsAllZeroWhenNothingMatches() {
        assertThat(insights.seasonal(region("Wales"))).hasSize(12).extracting(SeasonalPoint::count).containsOnly(0L);
    }

    @Test
    void rejectsUnknownFilterValues() {
        assertThatThrownBy(() -> insights.countByCity(new SightingFilterParams(null, null, "Atlantis", null, null)))
                .isInstanceOf(InvalidRequestException.class);
    }
}
