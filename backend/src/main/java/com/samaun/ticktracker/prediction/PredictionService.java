package com.samaun.ticktracker.prediction;

import com.samaun.ticktracker.common.ApiError.FieldError;
import com.samaun.ticktracker.common.InvalidRequestException;
import com.samaun.ticktracker.insights.InsightsService;
import com.samaun.ticktracker.insights.InsightsSummary;
import com.samaun.ticktracker.prediction.ForecastEvaluator.Evaluation;
import com.samaun.ticktracker.reference.UkCity;
import com.samaun.ticktracker.sighting.SightingFilter;
import com.samaun.ticktracker.sighting.SightingFilterParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class PredictionService {

    static final int HOLDOUT_MONTHS = 12;
    private static final int MINIMUM_TRAINING_MONTHS = 24;
    private static final double WEAK_SEASONALITY = 0.2;
    private static final double WEAK_TREND = 0.3;
    private static final double SMALL_MONTHLY_COUNT = 5;

    private static final String FORECAST_METHOD = "Seasonal profile x linear trend: a straight line fitted to yearly "
            + "totals gives next year's total, which is split across months by each calendar month's average share "
            + "of the year. The range is plus or minus one standard deviation of that calendar month's past counts.";
    private static final String EVALUATION_METHOD = "Back-test: the last " + HOLDOUT_MONTHS + " months are hidden, "
            + "each model forecasts them from the earlier months only, and is scored by mean absolute error "
            + "(the average miss in sightings per month, lower is better).";
    private static final String ANOMALY_METHOD = "Poisson test per area and month: the expected count is the mean of "
            + "the same calendar month in that area's other years. A spike's p-value is the chance of seeing at least "
            + "that many, a dip's is the chance of seeing at most that many. p-values are adjusted with "
            + "Benjamini-Hochberg across all tests, and only adjusted p below alpha is flagged.";
    private static final String NO_DATA = "No sightings match these filters yet.";

    private final InsightsService insightsService;

    public PredictionService(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    public ForecastResponse forecast(String city, String species, int months) {
        SightingFilter filter = parseFilter(city, species);
        Optional<MonthlySeries> history = loadSeries(filter.city(), filter.species());
        if (history.isEmpty()) {
            return new ForecastResponse(filter.city(), filter.species(), null, null, 0, 0, List.of(),
                    FORECAST_METHOD, NO_DATA);
        }

        MonthlySeries series = history.get();
        SeasonalTrendModel model = SeasonalTrendModel.fit(series);
        List<ForecastPoint> forecast = new ArrayList<>();
        for (int ahead = 1; ahead <= months; ahead++) {
            YearMonth month = series.end().plusMonths(ahead);
            double predicted = model.predict(month);
            double spread = model.spread(month.getMonth());
            forecast.add(new ForecastPoint(month, round(predicted), round(Math.max(0, predicted - spread)),
                    round(predicted + spread)));
        }

        return new ForecastResponse(filter.city(), filter.species(), series.start(), series.end(),
                round(model.trendPerYear()), round(model.seasonalStrength()), forecast, FORECAST_METHOD,
                joinCaveats(modelCaveats(model)));
    }

    public EvaluationResponse evaluate(String city, String species) {
        SightingFilter filter = parseFilter(city, species);
        Optional<MonthlySeries> history = loadSeries(filter.city(), filter.species());
        if (history.isEmpty() || history.get().size() < HOLDOUT_MONTHS + MINIMUM_TRAINING_MONTHS) {
            String reason = history.isEmpty() ? NO_DATA : "Needs at least "
                    + (HOLDOUT_MONTHS + MINIMUM_TRAINING_MONTHS) + " months of history to back-test.";
            return new EvaluationResponse(filter.city(), filter.species(), null, null, null, null, List.of(), null,
                    List.of(), EVALUATION_METHOD, reason);
        }

        Evaluation evaluation = ForecastEvaluator.evaluate(history.get(), HOLDOUT_MONTHS);
        List<String> caveats = new ArrayList<>();
        if (!evaluation.bestModel().equals(ForecastEvaluator.SEASONAL_TREND)) {
            caveats.add("The simple " + evaluation.bestModel() + " baseline beat the seasonal-trend model ("
                    + format(evaluation.scores().get(0).meanAbsoluteError()) + " vs "
                    + format(evaluation.scoreOf(ForecastEvaluator.SEASONAL_TREND))
                    + " average miss), so the extra model does not earn its keep on this data.");
        }
        caveats.addAll(modelCaveats(evaluation.trainedModel()));

        List<ModelScore> scores = evaluation.scores().stream()
                .map(score -> new ModelScore(score.model(), round(score.meanAbsoluteError())))
                .toList();
        List<EvaluationMonth> months = evaluation.months().stream()
                .map(month -> new EvaluationMonth(month.month(), month.actual(), round(month.seasonalTrend()),
                        month.sameMonthLastYear(), round(month.historicalMonthlyAverage())))
                .toList();

        return new EvaluationResponse(filter.city(), filter.species(), evaluation.trainedFrom(),
                evaluation.trainedTo(), evaluation.testedFrom(), evaluation.testedTo(), scores,
                evaluation.bestModel(), months, EVALUATION_METHOD, joinCaveats(caveats));
    }

    public AnomalyResponse anomalies(double alpha, String levelName) {
        AnomalyLevel level = AnomalyLevel.fromName(levelName).orElseThrow(() -> new InvalidRequestException(
                List.of(new FieldError("level", "must be 'city' or 'region'"))));
        Optional<YearMonth[]> range = dataRange();
        if (range.isEmpty()) {
            return new AnomalyResponse(level, alpha, 0, 0, 0, 0, 0, List.of(), ANOMALY_METHOD, NO_DATA);
        }

        Map<String, MonthlySeries> seriesByArea = new LinkedHashMap<>();
        for (String area : areas(level)) {
            String city = level == AnomalyLevel.CITY ? area : null;
            String region = level == AnomalyLevel.REGION ? area : null;
            SightingFilterParams params = new SightingFilterParams(null, null, city, region, null);
            seriesByArea.put(area, MonthlySeries.covering(range.get()[0], range.get()[1],
                    insightsService.trends(params, "month")));
        }

        AnomalyDetector.Detection detection = AnomalyDetector.detect(seriesByArea, alpha);
        double expectedByChance = detection.tested() * alpha;
        double totalCount = seriesByArea.values().stream().mapToDouble(MonthlySeries::total).sum();
        int areaMonths = seriesByArea.values().stream().mapToInt(MonthlySeries::size).sum();

        List<Anomaly> anomalies = detection.flagged().stream()
                .map(a -> new Anomaly(a.area(), a.month(), a.observed(), round(a.expected()),
                        significant(a.pValue()), significant(a.adjustedPValue()), a.direction()))
                .toList();
        return new AnomalyResponse(level, alpha, detection.tested(), detection.skipped(), anomalies.size(),
                detection.flaggedWithoutCorrection(), round(expectedByChance), anomalies, ANOMALY_METHOD,
                anomalyCaveat(level, detection, expectedByChance, totalCount / areaMonths));
    }

    private String anomalyCaveat(AnomalyLevel level, AnomalyDetector.Detection detection, double expectedByChance,
                                 double averagePerAreaMonth) {
        List<String> caveats = new ArrayList<>();
        if (detection.flagged().isEmpty()) {
            caveats.add("Nothing stands out after correcting for " + detection.tested() + " tests: the ups and "
                    + "downs are what random variation alone would produce.");
        } else {
            caveats.add(detection.flagged().size() + " of " + detection.tested()
                    + " area-months are unlikely to be chance even after correcting for the number of tests.");
        }
        caveats.add("Without correction " + detection.flaggedWithoutCorrection() + " would have looked significant; "
                + "chance alone would give at most about " + format(expectedByChance) + " (fewer with small counts, "
                + "because a count of 0, 1 or 2 often cannot reach p below alpha at all).");
        if (averagePerAreaMonth < 1) {
            caveats.add("Counts are small (about " + format(averagePerAreaMonth) + " per "
                    + level.name().toLowerCase(Locale.ROOT) + " per month), so only big jumps can reach "
                    + "significance" + (level == AnomalyLevel.CITY ? "; level=region has more data per test." : "."));
        }
        if (detection.skipped() > 0) {
            caveats.add(detection.skipped() + " area-months were skipped because that calendar month had no "
                    + "sightings in any other year, so there is no baseline to compare with.");
        }
        caveats.add("The Poisson test assumes sightings arrive independently; clusters of reports from one "
                + "event would make it flag too often.");
        return String.join(" ", caveats);
    }

    private static List<String> areas(AnomalyLevel level) {
        return Arrays.stream(UkCity.values())
                .map(level == AnomalyLevel.CITY ? UkCity::displayName : UkCity::region)
                .distinct()
                .toList();
    }

    private List<String> modelCaveats(SeasonalTrendModel model) {
        List<String> caveats = new ArrayList<>();
        if (model.seasonalStrength() < WEAK_SEASONALITY) {
            caveats.add("Weak seasonality: the calendar month explains only "
                    + Math.round(model.seasonalStrength() * 100) + "% of the variation between months, so the "
                    + "forecast is close to a flat average.");
        }
        if (!model.hasTrend()) {
            caveats.add("Fewer than two full years of data, so no trend was fitted.");
        } else if (model.trendRSquared() < WEAK_TREND) {
            caveats.add("Yearly totals do not follow a clear trend (R squared " + format(model.trendRSquared())
                    + "), so the trend part of the forecast is uncertain.");
        }
        if (model.averagePerMonth() < SMALL_MONTHLY_COUNT) {
            caveats.add("Small numbers: about " + format(model.averagePerMonth())
                    + " sightings a month, so random noise is large compared with any pattern.");
        }
        return caveats;
    }

    private SightingFilter parseFilter(String city, String species) {
        List<FieldError> errors = new ArrayList<>();
        SightingFilter filter = SightingFilter.parse(new SightingFilterParams(null, null, city, null, species), errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return filter;
    }

    private Optional<MonthlySeries> loadSeries(String city, String species) {
        return dataRange().map(range -> seriesFor(city, species, range));
    }

    private MonthlySeries seriesFor(String city, String species, YearMonth[] range) {
        SightingFilterParams params = new SightingFilterParams(null, null, city, null, species);
        return MonthlySeries.covering(range[0], range[1], insightsService.trends(params, "month"));
    }

    private Optional<YearMonth[]> dataRange() {
        InsightsSummary everything = insightsService.summary(new SightingFilterParams(null, null, null, null, null));
        if (everything.totalSightings() == 0) {
            return Optional.empty();
        }
        return Optional.of(new YearMonth[]{
                YearMonth.from(everything.firstSighting()), YearMonth.from(everything.lastSighting())});
    }

    private static String joinCaveats(List<String> caveats) {
        return caveats.isEmpty() ? null : String.join(" ", caveats);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static double significant(double value) {
        return value == 0 ? 0 : new BigDecimal(value).round(new MathContext(4)).doubleValue();
    }

    private static String format(double value) {
        return String.format(Locale.UK, "%.2f", value);
    }
}
