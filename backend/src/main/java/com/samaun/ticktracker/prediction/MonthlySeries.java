package com.samaun.ticktracker.prediction;

import com.samaun.ticktracker.insights.TrendPoint;

import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

record MonthlySeries(YearMonth start, double[] counts) {

    static MonthlySeries covering(YearMonth start, YearMonth end, List<TrendPoint> points) {
        int size = (int) start.until(end, ChronoUnit.MONTHS) + 1;
        double[] counts = new double[size];
        for (TrendPoint point : points) {
            int index = (int) start.until(YearMonth.from(point.periodStart()), ChronoUnit.MONTHS);
            if (index >= 0 && index < size) {
                counts[index] = point.count();
            }
        }
        return new MonthlySeries(start, counts);
    }

    int size() {
        return counts.length;
    }

    YearMonth end() {
        return start.plusMonths(counts.length - 1L);
    }

    YearMonth monthAt(int index) {
        return start.plusMonths(index);
    }

    double valueAt(YearMonth month) {
        int index = (int) start.until(month, ChronoUnit.MONTHS);
        if (index < 0 || index >= counts.length) {
            throw new IllegalArgumentException(month + " is outside " + start + " to " + end());
        }
        return counts[index];
    }

    double[] valuesIn(Month calendarMonth) {
        return IntStream.range(0, counts.length)
                .filter(i -> monthAt(i).getMonth() == calendarMonth)
                .mapToDouble(i -> counts[i])
                .toArray();
    }

    double total() {
        return Arrays.stream(counts).sum();
    }

    MonthlySeries first(int months) {
        return new MonthlySeries(start, Arrays.copyOf(counts, months));
    }

    MonthlySeries last(int months) {
        return new MonthlySeries(start.plusMonths(counts.length - (long) months),
                Arrays.copyOfRange(counts, counts.length - months, counts.length));
    }
}
