package com.lorevault.api.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("service")
@DisplayName("TextChunkingService")
class TextChunkingServiceTest {

    @Test
    @DisplayName("should return single chunk when below threshold")
    void shouldReturnSingleChunkWhenBelowThreshold() throws Exception {
        TextChunkingService svc = new TextChunkingService();
        set(svc, "decisionThreshold", 5000);
        set(svc, "targetChunkSize", 3000);
        set(svc, "overlapPercentage", 15);
        set(svc, "minChunkSize", 2000);
        set(svc, "maxChunkSize", 4000);

        String text = "Short paragraph. Still short. End.";

        var chunks = svc.extractChunks(text);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getStartCharInChapter()).isEqualTo(0);
        assertThat(chunks.getFirst().getEndCharInChapter()).isEqualTo(text.length());
        assertThat(chunks.getFirst().getText()).isEqualTo(text.trim());
    }

    @Test
    @DisplayName("should create multiple chunks for long text with overlap")
    void shouldCreateMultipleChunksForLongText() throws Exception {
        TextChunkingService svc = new TextChunkingService();
        set(svc, "decisionThreshold", 200);
        set(svc, "targetChunkSize", 120);
        set(svc, "overlapPercentage", 15);
        set(svc, "minChunkSize", 80);
        set(svc, "maxChunkSize", 160);

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
            assertThat(c.getChunkNumberInChapter()).isEqualTo(i + 1);
            assertThat(c.getEndCharInChapter()).isGreaterThan(c.getStartCharInChapter());
            if (i > 0) {
                assertThat(c.getStartCharInChapter()).isLessThan(c.getEndCharInChapter());
                assertThat(c.getStartCharInChapter()).isLessThan(prevEnd);
            }
            prevEnd = c.getEndCharInChapter();
        }
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field f = TextChunkingService.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
