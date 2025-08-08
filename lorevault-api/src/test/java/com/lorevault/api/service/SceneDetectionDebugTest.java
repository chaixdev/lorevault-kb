package com.lorevault.api.service;

import com.lorevault.api.dto.SceneDetectionResult;
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
                <scene_index>1</scene_index>
                <context_summary>Elara returns to the familiar surroundings of her home, reflecting on her recent experiences and the lingering unease that accompanies them.</context_summary>
                <scene_break_reason>The scene shifts from the external environment of the town to Elara's private dwelling, marking a change in location and focus to her internal state.</scene_break_reason>
                <start_anchor><![CDATA[The air in her small cottage felt both comforting and suffocating.]]></start_anchor>
                <end_anchor><![CDATA[She found herself staring at the worn wooden table, its surface etched with the history of countless meals and conversations.]]></end_anchor>
              </scene>
              <scene>
                <scene_index>2</scene_index>
                <context_summary>Later, Elara ventures to the marketplace, seeking out the herbalist for a remedy to calm her troubled mind.</context_summary>
                <scene_break_reason>Elara leaves her home and moves to a public space, the marketplace, to interact with a different character and pursue a specific objective.</scene_break_reason>
                <start_anchor><![CDATA[The next morning, the sun painted the sky in hues of rose and gold, a stark contrast to the turmoil within Elara.]]></start_anchor>
                <end_anchor><![CDATA[The herbalist nodded slowly, her eyes crinkling at the corners as she reached for a small, dried pouch.]]></end_anchor>
              </scene>
              <scene>
                <scene_index>3</scene_index>
                <context_summary>Back in her cottage, Elara prepares the herbal remedy, finding a measure of peace in the ritual.</context_summary>
                <scene_break_reason>Elara returns to her home, indicating a shift back to a private setting after her excursion to the marketplace.</scene_break_reason>
                <start_anchor><![CDATA[With the precious pouch secured, Elara made her way back to the quiet solitude of her cottage.]]></start_anchor>
                <end_anchor><![CDATA[The scent of lavender and chamomile filled the air, a gentle balm to her frayed nerves.]]></end_anchor>
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
        assertThat(firstScene.endAnchor()).isEqualTo("She found herself staring at the worn wooden table, its surface etched with the history of countless meals and conversations.");
    }
}
