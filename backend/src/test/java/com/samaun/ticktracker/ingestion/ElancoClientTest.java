package com.samaun.ticktracker.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaun.ticktracker.config.ElancoApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ElancoClientTest {

    private static final String BASE_URL = "https://elanco.test/data";
    private static final String SIGHTINGS_URL = BASE_URL + "/tick-sightings";
    private static final String TWO_RECORDS = """
            [
              {"id":"a1","date":"2022-08-01T06:40:31","location":"Manchester","species":"Marsh tick","latinName":"Ixodes apronophorus"},
              {"id":"b2","date":"2023-05-12T14:00:00","location":"London","species":"Tree-hole tick","latinName":"Ixodes arboricola"}
            ]
            """;
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private MockRestServiceServer server;
    private RestClient restClient;
    private final List<Duration> sleeps = new ArrayList<>();
    private final Sleeper recordingSleeper = sleeps::add;
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private ElancoClient clientWithMaxAttempts(int maxAttempts) {
        ElancoApiProperties properties = new ElancoApiProperties(
                BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(20),
                maxAttempts, Duration.ofSeconds(5), Duration.ofSeconds(60),
                false, "0 0 */6 * * *");
        return new ElancoClient(restClient, properties, recordingSleeper, fixedClock);
    }

    @Test
    void returnsEveryRecordFromTheApi() {
        server.expect(requestTo(SIGHTINGS_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(TWO_RECORDS, MediaType.APPLICATION_JSON));

        List<JsonNode> records = clientWithMaxAttempts(5).fetchAllSightings();

        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("id").asText()).isEqualTo("a1");
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void retriesAfterRateLimitThenSucceeds() {
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess(TWO_RECORDS, MediaType.APPLICATION_JSON));

        List<JsonNode> records = clientWithMaxAttempts(5).fetchAllSightings();

        assertThat(records).hasSize(2);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
        server.verify();
    }

    @Test
    void doublesTheWaitAfterEachFailure() {
        server.expect(ExpectedCount.times(3), requestTo(SIGHTINGS_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess(TWO_RECORDS, MediaType.APPLICATION_JSON));

        clientWithMaxAttempts(5).fetchAllSightings();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20));
        server.verify();
    }

    @Test
    void neverWaitsLongerThanMaxBackoff() {
        server.expect(ExpectedCount.times(5), requestTo(SIGHTINGS_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess(TWO_RECORDS, MediaType.APPLICATION_JSON));

        clientWithMaxAttempts(6).fetchAllSightings();

        assertThat(sleeps).containsExactly(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20),
                Duration.ofSeconds(40), Duration.ofSeconds(60));
    }

    @Test
    void honoursRetryAfterInSeconds() {
        server.expect(requestTo(SIGHTINGS_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "7"));
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess(TWO_RECORDS, MediaType.APPLICATION_JSON));

        clientWithMaxAttempts(5).fetchAllSightings();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    void honoursRetryAfterAsHttpDate() {
        server.expect(requestTo(SIGHTINGS_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "Fri, 30 Oct 2026 12:00:12 GMT"));
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess(TWO_RECORDS, MediaType.APPLICATION_JSON));

        clientWithMaxAttempts(5).fetchAllSightings();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(12));
    }

    @Test
    void capsAVeryLongRetryAfterAtMaxBackoff() {
        server.expect(requestTo(SIGHTINGS_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "3600"));
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess(TWO_RECORDS, MediaType.APPLICATION_JSON));

        clientWithMaxAttempts(5).fetchAllSightings();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void fallsBackToBackoffWhenRetryAfterIsUnreadable() {
        server.expect(requestTo(SIGHTINGS_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "soon"));
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess(TWO_RECORDS, MediaType.APPLICATION_JSON));

        clientWithMaxAttempts(5).fetchAllSightings();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
    }

    @Test
    void retriesOnNetworkError() {
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess(TWO_RECORDS, MediaType.APPLICATION_JSON));

        List<JsonNode> records = clientWithMaxAttempts(5).fetchAllSightings();

        assertThat(records).hasSize(2);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        server.expect(ExpectedCount.times(3), requestTo(SIGHTINGS_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> clientWithMaxAttempts(3).fetchAllSightings())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("after 3 attempts");

        assertThat(sleeps).hasSize(2);
        server.verify();
    }

    @Test
    void doesNotRetryClientErrors() {
        server.expect(ExpectedCount.once(), requestTo(SIGHTINGS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> clientWithMaxAttempts(5).fetchAllSightings())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("HTTP 404");

        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void failsWhenResponseIsNotAList() {
        server.expect(requestTo(SIGHTINGS_URL))
                .andRespond(withSuccess("{\"error\":\"oops\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clientWithMaxAttempts(5).fetchAllSightings())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("instead of a list");
    }

    @Test
    void failsWhenResponseIsEmpty() {
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clientWithMaxAttempts(5).fetchAllSightings())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void failsWhenResponseIsNotValidJson() {
        server.expect(requestTo(SIGHTINGS_URL)).andRespond(withSuccess("[{not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clientWithMaxAttempts(5).fetchAllSightings())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("could not read");
    }
}
