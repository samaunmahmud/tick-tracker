package com.samaun.ticktracker.config;

import com.samaun.ticktracker.ingestion.Sleeper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ElancoClientConfig {

    @Bean
    public RestClient elancoRestClient(RestClient.Builder builder, ElancoApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public Sleeper sleeper() {
        return Thread::sleep;
    }
}
