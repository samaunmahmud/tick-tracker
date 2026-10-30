package com.samaun.ticktracker.reference;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Tag(name = "Reference", description = "The cities and species the API recognises")
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    @Operation(summary = "Known cities", description = "Each city with its region and coordinates.")
    @GetMapping("/cities")
    public List<CityResponse> cities() {
        return Arrays.stream(UkCity.values())
                .map(CityResponse::from)
                .toList();
    }

    @Operation(summary = "Known species", description = "Common name, latin name and a short species guide.")
    @GetMapping("/species")
    public List<SpeciesResponse> species() {
        return Arrays.stream(TickSpecies.values())
                .map(SpeciesResponse::from)
                .toList();
    }
}
