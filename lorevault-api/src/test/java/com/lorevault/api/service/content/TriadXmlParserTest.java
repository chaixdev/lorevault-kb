package com.lorevault.api.service.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("TriadXmlParser")
class TriadXmlParserTest {

    private final TriadXmlParser parser = new TriadXmlParser();

    @Test
    @DisplayName("should parse happy path triad XML")
    void shouldParseHappyPath() {
        String xml = """
            <scene_analysis>
              <timeline_marker>immediately after the interview</timeline_marker>
              <relationships>
                <previous_to_current>
                  <temporal_type>R:temporal.overlaps</temporal_type>
                  <certainty>StronglyImplied</certainty>
                  <evidence>alarm rings during interview</evidence>
                </previous_to_current>
                <current_to_next>
                  <temporal_type>R:temporal.before</temporal_type>
                  <certainty>Explicit</certainty>
                  <evidence>Months later</evidence>
                </current_to_next>
              </relationships>
            </scene_analysis>
            """;

        var result = parser.parse(xml);
        assertThat(result).isNotNull();
        assertThat(result.timelineMarker()).isEqualTo("immediately after the interview");
        assertThat(result.prevToCurr()).isNotNull();
        assertThat(result.prevToCurr().temporalType()).isEqualTo("R:temporal.overlaps");
        assertThat(result.prevToCurr().certainty()).isEqualTo("StronglyImplied");
        assertThat(result.currToNext()).isNotNull();
        assertThat(result.currToNext().temporalType()).isEqualTo("R:temporal.before");
    }

    @Test
    @DisplayName("should default on malformed XML")
    void shouldDefaultOnMalformed() {
        var result = parser.parse("<not_xml>");
        assertThat(result).isNotNull();
        assertThat(result.timelineMarker()).isNull();
        assertThat(result.prevToCurr()).isNull();
        assertThat(result.currToNext()).isNull();
    }
}
