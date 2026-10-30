package com.samaun.ticktracker.reference;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    @GetMapping("/cities")
    public List<CityResponse> cities() {
        return Arrays.stream(UkCity.values())
                .map(CityResponse::from)
                .toList();
    }

    @GetMapping("/species")
    public List<SpeciesResponse> species() {
        return Arrays.stream(TickSpecies.values())
                .map(SpeciesResponse::from)
                .toList();
    }
}
