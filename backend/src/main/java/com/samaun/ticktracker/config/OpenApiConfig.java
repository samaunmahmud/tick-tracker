package com.samaun.ticktracker.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tickTrackerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Tick Tracker API")
                .version("1.0")
                .description("Syncs UK tick sightings from the Elanco API, accepts new reports, "
                        + "and serves search and aggregate insights."));
    }
}
