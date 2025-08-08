package com.lorevault.api.service;

import com.lorevault.api.dto.SceneDetectionResult;
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
                <scene_index>1</scene_index>
                <context_summary>The crew prepares for arrival on the ship's bridge.</context_summary>
                <start_anchor>The crew gathered on the bridge</start_anchor>
                <end_anchor>planet of Xylos grew larger.</end_anchor>
                <scene_break_reason>Change in setting from ship to planet</scene_break_reason>
              </scene>
              <scene>
                <scene_index>2</scene_index>
                <context_summary>The landing party explores the jungle on Xylos.</context_summary>
                <start_anchor>Three hours later, the landing party</start_anchor>
                <end_anchor>cries of unseen aliens.</end_anchor>
                <scene_break_reason>Change in time and location from bridge to jungle</scene_break_reason>
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
        assertThat(scene1.endAnchor()).contains("planet of Xylos grew larger.");
        
        // Verify second scene
        SceneDetectionResult scene2 = results.get(1);
        assertThat(scene2.sceneIndex()).isEqualTo(2);
        assertThat(scene2.contextSummary()).contains("landing party explores");
        assertThat(scene2.startAnchor()).contains("Three hours later, the landing party");
        assertThat(scene2.endAnchor()).contains("cries of unseen aliens.");
    }

    @Test
    void parseResponse_ShouldHandleMarkdownWrapping() {
        String xmlWithMarkdown = """
            ```xml
            <scenes>
              <scene>
                <scene_index>1</scene_index>
                <context_summary>A simple test scene.</context_summary>
                <start_anchor>The beginning of text</start_anchor>
                <end_anchor>the end of text.</end_anchor>
                <scene_break_reason>Simple scene transition</scene_break_reason>
              </scene>
            </scenes>
            ```
            """;

        List<SceneDetectionResult> results = xmlParser.parseResponse(xmlWithMarkdown, 1000);

        assertThat(results).hasSize(1);
        SceneDetectionResult scene = results.get(0);
        assertThat(scene.sceneIndex()).isEqualTo(1);
        assertThat(scene.contextSummary()).isEqualTo("A simple test scene.");
    }

    @Test
    void parseResponse_ShouldSkipIncompleteScenes() {
        String xmlWithIncompleteScene = """
            <scenes>
              <scene>
                <scene_index>1</scene_index>
                <context_summary>Complete scene.</context_summary>
                <start_anchor>Complete start</start_anchor>
                <end_anchor>complete end.</end_anchor>
                <scene_break_reason>Valid scene break</scene_break_reason>
              </scene>
              <scene>
                <scene_index>2</scene_index>
                <context_summary>Incomplete scene missing snippets.</context_summary>
                <!-- Missing start_anchor and end_anchor -->
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
