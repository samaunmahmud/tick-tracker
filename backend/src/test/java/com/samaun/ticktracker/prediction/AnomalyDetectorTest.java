package com.samaun.ticktracker.prediction;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static com.samaun.ticktracker.prediction.SeriesFixtures.every;
import static com.samaun.ticktracker.prediction.SeriesFixtures.years;
import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectorTest {

    private static MonthlySeries steadyYears(double[] levels) {
        double[][] yearsOfData = new double[levels.length][];
        for (int i = 0; i < levels.length; i++) {
            yearsOfData[i] = every(levels[i]);
        }
        return years(2019, yearsOfData);
    }

    private static int poisson(Random random, double lambda) {
        double limit = Math.exp(-lambda);
        double product = random.nextDouble();
        int count = 0;
        while (product > limit) {
            product *= random.nextDouble();
            count++;
        }
        return count;
    }

    @Test
    void flagsAnInjectedBigSpike() {
        MonthlySeries leeds = steadyYears(new double[]{4, 5, 6, 5, 4});
        leeds.counts()[3 * 12 + 2] = 20;

        AnomalyDetector.Detection detection = AnomalyDetector.detect(Map.of("Leeds", leeds), 0.05);

        assertThat(detection.tested()).isEqualTo(60);
        assertThat(detection.flagged()).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.area()).isEqualTo("Leeds");
            assertThat(anomaly.month()).isEqualTo(YearMonth.of(2022, 3));
            assertThat(anomaly.observed()).isEqualTo(20);
            assertThat(anomaly.expected()).isEqualTo(4.75);
            assertThat(anomaly.direction()).isEqualTo(AnomalyType.SPIKE);
            assertThat(anomaly.pValue()).isEqualTo(Stats.poissonAtLeast(20, 4.75));
            assertThat(anomaly.adjustedPValue()).isGreaterThanOrEqualTo(anomaly.pValue()).isLessThan(0.0001);
        });
    }

    @Test
    void flagsAnInjectedBigDip() {
        MonthlySeries bristol = steadyYears(new double[]{10, 11, 12, 11, 10});
        bristol.counts()[2 * 12 + 7] = 0;

        AnomalyDetector.Detection detection = AnomalyDetector.detect(Map.of("Bristol", bristol), 0.05);

        assertThat(detection.flagged()).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.month()).isEqualTo(YearMonth.of(2021, 8));
            assertThat(anomaly.direction()).isEqualTo(AnomalyType.DIP);
            assertThat(anomaly.expected()).isEqualTo(10.5);
            assertThat(anomaly.pValue()).isEqualTo(Stats.poissonAtMost(0, 10.5));
        });
    }

    @Test
    void randomNoiseIsNotFlaggedOnceCorrected() {
        Random random = new Random(20261030L);
        Map<String, MonthlySeries> areas = new LinkedHashMap<>();
        for (int area = 0; area < 20; area++) {
            double[] counts = new double[10 * 12];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = poisson(random, 3);
            }
            areas.put("area" + area, new MonthlySeries(YearMonth.of(2015, 1), counts));
        }

        AnomalyDetector.Detection detection = AnomalyDetector.detect(areas, 0.05);

        assertThat(detection.tested()).isEqualTo(2400);
        assertThat(detection.flaggedWithoutCorrection()).isGreaterThan(30);
        assertThat(detection.flagged()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void skipsMonthsWithNoSightingsInAnyOtherYear() {
        MonthlySeries quiet = steadyYears(new double[]{0, 0, 0, 0});
        quiet.counts()[12] = 1;

        AnomalyDetector.Detection detection = AnomalyDetector.detect(Map.of("Quiet", quiet), 0.05);

        assertThat(detection.skipped()).isEqualTo(45);
        assertThat(detection.tested()).isEqualTo(3);
        assertThat(detection.flagged()).isEmpty();
    }

    @Test
    void correctsAcrossAllAreasTogether() {
        MonthlySeries leeds = steadyYears(new double[]{4, 5, 6, 5, 4});
        leeds.counts()[3 * 12 + 2] = 20;
        Map<String, MonthlySeries> areas = new LinkedHashMap<>();
        areas.put("Leeds", leeds);
        areas.put("Bristol", steadyYears(new double[]{4, 5, 6, 5, 4}));

        AnomalyDetector.Detection detection = AnomalyDetector.detect(areas, 0.05);

        assertThat(detection.tested()).isEqualTo(120);
        assertThat(detection.flagged()).singleElement()
                .satisfies(anomaly -> assertThat(anomaly.area()).isEqualTo("Leeds"));
    }
}
