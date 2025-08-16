package com.lorevault.api.infrastructure.ai;

import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.configuration.properties.LoreVaultMultiLlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Multi-provider embedding adapter that can route embedding requests
 * to different providers based on configuration.
 * This replaces the single-provider EmbeddingModelAdapter when multi-provider is enabled.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "lorevault.multi-llm", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MultiProviderEmbeddingAdapter implements EmbeddingPort {

    private final LoreVaultMultiLlmProperties multiLlmProperties;
    private final EmbeddingProviderFactory providerFactory;

    public MultiProviderEmbeddingAdapter(
            LoreVaultMultiLlmProperties multiLlmProperties,
            EmbeddingProviderFactory providerFactory) {
        this.multiLlmProperties = multiLlmProperties;
        this.providerFactory = providerFactory;
    }

    @Override
    public double[] embed(String text) {
        if (text == null) return new double[0];
        List<double[]> list = embedBatch(List.of(text));
        return list.isEmpty() ? new double[0] : list.get(0);
    }

    @Override
    public List<double[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        // Try primary embedding provider first
        LoreVaultMultiLlmProperties.ProviderProperties primaryProvider = 
            multiLlmProperties.getPrimaryEmbeddingProvider();
        
        if (primaryProvider != null && primaryProvider.enabled() && primaryProvider.supportsEmbeddings()) {
            try {
                EmbeddingProvider provider = providerFactory.createProvider(primaryProvider);
                List<double[]> result = provider.embedBatch(texts);
                if (!result.isEmpty()) {
                    log.debug("[MultiEmbedding] Successfully embedded {} texts using primary provider: {}", 
                             texts.size(), primaryProvider.name());
                    return result;
                }
            } catch (Exception e) {
                log.warn("[MultiEmbedding] Primary provider {} failed: {}", 
                        primaryProvider.name(), e.getMessage());
            }
        }

        // Try fallback embedding provider if primary fails
        LoreVaultMultiLlmProperties.ProviderProperties fallbackProvider = 
            multiLlmProperties.getFallbackEmbeddingProvider();
            
        if (fallbackProvider != null && fallbackProvider.enabled() && fallbackProvider.supportsEmbeddings()) {
            try {
                EmbeddingProvider provider = providerFactory.createProvider(fallbackProvider);
                List<double[]> result = provider.embedBatch(texts);
                if (!result.isEmpty()) {
                    log.info("[MultiEmbedding] Successfully embedded {} texts using fallback provider: {}", 
                            texts.size(), fallbackProvider.name());
                    return result;
                }
            } catch (Exception e) {
                log.error("[MultiEmbedding] Fallback provider {} also failed: {}", 
                         fallbackProvider.name(), e.getMessage());
            }
        }

        // All providers failed, return empty vectors
        log.error("[MultiEmbedding] All embedding providers failed for {} texts", texts.size());
        return texts.stream().map(t -> new double[0]).toList();
    }

    @Override
    public String getModelId() {
        LoreVaultMultiLlmProperties.ProviderProperties provider = 
            multiLlmProperties.getPrimaryEmbeddingProvider();
        return provider != null ? provider.embeddingModel() : "unknown";
    }

    @Override
    public int getDimension() {
        // TODO: This should be configurable per provider
        // For now, return the default dimension
        return 3072;
    }

    /**
     * Interface for provider-specific embedding implementations.
     */
    public interface EmbeddingProvider {
        List<double[]> embedBatch(List<String> texts);
        String getModelId();
        int getDimension();
    }

    /**
     * Factory for creating provider-specific embedding clients.
     */
    @Component
    public static class EmbeddingProviderFactory {
        
        public EmbeddingProvider createProvider(LoreVaultMultiLlmProperties.ProviderProperties providerConfig) {
            // For now, all providers use OpenAI-compatible API
            return new OpenAiCompatibleEmbeddingProvider(providerConfig);
        }
    }

    /**
     * OpenAI-compatible embedding provider implementation.
     * Works with any provider that supports OpenAI's embedding API format.
     */
    public static class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {
        
        private final LoreVaultMultiLlmProperties.ProviderProperties config;
        
        public OpenAiCompatibleEmbeddingProvider(LoreVaultMultiLlmProperties.ProviderProperties config) {
            this.config = config;
        }
        
        @Override
        public List<double[]> embedBatch(List<String> texts) {
            // TODO: Implement the actual HTTP call to the provider's embedding endpoint
            // This would be similar to the current EmbeddingModelAdapter implementation
            // but using the provider-specific configuration
            log.info("[EmbeddingProvider] Would embed {} texts using provider: {}", texts.size(), config.name());
            
            // For now, return empty vectors (placeholder)
            return texts.stream().map(t -> new double[0]).toList();
        }
        
        @Override
        public String getModelId() {
            return config.embeddingModel();
        }
        
        @Override
        public int getDimension() {
            // TODO: This should be provider-specific configuration
            return 3072;
        }
    }
}
