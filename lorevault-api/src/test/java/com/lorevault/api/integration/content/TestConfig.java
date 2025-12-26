package com.lorevault.api.integration.content;

import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.service.content.retry.RetryAwareSceneDetectionService;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
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
    
    @Bean
    @Qualifier("nlpSmall")
    public ChatClient nlpSmallChatClient() {
        return mock(ChatClient.class);
    }

    @Bean
    @Qualifier("nlpBig")
    @Primary
    public ChatClient nlpBig() {
        return mock(ChatClient.class);
    }

    /**
     * Mock the SceneDetectionPort for integration tests.
     * RetryAwareSceneDetectionService now implements this port directly.
     */
    @Bean
    @Primary
    public SceneDetectionPort sceneDetectionPort() {
        return Mockito.mock(SceneDetectionPort.class);
    }
}