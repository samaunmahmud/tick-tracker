package com.samaun.ticktracker.sighting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samaun.ticktracker.config.ClockConfig;
import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SightingController.class)
@Import({SightingService.class, SightingControllerTest.FixedClock.class})
class SightingControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ClockConfig.UK);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SightingRepository repository;

    private static Sighting leedsSighting() {
        Sighting sighting = Sighting.fromUserReport(LocalDateTime.of(2024, 5, 1, 10, 0),
                UkCity.LEEDS, TickSpecies.MARSH_TICK, "In long grass", NOW);
        ReflectionTestUtils.setField(sighting, "id", ID);
        return sighting;
    }

    @SuppressWarnings("unchecked")
    private Pageable searchAndCapturePageable(MockHttpServletRequestBuilder request) throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findAll(any(Specification.class), pageable.capture()))
                .thenAnswer(call -> new PageImpl<>(List.of(leedsSighting()), call.getArgument(1), 1));
        mockMvc.perform(request).andExpect(status().isOk());
        return pageable.getValue();
    }

    private MockHttpServletRequestBuilder report(String sightedAt, String city, String species) throws Exception {
        return report(sightedAt, city, species, null);
    }

    private MockHttpServletRequestBuilder report(String sightedAt, String city, String species, String notes)
            throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("sightedAt", sightedAt);
        body.put("city", city);
        body.put("species", species);
        body.put("notes", notes);
        return post("/api/sightings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchReturnsPageOfSightings() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(leedsSighting()), call.getArgument(1), 1));

        mockMvc.perform(get("/api/sightings").param("city", "leeds").param("species", "Ixodes apronophorus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].city").value("Leeds"))
                .andExpect(jsonPath("$.content[0].region").value("Yorkshire and the Humber"))
                .andExpect(jsonPath("$.content[0].sightedAt").value("2024-05-01T10:00:00"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void searchSortsNewestFirstByDefault() throws Exception {
        Pageable pageable = searchAndCapturePageable(get("/api/sightings"));

        assertThat(pageable.getSort().getOrderFor("sightedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void searchSortsOldestFirstWhenAsked() throws Exception {
        Pageable pageable = searchAndCapturePageable(get("/api/sightings").param("sort", "OLDEST"));

        assertThat(pageable.getSort().getOrderFor("sightedAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void searchPassesPageAndSize() throws Exception {
        Pageable pageable = searchAndCapturePageable(get("/api/sightings").param("page", "3").param("size", "50"));

        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(50);
    }

    @Test
    void searchRejectsUnknownFilterValues() throws Exception {
        mockMvc.perform(get("/api/sightings")
                        .param("city", "Atlantis")
                        .param("region", "Narnia")
                        .param("species", "Dragon tick")
                        .param("sort", "random"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(
                        org.hamcrest.Matchers.containsInAnyOrder("city", "region", "species", "sort")));
    }

    @Test
    void searchRejectsFromAfterTo() throws Exception {
        mockMvc.perform(get("/api/sightings").param("from", "2024-02-01").param("to", "2024-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
    }

    @Test
    void searchAcceptsSameDayForFromAndTo() throws Exception {
        searchAndCapturePageable(get("/api/sightings").param("from", "2024-01-01").param("to", "2024-01-01"));
    }

    @Test
    void searchRejectsUnreadableDate() throws Exception {
        mockMvc.perform(get("/api/sightings").param("to", "last tuesday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("to"));
    }

    @Test
    void searchRejectsPageSizeOverOneHundred() throws Exception {
        mockMvc.perform(get("/api/sightings").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
    }

    @Test
    void findByIdReturnsSighting() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(leedsSighting()));

        mockMvc.perform(get("/api/sightings/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.notes").value("In long grass"));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sightings/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void findByIdReturns400ForMalformedId() throws Exception {
        mockMvc.perform(get("/api/sightings/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("has an invalid value 'not-a-uuid'"));
    }

    @Test
    void errorTimestampComesFromTheInjectedClock() throws Exception {
        mockMvc.perform(get("/api/sightings").param("city", "Atlantis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value("2026-10-30T12:00:00Z"));
    }

    @Test
    void reportCreatesSightingAndFillsInServerSideFields() throws Exception {
        when(repository.save(any(Sighting.class))).thenAnswer(call -> {
            Sighting saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ID);
            return saved;
        });

        mockMvc.perform(report("2024-05-01T10:00:00", " manchester ", "marsh tick", "  On my dog  "))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/sightings/" + ID)))
                .andExpect(jsonPath("$.city").value("Manchester"))
                .andExpect(jsonPath("$.region").value("North West"))
                .andExpect(jsonPath("$.latinName").value("Ixodes apronophorus"))
                .andExpect(jsonPath("$.notes").value("On my dog"))
                .andExpect(jsonPath("$.source").value("USER_REPORT"));
    }

    @Test
    void reportRejectsFutureDate() throws Exception {
        mockMvc.perform(report("2027-01-01T10:00:00", "Leeds", "Marsh tick"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sightedAt"));
        verify(repository, never()).save(any());
    }

    @Test
    void reportRejectsUnknownCityAndSpeciesTogether() throws Exception {
        mockMvc.perform(report("2024-05-01T10:00:00", "Atlantis", "Dragon tick"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)));
    }

    @Test
    void reportRejectsMissingFields() throws Exception {
        mockMvc.perform(report(null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(
                        org.hamcrest.Matchers.containsInAnyOrder("sightedAt", "city", "species")));
    }

    @Test
    void reportRejectsUnreadableDate() throws Exception {
        mockMvc.perform(report("yesterday", "Leeds", "Marsh tick"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sightedAt"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("has an invalid value 'yesterday'"));
    }

    @Test
    void reportRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/sightings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sightedAt\": \"2024-05-01T10:00:00\", \"city\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void reportRejectsFormData() throws Exception {
        mockMvc.perform(multipart("/api/sightings")
                        .param("sightedAt", "2024-05-01T10:00:00")
                        .param("city", "Leeds")
                        .param("species", "Marsh tick"))
                .andExpect(status().isUnsupportedMediaType());
        verify(repository, never()).save(any());
    }

    @Test
    void reportRejectsNotesOver500Characters() throws Exception {
        mockMvc.perform(report("2024-05-01T10:00:00", "Leeds", "Marsh tick", "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("notes"));
    }

    @Test
    void reportReturnsConflictWhenSightingAlreadyExists() throws Exception {
        when(repository.existsByContentHash(any())).thenReturn(true);

        mockMvc.perform(report("2024-05-01T10:00:00", "Leeds", "Marsh tick"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        verify(repository, never()).save(any());
    }

    @Test
    void reportReturnsConflictWhenDatabaseRejectsDuplicate() throws Exception {
        when(repository.save(any(Sighting.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        mockMvc.perform(report("2024-05-01T10:00:00", "Leeds", "Marsh tick"))
                .andExpect(status().isConflict());
    }
}
