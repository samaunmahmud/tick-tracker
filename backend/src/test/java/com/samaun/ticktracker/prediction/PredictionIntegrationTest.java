package com.samaun.ticktracker.prediction;

import com.samaun.ticktracker.common.InvalidRequestException;
import com.samaun.ticktracker.ingestion.ElancoClient;
import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;
import com.samaun.ticktracker.sighting.Sighting;
import com.samaun.ticktracker.sighting.SightingRepository;
import com.samaun.ticktracker.sync.RejectedRecordRepository;
import com.samaun.ticktracker.sync.SyncRunRepository;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PredictionIntegrationTest {

    private static final int[] SUMMER_PEAK = {1, 1, 2, 4, 6, 8, 8, 6, 4, 2, 1, 1};
    private static final YearMonth PLANTED_SPIKE = YearMonth.of(2022, 3);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    private ElancoClient elancoClient;

    @Autowired
    private PredictionService predictions;

    @Autowired
    private SightingRepository sightingRepository;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Autowired
    private RejectedRecordRepository rejectedRecordRepository;

    @BeforeEach
    void seedFiveYearsOfSeasonalGrowingData() {
        rejectedRecordRepository.deleteAll();
        syncRunRepository.deleteAll();
        sightingRepository.deleteAll();

        List<Sighting> sightings = new ArrayList<>();
        for (int year = 2019; year <= 2023; year++) {
            double growth = 1 + 0.15 * (year - 2019);
            for (int month = 1; month <= 12; month++) {
                YearMonth yearMonth = YearMonth.of(year, month);
                int leeds = yearMonth.equals(PLANTED_SPIKE) ? 25 : (int) Math.round(SUMMER_PEAK[month - 1] * 2 * growth);
                int bristol = (int) Math.round(SUMMER_PEAK[month - 1] * growth);
                addSightings(sightings, yearMonth, UkCity.LEEDS, leeds);
                addSightings(sightings, yearMonth, UkCity.BRISTOL, bristol);
            }
        }
        sightingRepository.saveAll(sightings);
    }

    private static void addSightings(List<Sighting> sightings, YearMonth month, UkCity city, int count) {
        for (int i = 0; i < count; i++) {
            sightings.add(Sighting.fromUserReport(month.atDay(1 + i).atTime(12, 0), city, TickSpecies.MARSH_TICK,
                    null, Instant.parse("2026-10-30T12:00:00Z")));
        }
    }

    @Test
    void forecastContinuesTheSeasonalShapeWithARange() {
        ForecastResponse response = predictions.forecast("leeds", null, 12);

        assertThat(response.city()).isEqualTo("Leeds");
        assertThat(response.trainedFrom()).isEqualTo(YearMonth.of(2019, 1));
        assertThat(response.trainedTo()).isEqualTo(YearMonth.of(2023, 12));
        assertThat(response.forecast()).hasSize(12);
        assertThat(response.forecast().get(0).month()).isEqualTo(YearMonth.of(2024, 1));

        ForecastPoint january = response.forecast().get(0);
        ForecastPoint july = response.forecast().get(6);
        assertThat(july.predicted()).isGreaterThan(january.predicted() * 4);
        assertThat(response.forecast()).allSatisfy(point -> {
            assertThat(point.low()).isLessThanOrEqualTo(point.predicted());
            assertThat(point.high()).isGreaterThanOrEqualTo(point.predicted());
        });
        assertThat(response.trendPerYear()).isPositive();
        assertThat(response.seasonalStrength()).isGreaterThan(0.5);
        assertThat(response.method()).isNotBlank();
        assertThat(response.caveat()).isNull();
    }

    @Test
    void evaluationBacktestsTheLastYearAgainstBaselines() {
        EvaluationResponse response = predictions.evaluate(null, null);

        assertThat(response.trainedTo()).isEqualTo(YearMonth.of(2022, 12));
        assertThat(response.testedFrom()).isEqualTo(YearMonth.of(2023, 1));
        assertThat(response.testedTo()).isEqualTo(YearMonth.of(2023, 12));
        assertThat(response.scores()).extracting(ModelScore::model).containsExactlyInAnyOrder(
                "seasonal-trend", "same-month-last-year", "historical-monthly-average");
        assertThat(response.scores()).extracting(ModelScore::meanAbsoluteError).isSorted();
        assertThat(response.months()).hasSize(12);
        assertThat(response.bestModel()).isEqualTo(response.scores().get(0).model());
        double average = response.scores().stream()
                .filter(score -> score.model().equals("historical-monthly-average"))
                .findFirst().orElseThrow().meanAbsoluteError();
        double model = response.scores().stream()
                .filter(score -> score.model().equals("seasonal-trend"))
                .findFirst().orElseThrow().meanAbsoluteError();
        assertThat(model).isLessThan(average);
    }

    @Test
    void cityLevelPutsThePlantedSpikeFirst() {
        AnomalyResponse response = predictions.anomalies(0.05, "city");

        assertThat(response.level()).isEqualTo(AnomalyLevel.CITY);
        assertThat(response.anomalies()).isNotEmpty();
        Anomaly top = response.anomalies().get(0);
        assertThat(top.area()).isEqualTo("Leeds");
        assertThat(top.month()).isEqualTo(PLANTED_SPIKE);
        assertThat(top.observed()).isEqualTo(25);
        assertThat(top.direction()).isEqualTo(AnomalyType.SPIKE);
        assertThat(top.adjustedPValue()).isLessThan(0.05);
        assertThat(response.tested()).isPositive();
        assertThat(response.expectedByChanceWithoutCorrection()).isEqualTo(response.tested() * 0.05, within(0.01));
        assertThat(response.caveat()).contains("chance alone would give at most");
    }

    @Test
    void regionLevelFindsTheSpikeInItsRegion() {
        AnomalyResponse response = predictions.anomalies(0.05, "REGION");

        assertThat(response.level()).isEqualTo(AnomalyLevel.REGION);
        assertThat(response.anomalies().get(0).area()).isEqualTo("Yorkshire and the Humber");
        assertThat(response.anomalies().get(0).month()).isEqualTo(PLANTED_SPIKE);
    }

    @Test
    void rejectsUnknownLevel() {
        assertThatThrownBy(() -> predictions.anomalies(0.05, "country"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsUnknownSpecies() {
        assertThatThrownBy(() -> predictions.forecast(null, "Dragon tick", 6))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void forecastWithNoMatchingDataSaysSo() {
        ForecastResponse response = predictions.forecast("Cardiff", null, 6);

        assertThat(response.forecast()).hasSize(6).allSatisfy(point -> assertThat(point.predicted()).isZero());
    }
}
