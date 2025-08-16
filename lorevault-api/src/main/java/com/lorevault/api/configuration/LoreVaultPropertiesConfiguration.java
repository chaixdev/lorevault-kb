package com.lorevault.api.configuration;

import com.lorevault.api.configuration.properties.LoreVaultContentProperties;
import com.lorevault.api.configuration.properties.LoreVaultEmbeddingProperties;
import com.lorevault.api.configuration.properties.LoreVaultLlmProperties;
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
    LoreVaultEmbeddingProperties.class,
    LoreVaultContentProperties.class
})
public class LoreVaultPropertiesConfiguration {
    // This class serves as the central point for enabling configuration properties
    // The actual properties are injected where needed via constructor injection
}
