package com.samaun.ticktracker.reference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UkCityTest {

    @Test
    void findsCityByExactName() {
        assertThat(UkCity.fromName("Manchester")).contains(UkCity.MANCHESTER);
    }

    @Test
    void ignoresCaseAndSurroundingWhitespace() {
        assertThat(UkCity.fromName("  mAnChEsTeR ")).contains(UkCity.MANCHESTER);
    }

    @Test
    void acceptsNewcastleUponTyneAsAlias() {
        assertThat(UkCity.fromName("Newcastle upon Tyne")).contains(UkCity.NEWCASTLE);
        assertThat(UkCity.fromName("newcastle   UPON tyne")).contains(UkCity.NEWCASTLE);
    }

    @Test
    void returnsEmptyForUnknownCity() {
        assertThat(UkCity.fromName("Atlantis")).isEmpty();
    }

    @Test
    void returnsEmptyForBlankOrNullName() {
        assertThat(UkCity.fromName("   ")).isEmpty();
        assertThat(UkCity.fromName(null)).isEmpty();
    }

    @Test
    void knowsRegionAndCoordinates() {
        UkCity cardiff = UkCity.CARDIFF;

        assertThat(cardiff.region()).isEqualTo("Wales");
        assertThat(cardiff.latitude()).isBetween(51.0, 52.0);
        assertThat(cardiff.longitude()).isBetween(-4.0, -3.0);
    }

    @Test
    void coversAllFourteenCitiesInTheFeed() {
        assertThat(UkCity.values()).hasSize(14);
    }
}
