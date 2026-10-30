package com.samaun.ticktracker.prediction;

import java.util.Arrays;
import java.util.Comparator;

final class Stats {

    private static final double TAIL_PRECISION = 1e-17;

    private Stats() {
    }

    static double mean(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    static double sampleStdDev(double[] values) {
        if (values.length < 2) {
            return 0;
        }
        double mean = mean(values);
        double squaredDistances = 0;
        for (double value : values) {
            squaredDistances += (value - mean) * (value - mean);
        }
        return Math.sqrt(squaredDistances / (values.length - 1));
    }

    static LinearFit linearFit(double[] x, double[] y) {
        double meanX = mean(x);
        double meanY = mean(y);
        double sumXY = 0;
        double sumXX = 0;
        double sumYY = 0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            sumXY += dx * dy;
            sumXX += dx * dx;
            sumYY += dy * dy;
        }
        double slope = sumXX == 0 ? 0 : sumXY / sumXX;
        double intercept = meanY - slope * meanX;
        double rSquared = sumXX == 0 || sumYY == 0 ? 0 : (sumXY * sumXY) / (sumXX * sumYY);
        return new LinearFit(slope, intercept, rSquared);
    }

    static double meanAbsoluteError(double[] actual, double[] predicted) {
        double totalMiss = 0;
        for (int i = 0; i < actual.length; i++) {
            totalMiss += Math.abs(actual[i] - predicted[i]);
        }
        return actual.length == 0 ? 0 : totalMiss / actual.length;
    }

    static double poissonAtMost(long k, double lambda) {
        if (k < 0) {
            return 0;
        }
        double term = Math.exp(-lambda);
        double sum = term;
        for (long i = 1; i <= k; i++) {
            term *= lambda / i;
            sum += term;
        }
        return Math.min(1, sum);
    }

    static double poissonAtLeast(long k, double lambda) {
        if (k <= 0) {
            return 1;
        }
        double term = Math.exp(logPoissonProbability(k, lambda));
        double sum = 0;
        long i = k;
        while (term > 0 && (i <= lambda || term > sum * TAIL_PRECISION)) {
            sum += term;
            i++;
            term *= lambda / i;
        }
        return Math.min(1, sum);
    }

    static double[] benjaminiHochberg(double[] pValues) {
        int tests = pValues.length;
        Integer[] byPValue = new Integer[tests];
        for (int i = 0; i < tests; i++) {
            byPValue[i] = i;
        }
        Arrays.sort(byPValue, Comparator.comparingDouble(i -> pValues[i]));

        double[] adjusted = new double[tests];
        double smallestSoFar = 1;
        for (int rank = tests; rank >= 1; rank--) {
            int index = byPValue[rank - 1];
            smallestSoFar = Math.min(smallestSoFar, pValues[index] * tests / rank);
            adjusted[index] = smallestSoFar;
        }
        return adjusted;
    }

    private static double logPoissonProbability(long k, double lambda) {
        double logFactorial = 0;
        for (long i = 2; i <= k; i++) {
            logFactorial += Math.log(i);
        }
        return -lambda + k * Math.log(lambda) - logFactorial;
    }

    record LinearFit(double slope, double intercept, double rSquared) {

        double at(double x) {
            return intercept + slope * x;
        }
    }
}
