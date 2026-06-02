package com.lorevault.api.orchestration.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StageResult")
class StageResultTest {

    @Nested
    @DisplayName("success() factory")
    class SuccessFactory {

        @Test
        void withCounts() {
            Map<String, Integer> counts = Map.of("scenesDetected", 5, "edgesCreated", 12);
            StageResult result = StageResult.success(StageKey.SCENE_SEGMENTATION, "Done", counts, 1500L);

            assertThat(result.success()).isTrue();
            assertThat(result.stage()).isEqualTo(StageKey.SCENE_SEGMENTATION);
            assertThat(result.summary()).isEqualTo("Done");
            assertThat(result.counts()).containsEntry("scenesDetected", 5);
            assertThat(result.counts()).containsEntry("edgesCreated", 12);
            assertThat(result.durationMs()).isEqualTo(1500L);
            assertThat(result.retryable()).isFalse();
        }

        @Test
        void withoutCounts() {
            StageResult result = StageResult.success(StageKey.CHUNKING, "Chunked", 200L);

            assertThat(result.success()).isTrue();
            assertThat(result.stage()).isEqualTo(StageKey.CHUNKING);
            assertThat(result.summary()).isEqualTo("Chunked");
            assertThat(result.counts()).isEmpty();
            assertThat(result.durationMs()).isEqualTo(200L);
            assertThat(result.retryable()).isFalse();
        }

        @Test
        void countsAreImmutable() {
            Map<String, Integer> counts = Map.of("key", 1);
            StageResult result = StageResult.success(StageKey.EMBEDDING, "OK", counts, 100L);

            assertThatThrownBy(() -> result.counts().put("new", 2))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void nullCountsBecomesEmptyMap() {
            StageResult result = new StageResult(true, StageKey.CHUNKING, "OK", null, 50L, false);

            assertThat(result.counts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("failure() factory")
    class FailureFactory {

        @Test
        void createsNonRetryableFailure() {
            StageResult result = StageResult.failure(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, "Parse error", 300L);

            assertThat(result.success()).isFalse();
            assertThat(result.stage()).isEqualTo(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION);
            assertThat(result.summary()).isEqualTo("Parse error");
            assertThat(result.counts()).isEmpty();
            assertThat(result.durationMs()).isEqualTo(300L);
            assertThat(result.retryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("retryableFailure() factory")
    class RetryableFailureFactory {

        @Test
        void createsRetryableFailure() {
            StageResult result = StageResult.retryableFailure(StageKey.EMBEDDING, "Timeout", 5000L);

            assertThat(result.success()).isFalse();
            assertThat(result.stage()).isEqualTo(StageKey.EMBEDDING);
            assertThat(result.summary()).isEqualTo("Timeout");
            assertThat(result.counts()).isEmpty();
            assertThat(result.durationMs()).isEqualTo(5000L);
            assertThat(result.retryable()).isTrue();
        }
    }

    @Nested
    @DisplayName("record semantics")
    class RecordSemantics {

        @Test
        void equalResultsAreEqual() {
            StageResult a = StageResult.success(StageKey.CHUNKING, "OK", 100L);
            StageResult b = StageResult.success(StageKey.CHUNKING, "OK", 100L);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void differentResultsAreNotEqual() {
            StageResult a = StageResult.success(StageKey.CHUNKING, "OK", 100L);
            StageResult b = StageResult.success(StageKey.EMBEDDING, "OK", 100L);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void toStringContainsFields() {
            StageResult result = StageResult.success(StageKey.CHUNKING, "Done", 42L);

            assertThat(result.toString())
                    .contains("CHUNKING")
                    .contains("Done")
                    .contains("42");
        }
    }
}
