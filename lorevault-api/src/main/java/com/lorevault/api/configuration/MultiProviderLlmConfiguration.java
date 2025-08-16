package com.lorevault.api.configuration;

import com.lorevault.api.configuration.properties.LoreVaultMultiLlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Multi-Provider LLM Configuration
 * 
 * Creates multiple ChatClient beans programmatically, one for each enabled provider.
 * Uses custom configuration properties instead of Spring AI auto-configuration
 * to support multiple providers simultaneously.
 */
@Configuration
@ConditionalOnProperty(name = "lorevault.multi-llm.enabled", havingValue = "true")
@EnableConfigurationProperties(LoreVaultMultiLlmProperties.class)
@Slf4j
public class MultiProviderLlmConfiguration {

    private final LoreVaultMultiLlmProperties multiLlmProperties;

    public MultiProviderLlmConfiguration(LoreVaultMultiLlmProperties multiLlmProperties) {
        this.multiLlmProperties = multiLlmProperties;
    }

    /**
     * Creates a map of provider name -> ChatClient for all enabled providers.
     * Each ChatClient is configured with the provider-specific settings.
     */
    @Bean
    public Map<String, ChatClient> providerChatClients() {
        log.info("Creating multi-provider ChatClient beans for {} providers", 
                multiLlmProperties.providers().size());
        
        return multiLlmProperties.providers().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> createChatClientForProvider(entry.getKey(), entry.getValue())
                ));
    }

    /**
     * Creates a primary ChatClient bean that delegates to the appropriate provider
     * based on task type. This maintains compatibility with existing code.
     */
    @Bean
    @Primary
    public ChatClient primaryChatClient(Map<String, ChatClient> providerChatClients) {
        // For now, return the first available provider's ChatClient
        // TODO: Implement task-based routing logic
        String firstProvider = providerChatClients.keySet().iterator().next();
        ChatClient primaryClient = providerChatClients.get(firstProvider);
        
        log.info("Primary ChatClient configured for provider: {}", firstProvider);
        return primaryClient;
    }

    /**
     * Task-based ChatClient resolver service.
     * Maps specific tasks (extract-scenes, embeddings, etc.) to appropriate providers.
     */
    @Bean
    public TaskChatClientResolver taskChatClientResolver(Map<String, ChatClient> providerChatClients) {
        return new TaskChatClientResolver(multiLlmProperties, providerChatClients);
    }

    /**
     * Creates a ChatClient for a specific provider.
     * TODO: Implement proper Spring AI programmatic configuration once API issues are resolved.
     * For now, creates a simple placeholder that will be replaced with real implementation.
     */
    private ChatClient createChatClientForProvider(String providerName, 
                                                  LoreVaultMultiLlmProperties.ProviderProperties provider) {
        log.info("Creating ChatClient for provider: {} ({})", providerName, provider.name());
        
        // TODO: Replace with proper OpenAI-compatible ChatClient creation
        // Currently blocked by Spring AI API constructor issues
        log.warn("Multi-provider ChatClient creation not yet implemented - using placeholder");
        
        // Return null for now - this will cause bean creation to fail, 
        // which is appropriate until we implement the real solution
        throw new UnsupportedOperationException(
            "Multi-provider ChatClient creation not yet implemented. " +
            "Spring AI API constructor research needed."
        );
    }

    /**
     * Service class for resolving ChatClient based on task type.
     */
    public static class TaskChatClientResolver {
        private final LoreVaultMultiLlmProperties multiLlmProperties;
        private final Map<String, ChatClient> providerChatClients;

        public TaskChatClientResolver(LoreVaultMultiLlmProperties multiLlmProperties, 
                                     Map<String, ChatClient> providerChatClients) {
            this.multiLlmProperties = multiLlmProperties;
            this.providerChatClients = providerChatClients;
        }

        /**
         * Resolves the appropriate ChatClient for a given task.
         * Uses primary provider, falls back to fallback provider if configured.
         */
        public ChatClient resolveForTask(String taskName) {
            LoreVaultMultiLlmProperties.TaskMappingProperties tasks = multiLlmProperties.tasks();
            
            // Get task configuration
            LoreVaultMultiLlmProperties.TaskProviderMapping taskMapping = 
                    getTaskMapping(tasks, taskName);
            
            if (taskMapping == null) {
                throw new IllegalArgumentException("Unknown task: " + taskName);
            }
            
            // Try primary provider first
            String primaryProvider = taskMapping.primary();
            ChatClient primaryClient = providerChatClients.get(primaryProvider);
            if (primaryClient != null) {
                return primaryClient;
            }
            
            // Try fallback provider
            String fallbackProvider = taskMapping.fallback();
            if (fallbackProvider != null) {
                ChatClient fallbackClient = providerChatClients.get(fallbackProvider);
                if (fallbackClient != null) {
                    return fallbackClient;
                }
            }
            
            throw new IllegalStateException("No available provider for task: " + taskName);
        }

        private LoreVaultMultiLlmProperties.TaskProviderMapping getTaskMapping(
                LoreVaultMultiLlmProperties.TaskMappingProperties tasks, String taskName) {
            return switch (taskName) {
                case "embeddings" -> tasks.embeddings();
                case "extract-scenes" -> tasks.extractScenes();
                case "extract-entities" -> tasks.extractEntities();
                case "generate-response" -> tasks.generateResponse();
                default -> null;
            };
        }
    }
}
