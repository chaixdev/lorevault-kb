package com.lorevault.api.service;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.service.content.SceneDetectionXmlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to debug the specific XML parsing issue reported by the user.
 */
class SceneDetectionDebugTest {

    private SceneDetectionXmlParser xmlParser;

    @BeforeEach
    void setUp() {
        xmlParser = new SceneDetectionXmlParser();
    }

    @Test
    void debugXmlParsingIssue() {
        String problematicXml = """
            <scenes>
              <scene>
                <index>1</index>
                <context_summary>Elara returns to the familiar surroundings of her home, reflecting on her recent experiences and the lingering unease that accompanies them.</context_summary>
                <break_reason>The scene shifts from the external environment of the town to Elara's private dwelling, marking a change in location and focus to her internal state.</break_reason>
                <start_anchor><![CDATA[The air in her small cottage felt both comforting and suffocating.]]></start_anchor>
                <chronology>R:temporal.meets</chronology>
                <chronology_certainty>Heuristic</chronology_certainty>
                <chronology_marker>Scene start</chronology_marker>
              </scene>
              <scene>
                <index>2</index>
                <context_summary>Later, Elara ventures to the marketplace, seeking out the herbalist for a remedy to calm her troubled mind.</context_summary>
                <break_reason>Elara leaves her home and moves to a public space, the marketplace, to interact with a different character and pursue a specific objective.</break_reason>
                <start_anchor><![CDATA[The next morning, the sun painted the sky in hues of rose and gold, a stark contrast to the turmoil within Elara.]]></start_anchor>
                <chronology>R:temporal.after</chronology>
                <chronology_certainty>Explicit</chronology_certainty>
                <chronology_marker>The next morning</chronology_marker>
              </scene>
              <scene>
                <index>3</index>
                <context_summary>Back in her cottage, Elara prepares the herbal remedy, finding a measure of peace in the ritual.</context_summary>
                <break_reason>Elara returns to her home, indicating a shift back to a private setting after her excursion to the marketplace.</break_reason>
                <start_anchor><![CDATA[With the precious pouch secured, Elara made her way back to the quiet solitude of her cottage.]]></start_anchor>
                <chronology>R:temporal.after</chronology>
                <chronology_certainty>StronglyImplied</chronology_certainty>
                <chronology_marker>Back in her cottage</chronology_marker>
              </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = 
            xmlParser.parseResponse(problematicXml, 1000);

        // This should not be empty if parsing works correctly
  assertThat(results).isNotEmpty();
  assertThat(results).hasSize(3);
        
        // Verify first scene
        SceneDetectionResult firstScene = results.get(0);
  assertThat(firstScene.sceneIndex()).isEqualTo(1);
  assertThat(firstScene.startAnchor()).isEqualTo("The air in her small cottage felt both comforting and suffocating.");
    }
}
