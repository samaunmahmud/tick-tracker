package com.samaun.ticktracker.sighting;

import com.samaun.ticktracker.ingestion.NormalizedSighting;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sightings")
public class Sighting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String externalId;
    private String contentHash;
    private LocalDateTime sightedAt;
    private String city;
    private String region;
    private double latitude;
    private double longitude;
    private String species;
    private String latinName;
    private String notes;
    private String imageFilename;

    @Enumerated(EnumType.STRING)
    private SightingSource source;

    private Instant createdAt;

    protected Sighting() {
    }

    public static Sighting fromElanco(NormalizedSighting normalized, Instant createdAt) {
        Sighting sighting = new Sighting();
        sighting.externalId = normalized.externalId();
        sighting.contentHash = normalized.contentHash();
        sighting.sightedAt = normalized.sightedAt();
        sighting.city = normalized.city().displayName();
        sighting.region = normalized.city().region();
        sighting.latitude = normalized.city().latitude();
        sighting.longitude = normalized.city().longitude();
        sighting.species = normalized.species().commonName();
        sighting.latinName = normalized.species().latinName();
        sighting.source = SightingSource.ELANCO_API;
        sighting.createdAt = createdAt;
        return sighting;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public LocalDateTime getSightedAt() {
        return sightedAt;
    }

    public String getCity() {
        return city;
    }

    public String getRegion() {
        return region;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getSpecies() {
        return species;
    }

    public String getLatinName() {
        return latinName;
    }

    public String getNotes() {
        return notes;
    }

    public String getImageFilename() {
        return imageFilename;
    }

    public SightingSource getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
