package com.samaun.ticktracker.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaun.ticktracker.config.ElancoApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ElancoClient {

    private static final Logger log = LoggerFactory.getLogger(ElancoClient.class);

    private final RestClient restClient;
    private final ElancoApiProperties properties;
    private final Sleeper sleeper;
    private final Clock clock;

    public ElancoClient(RestClient elancoRestClient, ElancoApiProperties properties, Sleeper sleeper, Clock clock) {
        this.restClient = elancoRestClient;
        this.properties = properties;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    public List<JsonNode> fetchAllSightings() {
        int maxAttempts = properties.maxAttempts();
        Exception lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Duration wait;
            try {
                return fetchOnce();
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (!isRetryable(status)) {
                    throw new ExternalApiException("Elanco API returned HTTP " + status, e);
                }
                lastFailure = e;
                wait = retryAfter(e.getResponseHeaders()).orElse(backoff(attempt));
            } catch (ResourceAccessException e) {
                lastFailure = e;
                wait = backoff(attempt);
            }

            if (attempt < maxAttempts) {
                log.warn("Elanco API attempt {}/{} failed ({}), retrying in {}s",
                        attempt, maxAttempts, lastFailure.getMessage(), wait.toSeconds());
                pause(wait);
            }
        }

        throw new ExternalApiException(
                "Elanco API still failing after " + maxAttempts + " attempts: " + lastFailure.getMessage(),
                lastFailure);
    }

    private List<JsonNode> fetchOnce() {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/tick-sightings")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ExternalApiException("Elanco API returned a response we could not read", e);
        }

        if (body == null) {
            throw new ExternalApiException("Elanco API returned an empty response");
        }
        if (!body.isArray()) {
            throw new ExternalApiException("Elanco API returned " + body.getNodeType() + " instead of a list");
        }

        List<JsonNode> records = new ArrayList<>(body.size());
        body.forEach(records::add);
        return records;
    }

    private boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private Duration backoff(int attempt) {
        Duration wait = properties.initialBackoff().multipliedBy(1L << (attempt - 1));
        return capped(wait);
    }

    private Optional<Duration> retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? Optional.empty() : Optional.of(capped(Duration.ofSeconds(seconds)));
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration untilRetry = Duration.between(clock.instant(), retryAt.toInstant());
                return Optional.of(capped(untilRetry.isNegative() ? Duration.ZERO : untilRetry));
            } catch (DateTimeParseException notADate) {
                return Optional.empty();
            }
        }
    }

    private Duration capped(Duration wait) {
        return wait.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : wait;
    }

    private void pause(Duration wait) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("Interrupted while waiting to retry the Elanco API", e);
        }
    }
}
