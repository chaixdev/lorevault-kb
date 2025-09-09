package com.lorevault.api.infrastructure.prompt;

import com.lorevault.api.application.port.PromptRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter that implements PromptRepositoryPort.
 * Uses PromptLocationResolver for path resolution, PromptCache for caching,
 * and ResourceLoader for actual file loading.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptRepositoryAdapter implements PromptRepositoryPort {

    private final PromptLocationResolver locationResolver;
    private final PromptCache cache;
    private final ResourceLoader resourceLoader;

    @Override
    public PromptTemplate get(String name) {
        try {
            return cache.getOrLoad(name, () -> {
                // Resolve logical name to resource path
                String resourcePath = locationResolver.resolve(name);
                log.debug("Loading prompt '{}' from: {}", name, resourcePath);

                // Load from resource
                var resource = resourceLoader.getResource(resourcePath);
                if (!resource.exists()) {
                    throw new RuntimeException("Prompt resource not found: " + resourcePath);
                }

                // Create template (renderer selection is based on file extension by Spring AI)
                PromptTemplate template = new PromptTemplate(resource);
                log.debug("Loaded prompt: {}", name);
                
                return template;
            });

        } catch (Exception e) {
            log.error("Failed to load prompt '{}': {}", name, e.getMessage(), e);
            throw new RuntimeException("Failed to load prompt: " + name, e);
        }
    }

    @Override
    public void clearCache() {
        log.info("Clearing prompt cache");
        cache.clear();
    }

    @Override
    public int getCacheSize() {
        return cache.size();
    }
}
