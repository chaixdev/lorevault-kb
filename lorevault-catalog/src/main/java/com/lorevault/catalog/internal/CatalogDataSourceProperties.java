package com.lorevault.catalog.internal;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "lorevault.catalog.datasource")
public record CatalogDataSourceProperties(
    @NotBlank String url,
    @NotBlank String username,
    @NotBlank String password
) {}
