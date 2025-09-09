package com.lorevault.api.infrastructure.prompt;

import com.lorevault.api.configuration.properties.LoreVaultPromptProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity tests for system prompt templates to prevent runtime failures
 * like ST4 template parsing errors when loading triad system prompts.
 */
public class PromptSystemTemplateRenderingTest {

    private PromptRepositoryAdapter repo;

    @BeforeEach
    void setUp() {
        LoreVaultPromptProperties props = new LoreVaultPromptProperties(
                "classpath:prompts",
                null, null, null, null, null
        );
        PromptLocationResolver resolver = new PromptLocationResolver(props);
        PromptCache cache = new PromptCache(60);
        repo = new PromptRepositoryAdapter(resolver, cache, new DefaultResourceLoader());
    }

    @Test
    void sceneDetectionPass2SystemTemplate_renders_without_variables() {
        PromptTemplate t = repo.get("scene-detection-pass2");
        String rendered = t.render(java.util.Map.of());
        assertThat(rendered).contains("<scene_analysis>");
        assertThat(rendered).contains("<relationships>");
        assertThat(rendered).contains("<current_scene_entities>");
    }
}
