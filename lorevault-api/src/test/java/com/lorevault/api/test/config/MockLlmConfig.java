package com.lorevault.api.test.config;

import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.*;

/**
 * Provides mocked LLM-related beans (ChatClient) for tests so that
 * no real external LLM calls are made and health checks pass fast.
 */
@Configuration
public class MockLlmConfig {

    @Bean
    @Primary
    ChatClient mockChatClient() {
        ChatClient mock = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        // Health check path uses chatClient.prompt(Prompt)
        Mockito.when(mock.prompt(any(org.springframework.ai.chat.prompt.Prompt.class))
                .call()
                .content())
                .thenReturn("OK");
        // Scene detection path builds via fluent builder prompt()
        Mockito.when(mock.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .content())
                .thenReturn("<scenes></scenes>");
        return mock;
    }
}
