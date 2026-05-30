package com.lorevault.api.integration;

import com.lorevault.api.graph.event.scene.Scene;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Test configuration to provide mock beans for integration tests.
 */
@TestConfiguration
public class TestConfig {
    
    @Bean("testNlpSmallChatClient")
    @Qualifier("nlpSmall")
    public ChatClient nlpSmallChatClient() {
        return mock(ChatClient.class);
    }

    @Bean("testNlpBigChatClient")
    @Qualifier("nlpBig")
    @Primary
    public ChatClient nlpBig() {
        return mock(ChatClient.class);
    }

    @Bean("testEmbeddingModel")
    @Qualifier("embeddingModel")
    public EmbeddingModel embeddingModel() {
        return mock(EmbeddingModel.class);
    }

    /**
     * Mock the SceneDetectionService for integration tests.
     */
    @Bean
    @Primary
    public Scene.SceneDetectionService sceneDetectionService() {
        return Mockito.mock(Scene.SceneDetectionService.class);
    }
}
