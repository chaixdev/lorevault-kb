package com.lorevault.api.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring AI integration with Gemini via OpenAI compatibility endpoint.
 */
@Configuration
public class SpringAiConfig {

    /**
     * Creates a ChatClient bean for dependency injection.
     * The OpenAiChatModel will be auto-configured by Spring AI using the properties
     * defined in application.properties.
     * 
     * @param chatModel The auto-configured OpenAI-compatible chat model (pointing to Gemini)
     * @return Configured ChatClient instance
     */
    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
