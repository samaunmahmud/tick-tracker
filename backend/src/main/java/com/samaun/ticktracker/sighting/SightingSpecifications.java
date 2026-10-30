package com.samaun.ticktracker.sighting;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class SightingSpecifications {

    private SightingSpecifications() {
    }

    public static Specification<Sighting> sightedOnOrAfter(LocalDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("sightedAt"), from);
    }

    public static Specification<Sighting> sightedOnOrBefore(LocalDateTime to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("sightedAt"), to);
    }

    public static Specification<Sighting> inCity(String city) {
        return (root, query, cb) -> cb.equal(root.get("city"), city);
    }

    public static Specification<Sighting> inRegion(String region) {
        return (root, query, cb) -> cb.equal(root.get("region"), region);
    }

    public static Specification<Sighting> ofSpecies(String species) {
        return (root, query, cb) -> cb.equal(root.get("species"), species);
    }
}
