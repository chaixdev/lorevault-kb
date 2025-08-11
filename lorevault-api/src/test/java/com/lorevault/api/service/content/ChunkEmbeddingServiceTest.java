package com.lorevault.api.service.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ChunkEmbeddingServiceTest {

    // Embedding tests disabled (embeddings deferred to v0.5.0)
    @Test
    void embeddingsDeferredPlaceholder() {
        assertThat(true).isTrue();
    }
}
