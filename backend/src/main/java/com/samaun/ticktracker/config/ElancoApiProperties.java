package com.samaun.ticktracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.elanco")
public record ElancoApiProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        boolean syncOnStartup,
        String syncCron
) {
}
