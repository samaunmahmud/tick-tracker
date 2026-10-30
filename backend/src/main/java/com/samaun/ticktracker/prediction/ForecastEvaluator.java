package com.samaun.ticktracker.prediction;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ForecastEvaluator {

    static final String SEASONAL_TREND = "seasonal-trend";
    static final String SAME_MONTH_LAST_YEAR = "same-month-last-year";
    static final String HISTORICAL_MONTHLY_AVERAGE = "historical-monthly-average";

    private ForecastEvaluator() {
    }

    static Evaluation evaluate(MonthlySeries series, int holdoutMonths) {
        MonthlySeries training = series.first(series.size() - holdoutMonths);
        MonthlySeries test = series.last(holdoutMonths);
        SeasonalTrendModel model = SeasonalTrendModel.fit(training);

        double[] actual = test.counts();
        double[] seasonalTrend = new double[holdoutMonths];
        double[] sameMonthLastYear = new double[holdoutMonths];
        double[] historicalAverage = new double[holdoutMonths];
        List<EvaluationMonth> months = new ArrayList<>();
        for (int i = 0; i < holdoutMonths; i++) {
            YearMonth month = test.monthAt(i);
            seasonalTrend[i] = model.predict(month);
            sameMonthLastYear[i] = training.valueAt(month.minusYears(1));
            historicalAverage[i] = model.monthlyAverage(month.getMonth());
            months.add(new EvaluationMonth(month, actual[i], seasonalTrend[i], sameMonthLastYear[i], historicalAverage[i]));
        }

        List<ModelScore> scores = new ArrayList<>(List.of(
                new ModelScore(SEASONAL_TREND, Stats.meanAbsoluteError(actual, seasonalTrend)),
                new ModelScore(SAME_MONTH_LAST_YEAR, Stats.meanAbsoluteError(actual, sameMonthLastYear)),
                new ModelScore(HISTORICAL_MONTHLY_AVERAGE, Stats.meanAbsoluteError(actual, historicalAverage))));
        scores.sort(Comparator.comparingDouble(ModelScore::meanAbsoluteError));

        return new Evaluation(training.start(), training.end(), test.start(), test.end(), scores, months, model);
    }

    record Evaluation(
            YearMonth trainedFrom,
            YearMonth trainedTo,
            YearMonth testedFrom,
            YearMonth testedTo,
            List<ModelScore> scores,
            List<EvaluationMonth> months,
            SeasonalTrendModel trainedModel
    ) {
        String bestModel() {
            return scores.get(0).model();
        }

        double scoreOf(String model) {
            return scores.stream().filter(score -> score.model().equals(model)).findFirst().orElseThrow()
                    .meanAbsoluteError();
        }
    }
}
