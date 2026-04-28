package com.lorevault.api.ingestion.application.eventembedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BookEventAnnProperties")
class BookEventAnnPropertiesTest {

    @Test
    @DisplayName("Accepts valid ANN threshold configuration")
    void acceptsValidConfiguration() {
        BookEventAnnProperties properties = new BookEventAnnProperties(8, 3, 0.82, 3, 2560);

        assertThat(properties.topK()).isEqualTo(8);
        assertThat(properties.oversampleFactor()).isEqualTo(3);
        assertThat(properties.annFloor()).isEqualTo(0.82);
        assertThat(properties.maxCandidatesPerEvent()).isEqualTo(3);
        assertThat(properties.embeddingDimension()).isEqualTo(2560);
    }

    @Test
    @DisplayName("Rejects invalid thresholds")
    void rejectsInvalidThresholds() {
        assertThatThrownBy(() -> new BookEventAnnProperties(8, 3, -0.1, 3, 2560))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("annFloor");
        assertThatThrownBy(() -> new BookEventAnnProperties(8, 3, 0.82, 0, 2560))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxCandidatesPerEvent");
        assertThatThrownBy(() -> new BookEventAnnProperties(8, 3, 0.82, 3, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingDimension");
    }
}
