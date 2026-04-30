package com.lorevault.api.config;

import com.lorevault.api.ingestion.resolution.event.BookEventAnnProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to enable LoreVault configuration properties.
 * This centralizes the activation of all @ConfigurationProperties classes
 * for the lorevault.* namespace.
 */
@Configuration
@EnableConfigurationProperties({
    LoreVaultLlmProperties.class,
    LoreVaultLlmLoggingProperties.class,
    LoreVaultEmbeddingProperties.class,
    LoreVaultContentProperties.class,
    LoreVaultSlotsProperties.class,
    BookEventAnnProperties.class
})
public class LoreVaultPropertiesConfiguration {
    // This class serves as the central point for enabling configuration properties
    // The actual properties are injected where needed via constructor injection
}
