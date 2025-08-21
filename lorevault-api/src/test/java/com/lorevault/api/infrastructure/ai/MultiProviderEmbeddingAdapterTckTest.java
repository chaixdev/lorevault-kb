package com.lorevault.api.infrastructure.ai;

import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.configuration.properties.LoreVaultMultiLlmProperties;
import com.lorevault.api.tck.ai.EmbeddingPortTCK;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * TCK for MultiProviderEmbeddingAdapter using mocked configuration and provider factory.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MultiProviderEmbeddingAdapterTckTest extends EmbeddingPortTCK {

    @Mock private LoreVaultMultiLlmProperties mockProperties;
    @Mock private MultiProviderEmbeddingAdapter.EmbeddingProviderFactory mockFactory;
    @Mock private MultiProviderEmbeddingAdapter.EmbeddingProvider mockProvider;
    
    private MultiProviderEmbeddingAdapter adapter;

    @BeforeEach
    void setUp() {
        // Mock properties for primary/fallback providers
        var primaryConfig = mock(LoreVaultMultiLlmProperties.ProviderProperties.class);
        var fallbackConfig = mock(LoreVaultMultiLlmProperties.ProviderProperties.class);
        
        when(mockProperties.getPrimaryEmbeddingProvider()).thenReturn(primaryConfig);
        when(mockProperties.getFallbackEmbeddingProvider()).thenReturn(fallbackConfig);
        
        // Configure primary provider
        when(primaryConfig.enabled()).thenReturn(true);
        when(primaryConfig.supportsEmbeddings()).thenReturn(true);
        when(primaryConfig.name()).thenReturn("primary-provider");
        
        // Mock provider factory to return our mock provider
        when(mockFactory.createProvider(any())).thenReturn(mockProvider);
        
        // Mock provider behavior
        when(mockProvider.embedBatch(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(text -> new double[]{0.1, 0.2, 0.3}).toList();
        });
        when(mockProvider.getModelId()).thenReturn("mock-model-v1");
        when(mockProvider.getDimension()).thenReturn(3);
        
        adapter = new MultiProviderEmbeddingAdapter(mockProperties, mockFactory);
    }

    @Override
    protected EmbeddingPort createPort() {
        return adapter;
    }
}
