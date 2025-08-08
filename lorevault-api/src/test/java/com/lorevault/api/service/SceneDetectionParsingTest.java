package com.lorevault.api.service;

import com.lorevault.api.dto.SceneDetectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for XML parsing logic in SceneDetectionXmlParser.
 * Validates that the parser correctly handles various XML formats, CDATA sections,
 * markdown wrapping, and error conditions.
 */
class SceneDetectionParsingTest {

    private SceneDetectionXmlParser xmlParser;

    @BeforeEach
    void setUp() {
        // Create parser instance for isolated testing
        xmlParser = new SceneDetectionXmlParser();
    }

    @Test
    void parseSceneDetectionResponse_ShouldParseValidXml() {
        String validXml = """
            <scenes>
              <scene>
                <scene_index>1</scene_index>
                <context_summary>The crew prepares for arrival on the ship's bridge.</context_summary>
                <scene_break_reason>Initial scene of the chapter.</scene_break_reason>
                <start_anchor><![CDATA[The crew gathered on the bridge]]></start_anchor>
                <end_anchor><![CDATA[planet of Xylos grew larger.]]></end_anchor>
              </scene>
              <scene>
                <scene_index>2</scene_index>
                <context_summary>The landing party explores the jungle on Xylos.</context_summary>
                <scene_break_reason>Time jump and location change to planet surface.</scene_break_reason>
                <start_anchor><![CDATA[Three hours later, the landing party]]></start_anchor>
                <end_anchor><![CDATA[cries of unseen aliens.]]></end_anchor>
              </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = 
            xmlParser.parseResponse(validXml, 1000);

        assertThat(results).hasSize(2);
        
        // Verify first scene
        SceneDetectionResult firstScene = results.get(0);
        assertThat(firstScene.sceneIndex()).isEqualTo(1);
        assertThat(firstScene.contextSummary()).isEqualTo("The crew prepares for arrival on the ship's bridge.");
        assertThat(firstScene.startAnchor()).isEqualTo("The crew gathered on the bridge");
        assertThat(firstScene.endAnchor()).isEqualTo("planet of Xylos grew larger.");
        assertThat(firstScene.sceneBreakReason()).isEqualTo("Initial scene of the chapter.");
        
        // Verify second scene
        SceneDetectionResult secondScene = results.get(1);
        assertThat(secondScene.sceneIndex()).isEqualTo(2);
        assertThat(secondScene.contextSummary()).isEqualTo("The landing party explores the jungle on Xylos.");
        assertThat(secondScene.startAnchor()).isEqualTo("Three hours later, the landing party");
        assertThat(secondScene.endAnchor()).isEqualTo("cries of unseen aliens.");
        assertThat(secondScene.sceneBreakReason()).isEqualTo("Time jump and location change to planet surface.");
    }

    @Test
    void parseSceneDetectionResponse_ShouldHandleMarkdownWrapping() {
        String xmlWithMarkdown = """
            ```xml
            <scenes>
              <scene>
                <scene_index>1</scene_index>
                <context_summary>A test scene.</context_summary>
                <scene_break_reason>Initial scene.</scene_break_reason>
                <start_anchor><![CDATA[Once upon a time]]></start_anchor>
                <end_anchor><![CDATA[the end.]]></end_anchor>
              </scene>
            </scenes>
            ```
            """;

        List<SceneDetectionResult> results = 
            xmlParser.parseResponse(xmlWithMarkdown, 1000);

        assertThat(results).hasSize(1);
        SceneDetectionResult scene = results.get(0);
        assertThat(scene.sceneIndex()).isEqualTo(1);
        assertThat(scene.contextSummary()).isEqualTo("A test scene.");
    }

    @Test
    void parseSceneDetectionResponse_ShouldHandleGenericMarkdownWrapping() {
        String xmlWithGenericMarkdown = """
            ```
            <scenes>
              <scene>
                <scene_index>1</scene_index>
                <context_summary>A test scene.</context_summary>
                <scene_break_reason>Initial scene.</scene_break_reason>
                <start_anchor><![CDATA[Once upon a time]]></start_anchor>
                <end_anchor><![CDATA[the end.]]></end_anchor>
              </scene>
            </scenes>
            ```
            """;

        List<SceneDetectionResult> results = 
            xmlParser.parseResponse(xmlWithGenericMarkdown, 1000);

        assertThat(results).hasSize(1);
        SceneDetectionResult scene = results.get(0);
        assertThat(scene.sceneIndex()).isEqualTo(1);
    }

    @Test
    void parseSceneDetectionResponse_ShouldSkipIncompleteScenes() {
        String xmlWithIncompleteScene = """
            <scenes>
              <scene>
                <scene_index>1</scene_index>
                <context_summary>Complete scene.</context_summary>
                <scene_break_reason>Initial scene.</scene_break_reason>
                <start_anchor><![CDATA[Complete start]]></start_anchor>
                <end_anchor><![CDATA[Complete end.]]></end_anchor>
              </scene>
              <scene>
                <scene_index>2</scene_index>
                <context_summary>Incomplete scene missing end anchor.</context_summary>
                <scene_break_reason>Location change.</scene_break_reason>
                <start_anchor><![CDATA[Incomplete start]]></start_anchor>
              </scene>
              <scene>
                <scene_index>3</scene_index>
                <context_summary>Another complete scene.</context_summary>
                <scene_break_reason>Time jump.</scene_break_reason>
                <start_anchor><![CDATA[Another start]]></start_anchor>
                <end_anchor><![CDATA[Another end.]]></end_anchor>
              </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = 
            xmlParser.parseResponse(xmlWithIncompleteScene, 1000);

        // Should only return the 2 complete scenes, skipping the incomplete one
        assertThat(results).hasSize(2);
        assertThat(results.get(0).sceneIndex()).isEqualTo(1);
        assertThat(results.get(1).sceneIndex()).isEqualTo(3);
    }

    @Test
    void parseSceneDetectionResponse_ShouldHandleCDataSections() {
        String xmlWithCData = """
            <scenes>
              <scene>
                <scene_index>1</scene_index>
                <context_summary><![CDATA[A scene with "quotes" and <special> characters.]]></context_summary>
                <scene_break_reason><![CDATA[Initial scene with complex dialogue.]]></scene_break_reason>
                <start_anchor><![CDATA["Hello," she said]]></start_anchor>
                <end_anchor><![CDATA[he replied, "Goodbye."]]></end_anchor>
              </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = 
            xmlParser.parseResponse(xmlWithCData, 1000);

        assertThat(results).hasSize(1);
        SceneDetectionResult scene = results.get(0);
        assertThat(scene.contextSummary()).isEqualTo("A scene with \"quotes\" and <special> characters.");
        assertThat(scene.startAnchor()).isEqualTo("\"Hello,\" she said");
        assertThat(scene.endAnchor()).isEqualTo("he replied, \"Goodbye.\"");
        assertThat(scene.sceneBreakReason()).isEqualTo("Initial scene with complex dialogue.");
    }

    @Test
    void parseSceneDetectionResponse_ShouldReturnEmptyListForInvalidXml() {
        String invalidXml = "This is not XML at all!";

        List<SceneDetectionResult> results = 
            xmlParser.parseResponse(invalidXml, 1000);

        assertThat(results).isEmpty();
    }

    @Test
    void parseSceneDetectionResponse_ShouldReturnEmptyListForEmptyInput() {
        List<SceneDetectionResult> results = 
            xmlParser.parseResponse("", 1000);

        assertThat(results).isEmpty();
    }

    @Test
    void parseSceneDetectionResponse_ShouldHandleWhitespaceInElements() {
        String xmlWithWhitespace = """
            <scenes>
              <scene>
                <scene_index>  1  </scene_index>
                <context_summary>  A scene with whitespace.  </context_summary>
                <scene_break_reason>  Initial scene.  </scene_break_reason>
                <start_anchor><![CDATA[Start with spaces]]></start_anchor>
                <end_anchor><![CDATA[End with spaces]]></end_anchor>
              </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = 
            xmlParser.parseResponse(xmlWithWhitespace, 1000);

        assertThat(results).hasSize(1);
        SceneDetectionResult scene = results.get(0);
        assertThat(scene.sceneIndex()).isEqualTo(1);
        assertThat(scene.contextSummary()).isEqualTo("A scene with whitespace.");
        assertThat(scene.startAnchor()).isEqualTo("Start with spaces");
        assertThat(scene.endAnchor()).isEqualTo("End with spaces");
        assertThat(scene.sceneBreakReason()).isEqualTo("Initial scene.");
    }
}
