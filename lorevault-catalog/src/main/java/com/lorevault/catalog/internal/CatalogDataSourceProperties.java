package com.lorevault.catalog.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lorevault.catalog.datasource")
public record CatalogDataSourceProperties(
    String url,
    String username,
    String password
) {}
