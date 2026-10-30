package com.samaun.ticktracker.prediction;

import java.time.YearMonth;
import java.util.Arrays;

final class SeriesFixtures {

    private SeriesFixtures() {
    }

    static MonthlySeries years(int firstYear, double[]... years) {
        double[] counts = Arrays.stream(years).flatMapToDouble(Arrays::stream).toArray();
        return new MonthlySeries(YearMonth.of(firstYear, 1), counts);
    }

    static double[] every(double value) {
        double[] year = new double[12];
        Arrays.fill(year, value);
        return year;
    }
}
