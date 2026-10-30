package com.samaun.ticktracker.reference;

public record SpeciesResponse(String commonName, String latinName, String description) {

    public static SpeciesResponse from(TickSpecies species) {
        return new SpeciesResponse(species.commonName(), species.latinName(), species.description());
    }
}
