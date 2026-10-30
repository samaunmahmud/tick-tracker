package com.samaun.ticktracker.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalSighting(
        String id,
        String date,
        String location,
        String species,
        String latinName
) {
}
