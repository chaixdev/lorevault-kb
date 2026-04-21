package com.lorevault.api.config;

import com.lorevault.api.config.LoreVaultContentProperties;
import com.lorevault.api.config.LoreVaultEmbeddingProperties;
import com.lorevault.api.config.LoreVaultLlmProperties;
import com.lorevault.api.config.LoreVaultLlmLoggingProperties;
import com.lorevault.api.config.LoreVaultSlotsProperties;
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
    LoreVaultSlotsProperties.class
})
public class LoreVaultPropertiesConfiguration {
    // This class serves as the central point for enabling configuration properties
    // The actual properties are injected where needed via constructor injection
}
