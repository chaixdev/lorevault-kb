package com.lorevault.api.ai;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.config.LoreVaultContentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("service")
@DisplayName("TextChunkingService")
class TextChunkingServiceTest {

    @Test
    @DisplayName("should return single chunk when below threshold")
    void shouldReturnSingleChunkWhenBelowThreshold() {
        TextChunkingService svc = createService(5000, 3000, 15, 2000, 4000);

        String text = "Short paragraph. Still short. End.";

        var chunks = svc.extractChunks(text);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getStartCharInChapter()).isEqualTo(0);
        assertThat(chunks.getFirst().getEndCharInChapter()).isEqualTo(text.length());
        assertThat(chunks.getFirst().getText()).isEqualTo(text.trim());
    }

    @Test
    @DisplayName("should create multiple chunks for long text with overlap")
    void shouldCreateMultipleChunksForLongText() {
        TextChunkingService svc = createService(200, 120, 15, 80, 160);

        String sentence = "This is a sentence that ends here. ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(sentence);
        String text = sb.toString();

        var chunks = svc.extractChunks(text);
        assertThat(chunks.size()).isGreaterThan(1);
        // Verify monotonic chunk numbers and overlapping bounds
        int prevEnd = -1;
        for (int i = 0; i < chunks.size(); i++) {
            var c = chunks.get(i);
            int chunkNumber = c.getChunkNumberInChapter();
            int start = c.getStartCharInChapter();
            int end = c.getEndCharInChapter();
            assertThat(chunkNumber).isEqualTo(i + 1);
            assertThat(end).isGreaterThan(start);
            if (i > 0) {
                assertThat(start).isLessThan(end);
                assertThat(start).isLessThan(prevEnd);
            }
            prevEnd = end;
        }
    }

    private static TextChunkingService createService(
            int decisionThreshold,
            int targetSize,
            int overlapPercentage,
            int minChunkSize,
            int maxChunkSize
    ) {
        LoreVaultContentProperties.ChunkingProperties chunkingProperties =
                new LoreVaultContentProperties.ChunkingProperties(
                        decisionThreshold,
                        targetSize,
                        overlapPercentage,
                        minChunkSize,
                        maxChunkSize,
                        "sentence-aware",
                        new LoreVaultContentProperties.SentenceSplitterProperties(300, true)
                );
        LoreVaultContentProperties properties = new LoreVaultContentProperties(chunkingProperties);
        return new TextChunkingService(properties);
    }
}
