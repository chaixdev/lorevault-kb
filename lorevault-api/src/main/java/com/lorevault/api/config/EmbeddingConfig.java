package com.lorevault.api.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for embedding infrastructure beans.
 */
@Configuration
public class EmbeddingConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Configure timeouts via request factory to avoid deprecated builder methods
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return builder.requestFactory(() -> factory).build();
    }

    @Bean(name = "embeddingRestTemplate")
    public RestTemplate embeddingRestTemplate() {
        // Simple fallback RestTemplate (no custom timeouts) to satisfy constructor injection in tests
        return new RestTemplate();
    }
}
