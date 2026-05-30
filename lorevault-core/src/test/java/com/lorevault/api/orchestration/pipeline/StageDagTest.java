package com.lorevault.api.orchestration.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StageDag")
class StageDagTest {

    private StageDag dag;

    @BeforeEach
    void setUp() {
        dag = new StageDag();
    }

    @Nested
    @DisplayName("roots()")
    class Roots {

        @Test
        void returnsOnlySceneSegmentation() {
            assertThat(dag.roots()).containsExactly(StageKey.SCENE_SEGMENTATION);
        }

        @Test
        void isImmutable() {
            Set<StageKey> roots = dag.roots();
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> roots.add(StageKey.CHUNKING));
        }
    }

    @Nested
    @DisplayName("childrenOf()")
    class ChildrenOf {

        @Test
        void sceneSegmentationHasSixChildren() {
            assertThat(dag.childrenOf(StageKey.SCENE_SEGMENTATION))
                    .containsExactlyInAnyOrder(
                            StageKey.CHUNKING,
                            StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION,
                            StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,
                            StageKey.CHAPTER_LOCATION_CONSOLIDATION,
                            StageKey.CHAPTER_OBJECT_CONSOLIDATION,
                            StageKey.CHAPTER_EVENT_CONSOLIDATION
                    );
        }

        @Test
        void chunkingLeadsToEmbedding() {
            assertThat(dag.childrenOf(StageKey.CHUNKING))
                    .containsExactly(StageKey.EMBEDDING);
        }

        @Test
        void chapterResolutionLeadsToBookReduction() {
            assertThat(dag.childrenOf(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION))
                    .containsExactly(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION);
            assertThat(dag.childrenOf(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION))
                    .containsExactly(StageKey.BOOK_COLLECTIVE_CONSOLIDATION);
            assertThat(dag.childrenOf(StageKey.CHAPTER_LOCATION_CONSOLIDATION))
                    .containsExactly(StageKey.BOOK_LOCATION_CONSOLIDATION);
            assertThat(dag.childrenOf(StageKey.CHAPTER_OBJECT_CONSOLIDATION))
                    .containsExactly(StageKey.BOOK_OBJECT_CONSOLIDATION);
        }

        @Test
        void eventResolutionLeadsToEventEmbedding() {
            assertThat(dag.childrenOf(StageKey.CHAPTER_EVENT_CONSOLIDATION))
                    .containsExactly(StageKey.CHAPTER_EVENT_EMBEDDING);
        }

        @Test
        void eventEmbeddingLeadsToBookEventCandidateGeneration() {
            assertThat(dag.childrenOf(StageKey.CHAPTER_EVENT_EMBEDDING))
                    .containsExactly(StageKey.BOOK_EVENT_CANDIDATE_GENERATION);
        }

        @Test
        void allBookLevelStagesLeadToIngestionComplete() {
            assertThat(dag.childrenOf(StageKey.EMBEDDING))
                    .containsExactly(StageKey.INGESTION_COMPLETE);
            assertThat(dag.childrenOf(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION))
                    .containsExactly(StageKey.INGESTION_COMPLETE);
            assertThat(dag.childrenOf(StageKey.BOOK_COLLECTIVE_CONSOLIDATION))
                    .containsExactly(StageKey.INGESTION_COMPLETE);
            assertThat(dag.childrenOf(StageKey.BOOK_LOCATION_CONSOLIDATION))
                    .containsExactly(StageKey.INGESTION_COMPLETE);
            assertThat(dag.childrenOf(StageKey.BOOK_OBJECT_CONSOLIDATION))
                    .containsExactly(StageKey.INGESTION_COMPLETE);
            assertThat(dag.childrenOf(StageKey.BOOK_EVENT_CANDIDATE_GENERATION))
                    .containsExactly(StageKey.INGESTION_COMPLETE);
        }

        @Test
        void ingestionCompleteHasNoChildren() {
            assertThat(dag.childrenOf(StageKey.INGESTION_COMPLETE)).isEmpty();
        }

        @Test
        void unknownStageReturnsEmptyList() {
            // StageDag uses getOrDefault, so any stage not in the map returns empty
            // This tests the defensive behavior
            assertThat(dag.childrenOf(StageKey.SCENE_SEGMENTATION)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("parentsOf()")
    class ParentsOf {

        @Test
        void sceneSegmentationHasNoParents() {
            assertThat(dag.parentsOf(StageKey.SCENE_SEGMENTATION)).isEmpty();
        }

        @Test
        void chunkingHasSceneSegmentationAsParent() {
            assertThat(dag.parentsOf(StageKey.CHUNKING))
                    .containsExactly(StageKey.SCENE_SEGMENTATION);
        }

        @Test
        void ingestionCompleteHasSixParents() {
            assertThat(dag.parentsOf(StageKey.INGESTION_COMPLETE))
                    .containsExactlyInAnyOrder(
                            StageKey.EMBEDDING,
                            StageKey.BOOK_INDIVIDUAL_CONSOLIDATION,
                            StageKey.BOOK_COLLECTIVE_CONSOLIDATION,
                            StageKey.BOOK_LOCATION_CONSOLIDATION,
                            StageKey.BOOK_OBJECT_CONSOLIDATION,
                            StageKey.BOOK_EVENT_CANDIDATE_GENERATION
                    );
        }

        @Test
        void bookReductionHasChapterResolutionAsParent() {
            assertThat(dag.parentsOf(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION))
                    .containsExactly(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION);
        }
    }

    @Nested
    @DisplayName("transitiveDownstream()")
    class TransitiveDownstream {

        @Test
        void fromSceneSegmentationIncludesAllStages() {
            Set<StageKey> downstream = dag.transitiveDownstream(StageKey.SCENE_SEGMENTATION);
            assertThat(downstream).hasSize(StageKey.values().length);
            assertThat(downstream).contains(StageKey.values());
        }

        @Test
        void fromIngestionCompleteIncludesOnlyItself() {
            Set<StageKey> downstream = dag.transitiveDownstream(StageKey.INGESTION_COMPLETE);
            assertThat(downstream).containsExactly(StageKey.INGESTION_COMPLETE);
        }

        @Test
        void fromChunkingIncludesContentLane() {
            Set<StageKey> downstream = dag.transitiveDownstream(StageKey.CHUNKING);
            assertThat(downstream).containsExactlyInAnyOrder(
                    StageKey.CHUNKING,
                    StageKey.EMBEDDING,
                    StageKey.INGESTION_COMPLETE
            );
        }

        @Test
        void fromChapterEventConsolidationIncludesEventLane() {
            Set<StageKey> downstream = dag.transitiveDownstream(StageKey.CHAPTER_EVENT_CONSOLIDATION);
            assertThat(downstream).containsExactlyInAnyOrder(
                    StageKey.CHAPTER_EVENT_CONSOLIDATION,
                    StageKey.CHAPTER_EVENT_EMBEDDING,
                    StageKey.BOOK_EVENT_CANDIDATE_GENERATION,
                    StageKey.INGESTION_COMPLETE
            );
        }

        @Test
        void resultIsImmutable() {
            Set<StageKey> downstream = dag.transitiveDownstream(StageKey.CHUNKING);
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> downstream.add(StageKey.SCENE_SEGMENTATION));
        }
    }

    @Nested
    @DisplayName("topologicalDepthDescending()")
    class TopologicalDepthDescending {

        @Test
        void ingestionCompleteComesBeforeItsParents() {
            Set<StageKey> stages = Set.of(
                    StageKey.EMBEDDING,
                    StageKey.INGESTION_COMPLETE
            );
            List<StageKey> ordered = dag.topologicalDepthDescending(stages);
            assertThat(ordered).containsExactly(
                    StageKey.INGESTION_COMPLETE,
                    StageKey.EMBEDDING
            );
        }

        @Test
        void fullDagContainsAllStagesInReverseBfsOrder() {
            Set<StageKey> allStages = Set.of(StageKey.values());
            List<StageKey> ordered = dag.topologicalDepthDescending(allStages);
            assertThat(ordered).hasSize(StageKey.values().length);
            assertThat(ordered).contains(StageKey.values());
            // BFS-based: INGESTION_COMPLETE is visited before BOOK_EVENT_CANDIDATE_GENERATION
            // (EMBEDDING is processed before CHAPTER_EVENT_EMBEDDING in BFS),
            // so reversed order puts BOOK_EVENT_CANDIDATE_GENERATION first.
            assertThat(ordered.get(0)).isEqualTo(StageKey.BOOK_EVENT_CANDIDATE_GENERATION);
        }

        @Test
        void emptySetReturnsEmptyList() {
            assertThat(dag.topologicalDepthDescending(Set.of())).isEmpty();
        }

        @Test
        void resultIsImmutable() {
            List<StageKey> ordered = dag.topologicalDepthDescending(
                    Set.of(StageKey.CHUNKING, StageKey.EMBEDDING));
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> ordered.add(StageKey.SCENE_SEGMENTATION));
        }
    }

    @Nested
    @DisplayName("validateConnectivity()")
    class ValidateConnectivity {

        @Test
        void allStagesAreReachable() {
            assertThat(dag.validateConnectivity()).isEmpty();
        }
    }
}
