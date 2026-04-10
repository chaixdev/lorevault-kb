package com.lorevault.api.config;

import com.lorevault.api.config.LoreVaultModelsProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Manual Spring AI configuration using our own model properties.
 * Completely bypasses Spring AI auto-configuration for full control.
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(LoreVaultModelsProperties.class)
public class SpringAiConfig {

    /**
     * Primary ChatClient for the "big" model - used as default.
     */
    @Bean
    @Primary
    @Profile("!test")
    public ChatClient chatClient(LoreVaultModelsProperties models) {
        var cfg = models.nlpBig();
        var openAiApi = OpenAiApi.builder()
            .baseUrl(cfg.baseUrl())
            .apiKey(cfg.apiKey())
            .completionsPath(cfg.completionsPath())
            .build();
        OpenAiChatOptions defaults = OpenAiChatOptions.builder()
            .model(cfg.model())
            .temperature(cfg.temperature())
            .topP(cfg.topP())
            .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(defaults)
            .build();
        return ChatClient.builder(model).build();
    }

    /**
     * ChatClient for the "small" model slot.
     */
    @Bean
    @Profile("!test")
    @Qualifier("nlpSmall")
    public ChatClient nlpSmallChatClient(LoreVaultModelsProperties models) {
        var cfg = models.nlpSmall();
        var openAiApi = OpenAiApi.builder()
            .baseUrl(cfg.baseUrl())
            .apiKey(cfg.apiKey())
            .completionsPath(cfg.completionsPath())
            .build();
        OpenAiChatOptions defaults = OpenAiChatOptions.builder()
            .model(cfg.model())
            .temperature(cfg.temperature())
            .topP(cfg.topP())
            .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(defaults)
            .build();
        return ChatClient.builder(model).build();
    }

    /**
     * ChatClient for the "big" model slot (same as primary but with explicit qualifier).
     */
    @Bean
    @Profile("!test")
    @Qualifier("nlpBig")
    public ChatClient nlpBigChatClient(LoreVaultModelsProperties models) {
        var cfg = models.nlpBig();
        var openAiApi = OpenAiApi.builder()
            .baseUrl(cfg.baseUrl())
            .apiKey(cfg.apiKey())
            .completionsPath(cfg.completionsPath())
            .build();
        OpenAiChatOptions defaults = OpenAiChatOptions.builder()
            .model(cfg.model())
            .temperature(cfg.temperature())
            .topP(cfg.topP())
            .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(defaults)
            .build();
        return ChatClient.builder(model).build();
    }

    /**
     * Dedicated EmbeddingModel for the embedding slot.
     */
    @Bean
    @Profile("!test")
    @Qualifier("embeddingModel")
    public EmbeddingModel embeddingModel(LoreVaultModelsProperties models) {
        var cfg = models.embedding();
        var openAiApi = OpenAiApi.builder()
            .baseUrl(cfg.baseUrl())
            .apiKey(cfg.apiKey())
            .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(cfg.model())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, org.springframework.ai.document.MetadataMode.EMBED, options);
    }
}
