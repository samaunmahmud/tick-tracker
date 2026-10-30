package com.samaun.ticktracker.prediction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StatsTest {

    @Test
    void meanAndSampleStandardDeviation() {
        double[] values = {2, 4, 4, 4, 5, 5, 7, 9};

        assertThat(Stats.mean(values)).isEqualTo(5);
        assertThat(Stats.sampleStdDev(values)).isCloseTo(2.138, within(0.001));
    }

    @Test
    void standardDeviationOfFewerThanTwoValuesIsZero() {
        assertThat(Stats.sampleStdDev(new double[]{3})).isZero();
        assertThat(Stats.mean(new double[0])).isZero();
    }

    @Test
    void linearFitRecoversAnExactLine() {
        Stats.LinearFit fit = Stats.linearFit(new double[]{2020, 2021, 2022, 2023}, new double[]{100, 112, 124, 136});

        assertThat(fit.slope()).isCloseTo(12, within(1e-9));
        assertThat(fit.at(2024)).isCloseTo(148, within(1e-9));
        assertThat(fit.rSquared()).isCloseTo(1, within(1e-9));
    }

    @Test
    void linearFitOfFlatDataHasZeroSlopeAndZeroRSquared() {
        Stats.LinearFit fit = Stats.linearFit(new double[]{1, 2, 3}, new double[]{5, 5, 5});

        assertThat(fit.slope()).isZero();
        assertThat(fit.rSquared()).isZero();
    }

    @Test
    void meanAbsoluteErrorIsTheAverageSizeOfTheMiss() {
        assertThat(Stats.meanAbsoluteError(new double[]{1, 2, 3}, new double[]{2, 2, 5})).isEqualTo(1);
        assertThat(Stats.meanAbsoluteError(new double[]{10, 10}, new double[]{7, 13})).isEqualTo(3);
    }

    @Test
    void poissonProbabilitiesMatchHandCalculations() {
        assertThat(Stats.poissonAtMost(0, 2)).isCloseTo(Math.exp(-2), within(1e-12));
        assertThat(Stats.poissonAtLeast(3, 1)).isCloseTo(1 - 2.5 / Math.E, within(1e-12));
        assertThat(Stats.poissonAtLeast(3, 0.17)).isCloseTo(1 - Math.exp(-0.17) * (1 + 0.17 + 0.17 * 0.17 / 2), within(1e-12));
        assertThat(Stats.poissonAtMost(5, 10)).isCloseTo(0.067086, within(0.000001));
    }

    @Test
    void poissonTailsAreConsistentAndHandleEdges() {
        for (long k = 1; k <= 30; k++) {
            assertThat(Stats.poissonAtLeast(k, 6) + Stats.poissonAtMost(k - 1, 6)).isCloseTo(1, within(1e-12));
        }
        assertThat(Stats.poissonAtLeast(0, 3)).isEqualTo(1);
        assertThat(Stats.poissonAtMost(-1, 3)).isZero();
        assertThat(Stats.poissonAtLeast(60, 5)).isPositive().isLessThan(1e-25);
    }

    @Test
    void benjaminiHochbergMatchesTheTextbookExample() {
        double[] adjusted = Stats.benjaminiHochberg(new double[]{0.01, 0.04, 0.03, 0.005});

        assertThat(adjusted).containsExactly(new double[]{0.02, 0.04, 0.04, 0.02}, within(1e-12));
    }

    @Test
    void benjaminiHochbergNeverGoesAboveOne() {
        assertThat(Stats.benjaminiHochberg(new double[]{0.9, 0.95, 1})).containsOnly(1.0);
    }
}
