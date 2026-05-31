package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the web tier's multipart file-upload handling.
 */
@ConfigurationProperties(prefix = "lorevault.web.multipart")
@Validated
public record LoreVaultWebMultipartProperties(
    Integer maxPartCount
) {
    public LoreVaultWebMultipartProperties {
        if (maxPartCount == null) maxPartCount = 200;
    }
}
