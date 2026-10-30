package com.samaun.ticktracker.prediction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class AnomalyDetector {

    private static final int MINIMUM_OTHER_YEARS = 2;

    private AnomalyDetector() {
    }

    static Detection detect(Map<String, MonthlySeries> seriesByArea, double alpha) {
        List<Anomaly> tests = new ArrayList<>();
        int skipped = 0;
        for (Map.Entry<String, MonthlySeries> area : seriesByArea.entrySet()) {
            MonthlySeries series = area.getValue();
            for (int i = 0; i < series.size(); i++) {
                double[] otherYears = sameMonthInOtherYears(series, i);
                if (otherYears.length < MINIMUM_OTHER_YEARS) {
                    continue;
                }
                double expected = Stats.mean(otherYears);
                if (expected == 0) {
                    skipped++;
                    continue;
                }
                long observed = (long) series.counts()[i];
                boolean spike = observed >= expected;
                double pValue = spike
                        ? Stats.poissonAtLeast(observed, expected)
                        : Stats.poissonAtMost(observed, expected);
                tests.add(new Anomaly(area.getKey(), series.monthAt(i), observed, expected, pValue, pValue,
                        spike ? AnomalyType.SPIKE : AnomalyType.DIP));
            }
        }

        double[] pValues = tests.stream().mapToDouble(Anomaly::pValue).toArray();
        double[] adjusted = Stats.benjaminiHochberg(pValues);
        List<Anomaly> flagged = new ArrayList<>();
        int flaggedWithoutCorrection = 0;
        for (int i = 0; i < tests.size(); i++) {
            Anomaly test = tests.get(i);
            if (test.pValue() < alpha) {
                flaggedWithoutCorrection++;
            }
            if (adjusted[i] < alpha) {
                flagged.add(new Anomaly(test.area(), test.month(), test.observed(), test.expected(),
                        test.pValue(), adjusted[i], test.direction()));
            }
        }
        flagged.sort(Comparator.comparingDouble(Anomaly::adjustedPValue)
                .thenComparingDouble(Anomaly::pValue)
                .thenComparing(Anomaly::month)
                .thenComparing(Anomaly::area));
        return new Detection(tests.size(), skipped, flaggedWithoutCorrection, flagged);
    }

    private static double[] sameMonthInOtherYears(MonthlySeries series, int index) {
        return IntStream.range(0, series.size())
                .filter(j -> j != index && series.monthAt(j).getMonth() == series.monthAt(index).getMonth())
                .mapToDouble(j -> series.counts()[j])
                .toArray();
    }

    record Detection(int tested, int skipped, int flaggedWithoutCorrection, List<Anomaly> flagged) {
    }
}
