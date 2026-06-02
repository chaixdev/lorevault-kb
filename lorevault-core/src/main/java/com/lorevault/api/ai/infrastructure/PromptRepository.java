package com.lorevault.api.ai.infrastructure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Loads and caches prompt templates from configured resources.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptRepository {

    private final PromptLocationResolver locationResolver;
    private final PromptCache cache;
    private final ResourceLoader resourceLoader;

    public PromptTemplate get(PromptName name) {
        try {
            String promptKey = name.promptKey();
            return cache.getOrLoad(promptKey, () -> {
                // Resolve logical name to resource path
                String resourcePath = locationResolver.resolve(name);
                log.debug("Loading prompt '{}' from: {}", promptKey, resourcePath);

                // Load from resource
                var resource = resourceLoader.getResource(resourcePath);
                if (!resource.exists()) {
                    throw new RuntimeException("Prompt resource not found: " + resourcePath);
                }

                // Create template (renderer selection is based on file extension by Spring AI)
                PromptTemplate template = new PromptTemplate(resource);
                log.debug("Loaded prompt: {}", promptKey);
                
                return template;
            });

        } catch (Exception e) {
            log.error("Failed to load prompt '{}': {}", name.promptKey(), e.getMessage(), e);
            throw new RuntimeException("Failed to load prompt: " + name.promptKey(), e);
        }
    }

    public void clearCache() {
        log.info("Clearing prompt cache");
        cache.clear();
    }

    public int getCacheSize() {
        return cache.size();
    }
}
