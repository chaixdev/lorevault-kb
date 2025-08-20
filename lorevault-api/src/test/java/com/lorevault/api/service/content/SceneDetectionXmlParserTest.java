package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneDetectionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("service")
@DisplayName("SceneDetectionXmlParser")
class SceneDetectionXmlParserTest {

    private final SceneDetectionXmlParser parser = new SceneDetectionXmlParser();

    @Test
    @DisplayName("should parse valid XML with two scenes")
    void shouldParseValidXmlWithTwoScenes() {
        String validXml = """
            <scenes>
                <scene index="1">
                    <start_anchor>The journey began at dawn</start_anchor>
                    <context_summary>The protagonist starts their adventure</context_summary>
                    <break_reason>Opening scene</break_reason>
                    <chronology>R:temporal.start</chronology>
                    <chronology_certainty>Explicit</chronology_certainty>
                    <chronology_marker>Chapter beginning</chronology_marker>
                </scene>
                <scene index="2">
                    <start_anchor>Three hours later, they reached</start_anchor>
                    <context_summary>The group arrives at the first destination</context_summary>
                    <break_reason>Location change</break_reason>
                    <chronology>R:temporal.continues</chronology>
                    <chronology_certainty>Heuristic</chronology_certainty>
                    <chronology_marker>Temporal progression</chronology_marker>
                </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = parser.parseResponse(validXml, 1000);

        assertThat(results).hasSize(2);
        
        SceneDetectionResult scene1 = results.get(0);
        assertThat(scene1.sceneIndex()).isEqualTo(1);
        assertThat(scene1.startAnchor()).isEqualTo("The journey began at dawn");
        assertThat(scene1.contextSummary()).isEqualTo("The protagonist starts their adventure");
        assertThat(scene1.breakReason()).isEqualTo("Opening scene");
        assertThat(scene1.chronology()).isEqualTo("R:temporal.start");
        assertThat(scene1.chronologyCertainty()).isEqualTo("Explicit");
        assertThat(scene1.chronologyMarker()).isEqualTo("Chapter beginning");
        
        SceneDetectionResult scene2 = results.get(1);
        assertThat(scene2.sceneIndex()).isEqualTo(2);
        assertThat(scene2.startAnchor()).isEqualTo("Three hours later, they reached");
        assertThat(scene2.contextSummary()).isEqualTo("The group arrives at the first destination");
        assertThat(scene2.breakReason()).isEqualTo("Location change");
        assertThat(scene2.chronology()).isEqualTo("R:temporal.continues");
        assertThat(scene2.chronologyCertainty()).isEqualTo("Heuristic");
        assertThat(scene2.chronologyMarker()).isEqualTo("Temporal progression");
    }

    @Test
    @DisplayName("should handle XML with CDATA sections")
    void shouldHandleXmlWithCdataSections() {
        String xmlWithCdata = """
            <scenes>
                <scene index="1">
                    <start_anchor><![CDATA["Hello," she said, looking up.]]></start_anchor>
                    <context_summary><![CDATA[Character dialogue & description]]></context_summary>
                    <break_reason>Dialogue scene</break_reason>
                    <chronology>R:temporal.meets</chronology>
                    <chronology_certainty>Explicit</chronology_certainty>
                    <chronology_marker>Direct speech</chronology_marker>
                </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = parser.parseResponse(xmlWithCdata, 500);

        assertThat(results).hasSize(1);
        SceneDetectionResult scene = results.get(0);
        assertThat(scene.startAnchor()).isEqualTo("\"Hello,\" she said, looking up.");
        assertThat(scene.contextSummary()).isEqualTo("Character dialogue & description");
    }

    @Test
    @DisplayName("should handle markdown fencing around XML")
    void shouldHandleMarkdownFencing() {
        String fencedXml = """
            ```xml
            <scenes>
                <scene index="1">
                    <start_anchor>Walking through the forest</start_anchor>
                    <context_summary>Nature scene description</context_summary>
                    <break_reason>Setting establishment</break_reason>
                    <chronology>R:temporal.continues</chronology>
                    <chronology_certainty>Heuristic</chronology_certainty>
                    <chronology_marker>Environmental cue</chronology_marker>
                </scene>
            </scenes>
            ```
            """;

        List<SceneDetectionResult> results = parser.parseResponse(fencedXml, 300);

        assertThat(results).hasSize(1);
        SceneDetectionResult scene = results.get(0);
        assertThat(scene.startAnchor()).isEqualTo("Walking through the forest");
        assertThat(scene.contextSummary()).isEqualTo("Nature scene description");
    }

    @Test
    @DisplayName("should return empty list for malformed XML")
    void shouldReturnEmptyListForMalformedXml() {
        String malformedXml = "<scenes><scene index=\"1\"><start_anchor>Unclosed tag";

        List<SceneDetectionResult> results = parser.parseResponse(malformedXml, 200);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should skip scenes with missing required fields")
    void shouldSkipScenesWithMissingRequiredFields() {
        String incompleteXml = """
            <scenes>
                <scene index="1">
                    <start_anchor>Valid scene anchor</start_anchor>
                    <context_summary>Valid scene summary</context_summary>
                    <break_reason>Valid reason</break_reason>
                </scene>
                <scene index="2">
                    <!-- Missing start_anchor -->
                    <context_summary>Invalid scene - no anchor</context_summary>
                    <break_reason>Should be skipped</break_reason>
                </scene>
                <scene index="3">
                    <start_anchor>Another valid scene</start_anchor>
                    <!-- Missing context_summary -->
                    <break_reason>Should be skipped</break_reason>
                </scene>
                <scene index="4">
                    <start_anchor>Final valid scene</start_anchor>
                    <context_summary>This should be included</context_summary>
                    <break_reason>Complete scene</break_reason>
                </scene>
            </scenes>
            """;

        List<SceneDetectionResult> results = parser.parseResponse(incompleteXml, 800);

        // Only scenes 1 and 4 should be included (they have both required fields)
        assertThat(results).hasSize(2);
        assertThat(results.get(0).sceneIndex()).isEqualTo(1);
        assertThat(results.get(0).startAnchor()).isEqualTo("Valid scene anchor");
        assertThat(results.get(1).sceneIndex()).isEqualTo(4);
        assertThat(results.get(1).startAnchor()).isEqualTo("Final valid scene");
    }

    @Test
    @DisplayName("should handle empty XML response")
    void shouldHandleEmptyXmlResponse() {
        List<SceneDetectionResult> results = parser.parseResponse("", 100);
        assertThat(results).isEmpty();

        results = parser.parseResponse("   ", 100);
        assertThat(results).isEmpty();

        results = parser.parseResponse(null, 100);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should handle non-XML content gracefully")
    void shouldHandleNonXmlContentGracefully() {
        String nonXml = "This is just plain text without any XML structure.";

        List<SceneDetectionResult> results = parser.parseResponse(nonXml, 200);

        assertThat(results).isEmpty();
    }
}
