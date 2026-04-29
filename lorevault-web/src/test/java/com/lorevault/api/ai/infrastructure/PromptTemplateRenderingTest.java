package com.lorevault.api.ai.infrastructure;

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

    private PromptRepository repo;

    @BeforeEach
    void setUp() {
        // Configure with basePath only; other configs can be null to use defaults
        LoreVaultPromptProperties props = new LoreVaultPromptProperties(
                "classpath:prompts",
                null, null, null, null, null
        );
        PromptLocationResolver resolver = new PromptLocationResolver(props);
        PromptCache cache = new PromptCache(60); // 60s TTL is fine for tests
        repo = new PromptRepository(resolver, cache, new DefaultResourceLoader());
    }

    @Test
    void sceneAnalysisUserTemplate_renders_with_variables() {
        PromptTemplate t = repo.get("scene-analysis-user");

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

    @Test
    void eventCorefUserTemplate_renders_with_xml_payload_without_template_parse_errors() {
        PromptTemplate t = repo.get("event-coref-user");

        Map<String, Object> vars = new HashMap<>();
        vars.put("chapterId", "chapter-123");
        vars.put("scenes", "<scene id=\"scene-1\"><mention id=\"mention-1\">payload</mention></scene>");

        String rendered = t.render(vars);

        assertThat(rendered).contains("chapter-123");
        assertThat(rendered).contains("<mentions>");
        assertThat(rendered).contains("<scene id=\"scene-1\"><mention id=\"mention-1\">payload</mention></scene>");
        assertThat(rendered).contains("sameEventGroups");
        assertThat(rendered).contains("Return valid JSON only.");
        assertThat(rendered).doesNotContain("Return a JSON object matching this schema:");
    }

    @Test
    void eventMergeUserTemplate_renders_with_pair_payload_without_template_parse_errors() {
        PromptTemplate t = repo.get("event-merge-user");

        Map<String, Object> vars = new HashMap<>();
        vars.put("annScore", 0.91);
        vars.put("eventId1", "00000000-0000-0000-0000-000000000001");
        vars.put("eventId2", "00000000-0000-0000-0000-000000000002");
        vars.put("event1DisplayName", "Battle at Helm's Deep");
        vars.put("event1NormalizedName", "battle at helms deep");
        vars.put("event1RepresentativeEventType", "BATTLE");
        vars.put("event1AggregateCard", "card a");
        vars.put("event1SupportedAliases", "<item>Helm's Deep battle</item>");
        vars.put("event1SupportedEventTypes", "<item>BATTLE</item>");
        vars.put("event1EvidenceSnippets", "<item>evidence a</item>");
        vars.put("event2DisplayName", "The assault on Helm's Deep");
        vars.put("event2NormalizedName", "the assault on helms deep");
        vars.put("event2RepresentativeEventType", "ASSAULT");
        vars.put("event2AggregateCard", "card b");
        vars.put("event2SupportedAliases", "<item>assault</item>");
        vars.put("event2SupportedEventTypes", "<item>ASSAULT</item>");
        vars.put("event2EvidenceSnippets", "<item>evidence b</item>");

        String rendered = t.render(vars);

        assertThat(rendered).contains("<pair>");
        assertThat(rendered).contains("<annScore>0.91</annScore>");
        assertThat(rendered).contains("Battle at Helm's Deep");
        assertThat(rendered).contains("The assault on Helm's Deep");
        assertThat(rendered).contains("Return valid JSON only with keys: decision, confidence, rationale.");
    }
}
