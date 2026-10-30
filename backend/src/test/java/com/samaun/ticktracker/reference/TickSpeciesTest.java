package com.samaun.ticktracker.reference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TickSpeciesTest {

    @Test
    void findsSpeciesByCommonNameIgnoringCase() {
        assertThat(TickSpecies.fromCommonName("marsh TICK")).contains(TickSpecies.MARSH_TICK);
        assertThat(TickSpecies.fromCommonName(" Fox/badger tick ")).contains(TickSpecies.FOX_BADGER_TICK);
    }

    @Test
    void findsSpeciesByLatinNameIgnoringCase() {
        assertThat(TickSpecies.fromLatinName("ixodes arboricola")).contains(TickSpecies.TREE_HOLE_TICK);
    }

    @Test
    void doesNotMatchPartialNames() {
        assertThat(TickSpecies.fromCommonName("marsh")).isEmpty();
    }

    @Test
    void doesNotMatchLatinNameWhenSearchingByCommonName() {
        assertThat(TickSpecies.fromCommonName("Ixodes apronophorus")).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownOrNullName() {
        assertThat(TickSpecies.fromCommonName("Deer tick")).isEmpty();
        assertThat(TickSpecies.fromLatinName(null)).isEmpty();
    }

    @Test
    void pairsEachCommonNameWithItsLatinName() {
        assertThat(TickSpecies.PASSERINE_TICK.latinName()).isEqualTo("Dermacentor frontalis");
        assertThat(TickSpecies.SOUTHERN_RODENT_TICK.latinName()).isEqualTo("Ixodes acuminatus");
    }
}
