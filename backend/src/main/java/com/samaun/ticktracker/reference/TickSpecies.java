package com.samaun.ticktracker.reference;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public enum TickSpecies {

    FOX_BADGER_TICK(
            "Fox/badger tick",
            "Ixodes canisuga",
            "Lives in dens and burrows. Feeds on foxes, badgers and sometimes dogs."),
    MARSH_TICK(
            "Marsh tick",
            "Ixodes apronophorus",
            "Found in wet, marshy ground. Feeds mainly on voles, shrews and other small mammals."),
    PASSERINE_TICK(
            "Passerine tick",
            "Dermacentor frontalis",
            "Feeds on songbirds such as thrushes and finches, in woodland, hedgerows and gardens."),
    SOUTHERN_RODENT_TICK(
            "Southern rodent tick",
            "Ixodes acuminatus",
            "Mostly seen in southern areas. Feeds on small rodents and shrews and lives in their burrows."),
    TREE_HOLE_TICK(
            "Tree-hole tick",
            "Ixodes arboricola",
            "Lives in tree holes and nest boxes. Feeds on the birds that nest there, such as tits.");

    private final String commonName;
    private final String latinName;
    private final String description;

    TickSpecies(String commonName, String latinName, String description) {
        this.commonName = commonName;
        this.latinName = latinName;
        this.description = description;
    }

    public static Optional<TickSpecies> fromCommonName(String name) {
        return findBy(TickSpecies::commonName, name);
    }

    public static Optional<TickSpecies> fromLatinName(String name) {
        return findBy(TickSpecies::latinName, name);
    }

    private static Optional<TickSpecies> findBy(Function<TickSpecies, String> field, String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = normalize(name);
        return Arrays.stream(values())
                .filter(species -> normalize(field.apply(species)).equals(wanted))
                .findFirst();
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String commonName() {
        return commonName;
    }

    public String latinName() {
        return latinName;
    }

    public String description() {
        return description;
    }
}
