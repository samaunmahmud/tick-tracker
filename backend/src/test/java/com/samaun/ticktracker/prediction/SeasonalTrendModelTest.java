package com.samaun.ticktracker.prediction;

import org.junit.jupiter.api.Test;

import java.time.Month;
import java.time.YearMonth;

import static com.samaun.ticktracker.prediction.SeriesFixtures.every;
import static com.samaun.ticktracker.prediction.SeriesFixtures.years;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeasonalTrendModelTest {

    private static final double[] SUMMER_PEAK = {1, 1, 2, 4, 6, 8, 8, 6, 4, 2, 1, 1};

    @Test
    void reproducesASeasonalPatternThatRepeatsEveryYear() {
        SeasonalTrendModel model = SeasonalTrendModel.fit(years(2020, SUMMER_PEAK, SUMMER_PEAK, SUMMER_PEAK));

        for (int m = 1; m <= 12; m++) {
            assertThat(model.predict(YearMonth.of(2023, m))).isCloseTo(SUMMER_PEAK[m - 1], within(1e-9));
        }
        assertThat(model.trendPerYear()).isCloseTo(0, within(1e-9));
        assertThat(model.seasonalStrength()).isCloseTo(1, within(1e-9));
    }

    @Test
    void detectsALinearTrendInYearlyTotals() {
        SeasonalTrendModel model = SeasonalTrendModel.fit(years(2020, every(10), every(11), every(12), every(13)));

        assertThat(model.trendPerYear()).isCloseTo(12, within(1e-9));
        assertThat(model.trendRSquared()).isCloseTo(1, within(1e-9));
        assertThat(model.predict(YearMonth.of(2024, 6))).isCloseTo(14, within(1e-9));
        assertThat(model.predict(YearMonth.of(2025, 1))).isCloseTo(15, within(1e-9));
    }

    @Test
    void combinesSeasonalShapeWithTrend() {
        double[] doubled = SUMMER_PEAK.clone();
        for (int i = 0; i < doubled.length; i++) {
            doubled[i] *= 2;
        }
        SeasonalTrendModel model = SeasonalTrendModel.fit(years(2020, SUMMER_PEAK, doubled));

        assertThat(model.trendPerYear()).isCloseTo(44, within(1e-9));
        assertThat(model.predict(YearMonth.of(2022, 7))).isCloseTo(8 * 3, within(1e-9));
        assertThat(model.predict(YearMonth.of(2022, 1))).isCloseTo(1 * 3, within(1e-9));
    }

    @Test
    void spreadIsTheStandardDeviationOfThatCalendarMonth() {
        double[] year1 = every(5);
        double[] year2 = every(5);
        double[] year3 = every(5);
        year1[0] = 2;
        year2[0] = 4;
        year3[0] = 6;

        SeasonalTrendModel model = SeasonalTrendModel.fit(years(2020, year1, year2, year3));

        assertThat(model.spread(Month.JANUARY)).isCloseTo(2, within(1e-9));
        assertThat(model.spread(Month.FEBRUARY)).isZero();
    }

    @Test
    void seasonalStrengthIsZeroWhenMonthsDifferOnlyByNoise() {
        double[] odd = {1, 3, 1, 3, 1, 3, 1, 3, 1, 3, 1, 3};
        double[] even = {3, 1, 3, 1, 3, 1, 3, 1, 3, 1, 3, 1};

        SeasonalTrendModel model = SeasonalTrendModel.fit(years(2020, odd, even));

        assertThat(model.seasonalStrength()).isCloseTo(0, within(1e-9));
        assertThat(model.predict(YearMonth.of(2022, 3))).isCloseTo(2, within(1e-9));
    }

    @Test
    void withoutTwoFullYearsUsesTheAverageLevelAndNoTrend() {
        MonthlySeries eighteenMonths = new MonthlySeries(YearMonth.of(2020, 7),
                new double[]{4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4});

        SeasonalTrendModel model = SeasonalTrendModel.fit(eighteenMonths);

        assertThat(model.hasTrend()).isFalse();
        assertThat(model.fullYears()).isEqualTo(1);
        assertThat(model.predict(YearMonth.of(2022, 1))).isCloseTo(4, within(1e-9));
    }

    @Test
    void neverPredictsBelowZeroWhenTheTrendFalls() {
        SeasonalTrendModel model = SeasonalTrendModel.fit(years(2020, every(9), every(6), every(3)));

        assertThat(model.predict(YearMonth.of(2025, 5))).isZero();
    }
}
