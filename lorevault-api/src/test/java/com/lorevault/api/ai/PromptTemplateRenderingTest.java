package com.lorevault.api.ai;

import com.lorevault.api.config.LoreVaultPromptProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity tests for ST4-based prompt templates to prevent runtime failures
 * like "The template string is not valid".
 */
public class PromptTemplateRenderingTest {

    private PromptRepositoryAdapter repo;

    @BeforeEach
    void setUp() {
        // Configure with basePath only; other configs can be null to use defaults
        LoreVaultPromptProperties props = new LoreVaultPromptProperties(
                "classpath:prompts",
                null, null, null, null, null
        );
        PromptLocationResolver resolver = new PromptLocationResolver(props);
        PromptCache cache = new PromptCache(60); // 60s TTL is fine for tests
        repo = new PromptRepositoryAdapter(resolver, cache, new DefaultResourceLoader());
    }

    @Test
    void sceneDetectionPass2UserTemplate_renders_with_variables() {
        PromptTemplate t = repo.get("scene-detection-pass2-user");

        Map<String, Object> vars = new HashMap<>();
        vars.put("prev_context_summary", "prev ctx");
        vars.put("prev_time_indicators", "prev time");
        vars.put("prev_break_reason", "prev break");
        vars.put("prev_text", "PREV_TEXT");

        vars.put("curr_context_summary", "curr ctx");
        vars.put("curr_time_indicators", "curr time");
        vars.put("curr_break_reason", "curr break");
        vars.put("curr_text", "CURR_TEXT");

        vars.put("next_context_summary", "next ctx");
        vars.put("next_time_indicators", "next time");
        vars.put("next_break_reason", "next break");
        vars.put("next_text", "NEXT_TEXT");

        String rendered = t.render(vars);
        assertThat(rendered).contains("PREV_TEXT");
        assertThat(rendered).contains("CURR_TEXT");
        assertThat(rendered).contains("NEXT_TEXT");
        assertThat(rendered).contains("prev ctx");
        assertThat(rendered).contains("curr ctx");
        assertThat(rendered).contains("next ctx");
    }
}
