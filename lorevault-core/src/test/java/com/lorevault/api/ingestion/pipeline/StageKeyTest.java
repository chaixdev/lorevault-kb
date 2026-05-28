package com.lorevault.api.ingestion.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StageKey")
class StageKeyTest {

    @Nested
    @DisplayName("isChapterStage()")
    class IsChapterStage {

        @Test
        void sceneSegmentationIsChapterStage() {
            assertThat(StageKey.SCENE_SEGMENTATION.isChapterStage()).isTrue();
        }

        @Test
        void chunkingIsChapterStage() {
            assertThat(StageKey.CHUNKING.isChapterStage()).isTrue();
        }

        @Test
        void embeddingIsChapterStage() {
            assertThat(StageKey.EMBEDDING.isChapterStage()).isTrue();
        }

        @Test
        void chapterResolutionsAreChapterStages() {
            assertThat(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION.isChapterStage()).isTrue();
            assertThat(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION.isChapterStage()).isTrue();
            assertThat(StageKey.CHAPTER_LOCATION_CONSOLIDATION.isChapterStage()).isTrue();
            assertThat(StageKey.CHAPTER_OBJECT_CONSOLIDATION.isChapterStage()).isTrue();
            assertThat(StageKey.CHAPTER_EVENT_CONSOLIDATION.isChapterStage()).isTrue();
        }

        @Test
        void chapterEventEmbeddingIsChapterStage() {
            assertThat(StageKey.CHAPTER_EVENT_EMBEDDING.isChapterStage()).isTrue();
        }

        @Test
        void bookReductionsAreNotChapterStages() {
            assertThat(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION.isChapterStage()).isFalse();
            assertThat(StageKey.BOOK_COLLECTIVE_CONSOLIDATION.isChapterStage()).isFalse();
            assertThat(StageKey.BOOK_LOCATION_CONSOLIDATION.isChapterStage()).isFalse();
            assertThat(StageKey.BOOK_OBJECT_CONSOLIDATION.isChapterStage()).isFalse();
        }

        @Test
        void bookEventCandidateGenerationIsNotChapterStage() {
            assertThat(StageKey.BOOK_EVENT_CANDIDATE_GENERATION.isChapterStage()).isFalse();
        }

        @Test
        void ingestionCompleteIsNotChapterStage() {
            assertThat(StageKey.INGESTION_COMPLETE.isChapterStage()).isFalse();
        }
    }

    @Nested
    @DisplayName("isBookLevel()")
    class IsBookLevel {

        @Test
        void bookReductionsAreBookLevel() {
            assertThat(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION.isBookLevel()).isTrue();
            assertThat(StageKey.BOOK_COLLECTIVE_CONSOLIDATION.isBookLevel()).isTrue();
            assertThat(StageKey.BOOK_LOCATION_CONSOLIDATION.isBookLevel()).isTrue();
            assertThat(StageKey.BOOK_OBJECT_CONSOLIDATION.isBookLevel()).isTrue();
        }

        @Test
        void bookEventCandidateGenerationIsBookLevel() {
            assertThat(StageKey.BOOK_EVENT_CANDIDATE_GENERATION.isBookLevel()).isTrue();
        }

        @Test
        void chapterStagesAreNotBookLevel() {
            assertThat(StageKey.SCENE_SEGMENTATION.isBookLevel()).isFalse();
            assertThat(StageKey.CHUNKING.isBookLevel()).isFalse();
            assertThat(StageKey.EMBEDDING.isBookLevel()).isFalse();
            assertThat(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION.isBookLevel()).isFalse();
            assertThat(StageKey.CHAPTER_EVENT_EMBEDDING.isBookLevel()).isFalse();
        }

        @Test
        void ingestionCompleteIsNotBookLevel() {
            assertThat(StageKey.INGESTION_COMPLETE.isBookLevel()).isFalse();
        }
    }

    @Nested
    @DisplayName("classification coverage")
    class ClassificationCoverage {

        @ParameterizedTest
        @EnumSource(StageKey.class)
        void everyStageIsEitherChapterOrBookOrTerminal(StageKey stage) {
            if (stage == StageKey.INGESTION_COMPLETE) {
                assertThat(stage.isChapterStage()).isFalse();
                assertThat(stage.isBookLevel()).isFalse();
            } else {
                assertThat(stage.isChapterStage() || stage.isBookLevel())
                        .as("Stage %s should be classified as chapter or book level", stage)
                        .isTrue();
            }
        }

        @ParameterizedTest
        @EnumSource(StageKey.class)
        void noStageIsBothChapterAndBook(StageKey stage) {
            assertThat(stage.isChapterStage() && stage.isBookLevel())
                    .as("Stage %s should not be both chapter and book level", stage)
                    .isFalse();
        }
    }
}
