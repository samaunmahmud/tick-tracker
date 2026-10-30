package com.samaun.ticktracker.sighting;

import com.samaun.ticktracker.ingestion.ElancoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SightingReportIntegrationTest {

    private static final String REPORT = """
            {"sightedAt": "2024-06-01T10:30:00", "city": "leeds", "species": "Marsh tick", "notes": "On a dog"}
            """;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    private ElancoClient elancoClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SightingRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void jsonReportIsStoredAndCanBeFetchedFromItsLocation() throws Exception {
        String location = mockMvc.perform(post("/api/sightings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.city").value("Leeds"))
                .andExpect(jsonPath("$.region").value("Yorkshire and the Humber"))
                .andExpect(jsonPath("$.source").value("USER_REPORT"))
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).isNotNull();
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("On a dog"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void sameReportTwiceIsAConflict() throws Exception {
        mockMvc.perform(post("/api/sightings").contentType(MediaType.APPLICATION_JSON).content(REPORT))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/sightings").contentType(MediaType.APPLICATION_JSON).content(REPORT))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }
}
