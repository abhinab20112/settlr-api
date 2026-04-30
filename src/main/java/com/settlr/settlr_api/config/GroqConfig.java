package com.settlr.settlr_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@SuppressWarnings("null")
public class GroqConfig {

    private final GroqProperties groqProperties;

    public GroqConfig(GroqProperties groqProperties) {
        this.groqProperties = groqProperties;
    }

    @Bean
    public WebClient groqWebClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(groqProperties.getTimeout()));

        return builder
                .baseUrl(groqProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + groqProperties.getApiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
