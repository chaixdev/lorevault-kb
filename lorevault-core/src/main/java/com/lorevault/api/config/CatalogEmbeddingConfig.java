package com.lorevault.api.config;

import com.lorevault.catalog.EmbeddingFunction;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link EmbeddingFunction} bean for the catalog module.
 * <p>
 * The catalog defines the contract ({@code EmbeddingFunction} interface in
 * {@code com.lorevault.catalog}); core provides the implementation by wrapping
 * Spring AI's {@link EmbeddingModel}. The catalog has zero knowledge of Spring AI types.
 * <p>
 * This bean is conditional on {@code lorevault.catalog.enabled=true} so it
 * only activates when the catalog module is in use.
 */
@Configuration
@ConditionalOnProperty(name = "lorevault.catalog.enabled", havingValue = "true")
public class CatalogEmbeddingConfig {

    @Bean
    @Qualifier("catalogEmbeddingFunction")
    public EmbeddingFunction embeddingFunction(
            @Qualifier("embeddingModel") EmbeddingModel embeddingModel) {
        return embeddingModel::embed;
    }
}
