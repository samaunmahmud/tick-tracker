package com.samaun.ticktracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    public static final ZoneId UK = ZoneId.of("Europe/London");

    @Bean
    public Clock clock() {
        return Clock.system(UK);
    }
}
