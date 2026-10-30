package com.samaun.ticktracker.sighting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SightingReportRequest(
        @NotNull LocalDateTime sightedAt,
        @NotBlank String city,
        @NotBlank String species,
        @Size(max = 500) String notes
) {
}
