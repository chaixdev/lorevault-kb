package com.lorevault.api.service;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.service.content.SceneDetectionXmlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SceneDetectionXmlParser.
 * Tests the parseResponse method and related XML parsing functionality.
 */
class SceneDetectionXmlParsingTest {

    private SceneDetectionXmlParser xmlParser;

    @BeforeEach
    void setUp() {
        xmlParser = new SceneDetectionXmlParser();
    }

    @Test
    void parseResponse_ShouldParseValidXml() {
        String validXml = """
            <scenes>
              <scene>
                <index>1</index>
                <context_summary>The crew prepares for arrival on the ship's bridge.</context_summary>
                <start_anchor><![CDATA[The crew gathered on the bridge]]></start_anchor>
                <break_reason>Opening scene of the chapter</break_reason>
                <chronology>R:temporal.meets</chronology>
                <chronology_certainty>Heuristic</chronology_certainty>
                <chronology_marker>Chapter beginning</chronology_marker>
              </scene>
              <scene>
                <index>2</index>
                <context_summary>The landing party explores the jungle on Xylos.</context_summary>
                <start_anchor><![CDATA[Three hours later, the landing party]]></start_anchor>
                <break_reason>Change in time and location from bridge to jungle</break_reason>
                <chronology>R:temporal.after</chronology>
                <chronology_certainty>Explicit</chronology_certainty>
                <chronology_marker>Three hours later</chronology_marker>
              </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = xmlParser.parseResponse(validXml, 1000);

        assertThat(results).hasSize(2);
        
        // Verify first scene
        SceneDetectionResult scene1 = results.get(0);
        assertThat(scene1.sceneIndex()).isEqualTo(1);
        assertThat(scene1.contextSummary()).contains("crew prepares for arrival");
        assertThat(scene1.startAnchor()).contains("The crew gathered on the bridge");
        assertThat(scene1.breakReason()).isEqualTo("Opening scene of the chapter");
        assertThat(scene1.chronology()).isEqualTo("R:temporal.meets");
        assertThat(scene1.chronologyCertainty()).isEqualTo("Heuristic");
        assertThat(scene1.chronologyMarker()).isEqualTo("Chapter beginning");
        
        // Verify second scene
        SceneDetectionResult scene2 = results.get(1);
        assertThat(scene2.sceneIndex()).isEqualTo(2);
        assertThat(scene2.contextSummary()).contains("landing party explores");
        assertThat(scene2.startAnchor()).contains("Three hours later, the landing party");
        assertThat(scene2.breakReason()).contains("Change in time and location");
        assertThat(scene2.chronology()).isEqualTo("R:temporal.after");
        assertThat(scene2.chronologyCertainty()).isEqualTo("Explicit");
        assertThat(scene2.chronologyMarker()).isEqualTo("Three hours later");
    }

    @Test
    void parseResponse_ShouldHandleMarkdownWrapping() {
        String xmlWithMarkdown = """
            ```xml
            <scenes>
              <scene>
                <index>1</index>
                <context_summary>A simple test scene.</context_summary>
                <start_anchor><![CDATA[The beginning of text]]></start_anchor>
                <break_reason>Simple scene transition</break_reason>
                <chronology>R:temporal.meets</chronology>
                <chronology_certainty>Heuristic</chronology_certainty>
                <chronology_marker>Beginning of narrative</chronology_marker>
              </scene>
            </scenes>
            ```
            """;

        List<SceneDetectionResult> results = xmlParser.parseResponse(xmlWithMarkdown, 1000);

        assertThat(results).hasSize(1);
        SceneDetectionResult scene = results.get(0);
        assertThat(scene.sceneIndex()).isEqualTo(1);
        assertThat(scene.contextSummary()).isEqualTo("A simple test scene.");
        assertThat(scene.startAnchor()).isEqualTo("The beginning of text");
        assertThat(scene.breakReason()).isEqualTo("Simple scene transition");
    }

    @Test
    void parseResponse_ShouldSkipIncompleteScenes() {
        String xmlWithIncompleteScene = """
            <scenes>
              <scene>
                <index>1</index>
                <context_summary>Complete scene.</context_summary>
                <start_anchor><![CDATA[Complete start]]></start_anchor>
                <break_reason>Valid scene break</break_reason>
                <chronology>R:temporal.meets</chronology>
                <chronology_certainty>Heuristic</chronology_certainty>
                <chronology_marker>Chapter start</chronology_marker>
              </scene>
              <scene>
                <index>2</index>
                <context_summary>Incomplete scene missing start anchor.</context_summary>
                <!-- Missing start_anchor and other required fields -->
              </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = xmlParser.parseResponse(xmlWithIncompleteScene, 1000);

        // Should only return the complete scene, skip the incomplete one
        assertThat(results).hasSize(1);
        assertThat(results.get(0).sceneIndex()).isEqualTo(1);
    }

    @Test
    void parseResponse_ShouldReturnEmptyListForInvalidXml() {
        String invalidXml = "This is not XML at all!";

        List<SceneDetectionResult> results = xmlParser.parseResponse(invalidXml, 1000);

        assertThat(results).isEmpty();
    }

    /**
     * Note: The individual helper method tests have been removed as we moved 
     * from regex-based to DOM-based parsing in the XML parser implementation.
     */
}
