package com.lorevault.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for asynchronous processing
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // Uses Spring Boot's default async configuration
}
