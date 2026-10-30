package com.samaun.ticktracker.prediction;

import com.samaun.ticktracker.prediction.Stats.LinearFit;

import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

final class SeasonalTrendModel {

    private final double[] monthlyAverage;
    private final double[] monthlySpread;
    private final double[] seasonalIndex;
    private final LinearFit yearlyTrend;
    private final int fullYears;
    private final double averagePerMonth;
    private final double seasonalStrength;

    private SeasonalTrendModel(double[] monthlyAverage, double[] monthlySpread, double[] seasonalIndex,
                               LinearFit yearlyTrend, int fullYears, double averagePerMonth, double seasonalStrength) {
        this.monthlyAverage = monthlyAverage;
        this.monthlySpread = monthlySpread;
        this.seasonalIndex = seasonalIndex;
        this.yearlyTrend = yearlyTrend;
        this.fullYears = fullYears;
        this.averagePerMonth = averagePerMonth;
        this.seasonalStrength = seasonalStrength;
    }

    static SeasonalTrendModel fit(MonthlySeries series) {
        double[] monthlyAverage = new double[12];
        double[] monthlySpread = new double[12];
        for (Month month : Month.values()) {
            double[] values = series.valuesIn(month);
            monthlyAverage[month.ordinal()] = Stats.mean(values);
            monthlySpread[month.ordinal()] = Stats.sampleStdDev(values);
        }

        double profileMean = Stats.mean(monthlyAverage);
        double[] seasonalIndex = new double[12];
        for (int m = 0; m < 12; m++) {
            seasonalIndex[m] = profileMean == 0 ? 1 : monthlyAverage[m] / profileMean;
        }

        List<Double> years = new ArrayList<>();
        List<Double> totals = new ArrayList<>();
        for (int year = series.start().getYear(); year <= series.end().getYear(); year++) {
            boolean wholeYearInside = !YearMonth.of(year, 1).isBefore(series.start())
                    && !YearMonth.of(year, 12).isAfter(series.end());
            if (wholeYearInside) {
                double total = 0;
                for (int month = 1; month <= 12; month++) {
                    total += series.valueAt(YearMonth.of(year, month));
                }
                years.add((double) year);
                totals.add(total);
            }
        }
        LinearFit yearlyTrend = years.size() >= 2
                ? Stats.linearFit(toArray(years), toArray(totals))
                : null;

        double averagePerMonth = Stats.mean(series.counts());
        return new SeasonalTrendModel(monthlyAverage, monthlySpread, seasonalIndex, yearlyTrend, years.size(),
                averagePerMonth, seasonalStrength(series, monthlyAverage, averagePerMonth));
    }

    double predict(YearMonth month) {
        double yearlyTotal = yearlyTrend == null
                ? averagePerMonth * 12
                : Math.max(0, yearlyTrend.at(month.getYear()));
        return yearlyTotal / 12 * seasonalIndex[month.getMonthValue() - 1];
    }

    double spread(Month month) {
        return monthlySpread[month.ordinal()];
    }

    double monthlyAverage(Month month) {
        return monthlyAverage[month.ordinal()];
    }

    boolean hasTrend() {
        return yearlyTrend != null;
    }

    double trendPerYear() {
        return yearlyTrend == null ? 0 : yearlyTrend.slope();
    }

    double trendRSquared() {
        return yearlyTrend == null ? 0 : yearlyTrend.rSquared();
    }

    int fullYears() {
        return fullYears;
    }

    double averagePerMonth() {
        return averagePerMonth;
    }

    double seasonalStrength() {
        return seasonalStrength;
    }

    private static double seasonalStrength(MonthlySeries series, double[] monthlyAverage, double overallMean) {
        double explainedByMonth = 0;
        double totalVariation = 0;
        for (int i = 0; i < series.size(); i++) {
            double monthMean = monthlyAverage[series.monthAt(i).getMonthValue() - 1];
            explainedByMonth += (monthMean - overallMean) * (monthMean - overallMean);
            totalVariation += (series.counts()[i] - overallMean) * (series.counts()[i] - overallMean);
        }
        return totalVariation == 0 ? 0 : explainedByMonth / totalVariation;
    }

    private static double[] toArray(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
