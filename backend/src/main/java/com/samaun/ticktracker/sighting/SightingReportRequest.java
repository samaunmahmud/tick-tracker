package com.samaun.ticktracker.sighting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SightingReportRequest(
        @Schema(description = "When the tick was seen, UK local time. Must not be in the future.",
                example = "2024-06-01T10:30:00")
        @NotNull LocalDateTime sightedAt,
        @Schema(description = "A known UK city, case-insensitive.", example = "London")
        @NotBlank String city,
        @Schema(description = "Common or latin species name.", example = "Marsh tick")
        @NotBlank String species,
        @Schema(description = "Optional free text, up to 500 characters.", example = "Found on a dog after a walk")
        @Size(max = 500) String notes
) {
}
