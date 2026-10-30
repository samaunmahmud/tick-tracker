package com.samaun.ticktracker.prediction;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static com.samaun.ticktracker.prediction.SeriesFixtures.every;
import static com.samaun.ticktracker.prediction.SeriesFixtures.years;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ForecastEvaluatorTest {

    @Test
    void scoresEachModelOnTheHiddenLastYear() {
        ForecastEvaluator.Evaluation evaluation = ForecastEvaluator.evaluate(
                years(2020, every(10), every(11), every(12), every(13)), 12);

        assertThat(evaluation.trainedTo()).isEqualTo(YearMonth.of(2022, 12));
        assertThat(evaluation.testedFrom()).isEqualTo(YearMonth.of(2023, 1));
        assertThat(evaluation.scoreOf(ForecastEvaluator.SEASONAL_TREND)).isCloseTo(0, within(1e-9));
        assertThat(evaluation.scoreOf(ForecastEvaluator.SAME_MONTH_LAST_YEAR)).isEqualTo(1);
        assertThat(evaluation.scoreOf(ForecastEvaluator.HISTORICAL_MONTHLY_AVERAGE)).isEqualTo(2);
        assertThat(evaluation.bestModel()).isEqualTo(ForecastEvaluator.SEASONAL_TREND);
        assertThat(evaluation.months()).hasSize(12);
        assertThat(evaluation.months().get(0).actual()).isEqualTo(13);
    }

    @Test
    void reportsHonestlyWhenABaselineWins() {
        ForecastEvaluator.Evaluation evaluation = ForecastEvaluator.evaluate(
                years(2020, every(5), every(20), every(5), every(5)), 12);

        assertThat(evaluation.bestModel()).isEqualTo(ForecastEvaluator.SAME_MONTH_LAST_YEAR);
        assertThat(evaluation.scoreOf(ForecastEvaluator.SAME_MONTH_LAST_YEAR)).isZero();
        assertThat(evaluation.scoreOf(ForecastEvaluator.SEASONAL_TREND)).isCloseTo(5, within(1e-9));
        assertThat(evaluation.scoreOf(ForecastEvaluator.HISTORICAL_MONTHLY_AVERAGE)).isEqualTo(5);
    }

    @Test
    void scoresAreSortedBestFirst() {
        ForecastEvaluator.Evaluation evaluation = ForecastEvaluator.evaluate(
                years(2020, every(10), every(11), every(12), every(13)), 12);

        assertThat(evaluation.scores()).extracting(ModelScore::meanAbsoluteError).isSorted();
    }
}
