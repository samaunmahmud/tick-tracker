package com.samaun.ticktracker.reference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReferenceController.class)
class ReferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsAllCitiesWithRegionAndCoordinates() throws Exception {
        mockMvc.perform(get("/api/reference/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(14)))
                .andExpect(jsonPath("$[0].name").value("Birmingham"))
                .andExpect(jsonPath("$[0].region").value("West Midlands"))
                .andExpect(jsonPath("$[0].latitude").isNumber())
                .andExpect(jsonPath("$[0].longitude").isNumber());
    }

    @Test
    void listsAllSpeciesWithLatinNameAndDescription() throws Exception {
        mockMvc.perform(get("/api/reference/species"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[1].commonName").value("Marsh tick"))
                .andExpect(jsonPath("$[1].latinName").value("Ixodes apronophorus"))
                .andExpect(jsonPath("$[1].description").isNotEmpty());
    }
}
