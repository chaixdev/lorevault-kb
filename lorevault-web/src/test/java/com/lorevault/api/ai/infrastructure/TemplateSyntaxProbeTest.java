package com.lorevault.api.ai.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateSyntaxProbeTest {

    @Test
    void detectSupportedVariableSyntax() {
        String name = "World";
        String resCurly = new PromptTemplate("Hello {name}").render(Map.of("name", name));
        String resDollar = new PromptTemplate("Hello $name$").render(Map.of("name", name));
        String resAngle = new PromptTemplate("Hello <name>").render(Map.of("name", name));

        // At least one renderer should substitute
        boolean any = resCurly.contains(name) || resDollar.contains(name) || resAngle.contains(name);
        assertThat(any).isTrue();
        // Print to help debug locally if needed
        System.out.println("curly=" + resCurly + ", dollar=" + resDollar + ", angle=" + resAngle);
    }
}
