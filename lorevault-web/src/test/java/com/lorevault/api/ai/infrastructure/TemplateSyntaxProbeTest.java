package com.lorevault.api.ai.infrastructure;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

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
