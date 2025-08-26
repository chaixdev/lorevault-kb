package com.lorevault.api.integration.content;

import com.lorevault.api.infrastructure.adapter.ThreadLocalJobContextAdapter;
import com.lorevault.api.infrastructure.ai.openai.OpenAiSceneDetectionAdapter;
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

    @Bean
    public RetryAwareSceneDetectionService retryAwareSceneDetectionService() {
        return Mockito.mock(RetryAwareSceneDetectionService.class);
    }

    @Bean
    public ThreadLocalJobContextAdapter threadLocalJobContextAdapter() {
        return Mockito.mock(ThreadLocalJobContextAdapter.class);
    }

    @Bean
    public OpenAiSceneDetectionAdapter openAiSceneDetectionAdapter() {
        return Mockito.mock(OpenAiSceneDetectionAdapter.class);
    }
}