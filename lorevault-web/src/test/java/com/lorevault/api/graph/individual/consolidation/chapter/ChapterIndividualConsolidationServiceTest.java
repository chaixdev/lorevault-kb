package com.lorevault.api.graph.individual.consolidation.chapter;

import com.lorevault.api.graph.individual.persistence.ChapterIndividual;
import com.lorevault.api.graph.individual.persistence.ChapterIndividualGraphRepository;
import com.lorevault.api.graph.individual.persistence.IndividualMention;
import com.lorevault.api.graph.individual.persistence.IndividualMentionGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterIndividualConsolidationService")
class ChapterIndividualConsolidationServiceTest {

    @Mock
    private ChapterIndividualGraphRepository chapterIndividualRepository;

    @Mock
    private IndividualMentionGraphRepository individualMentionRepository;

    @Spy
    private ConsolidationEngine consolidationEngine = new ConsolidationEngine();

    private static final StageExecutionContext CTX = new StageExecutionContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION);

    @InjectMocks
    private ChapterIndividualConsolidationService service;

    private static IndividualMention mention(
            UUID id, String displayName, String normalizedName, List<String> aliases,
            UUID chapterId, Integer extractionIndex) {
        return new IndividualMention(
                id, "test-source", displayName, normalizedName, aliases,
                null, null, null, null, null,
                chapterId, null, null, extractionIndex, null, null);
    }

    @Test
    @DisplayName("Rebuilds one ChapterIndividual per cluster and links mentions by ID")
    void rebuildsChapterIndividualsFromClusters() {
        UUID chapterId = UUID.randomUUID();
        UUID nyx1Id = UUID.randomUUID();
        UUID nyx2Id = UUID.randomUUID();
        UUID orionId = UUID.randomUUID();

        IndividualMention nyx1 = mention(nyx1Id, "Nyx", "nyx", List.of(), chapterId, 0);
        IndividualMention nyx2 = mention(nyx2Id, "Nyx", "nyx", List.of(), chapterId, 1);
        IndividualMention orion = mention(orionId, "Orion", "orion", List.of(), chapterId, 0);

        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(3L);
        when(individualMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(nyx1, nyx2, orion));
        when(chapterIndividualRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterIndividualRepository.countChapterIndividualsByChapterId(chapterId)).thenReturn(2L);

        ChapterIndividualConsolidationResult result = service.consolidateChapter(CTX, chapterId);

        assertThat(result.success()).isTrue();
        assertThat(result.rawIndividualsProcessed()).isEqualTo(3);
        assertThat(result.chapterIndividualsCreated()).isEqualTo(2);

        verify(chapterIndividualRepository).deleteByChapterId(chapterId);
        verify(individualMentionRepository).findByChapterId(chapterId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterIndividual>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterIndividualRepository).saveAll(savedCaptor.capture());
        List<ChapterIndividual> saved = new ArrayList<>();
        savedCaptor.getValue().forEach(saved::add);

        assertThat(saved).hasSize(2);
        // After sorting by normalizedName: "nyx" before "orion"
        assertThat(saved)
                .extracting(ChapterIndividual::displayName, ChapterIndividual::normalizedName, ChapterIndividual::mentionCount)
                .containsExactly(
                        tuple("Nyx", "nyx", 2),
                        tuple("Orion", "orion", 1)
                );

        // Nyx cluster (first saved individual)
        verify(chapterIndividualRepository).linkChapterToIndividual(chapterId, saved.get(0).id());
        verify(chapterIndividualRepository).linkMentionToChapterIndividual(
                nyx1Id, saved.get(0).id(), ChapterIndividualConsolidationService.CHAPTER_CONSOLIDATED);
        verify(chapterIndividualRepository).linkMentionToChapterIndividual(
                nyx2Id, saved.get(0).id(), ChapterIndividualConsolidationService.CHAPTER_CONSOLIDATED);

        // Orion cluster (second saved individual)
        verify(chapterIndividualRepository).linkChapterToIndividual(chapterId, saved.get(1).id());
        verify(chapterIndividualRepository).linkMentionToChapterIndividual(
                orionId, saved.get(1).id(), ChapterIndividualConsolidationService.CHAPTER_CONSOLIDATED);
    }

    @Test
    @DisplayName("Skips save when no resolvable mentions")
    void skipsSaveWhenNoResolvableMentions() {
        UUID chapterId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();

        // Mention with blank normalizedName and null aliases → NameKeys.from() returns empty
        IndividualMention unresolvable = mention(id1, "Narrator", "", null, chapterId, 0);

        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(individualMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(unresolvable));

        ChapterIndividualConsolidationResult result = service.consolidateChapter(CTX, chapterId);

        assertThat(result.success()).isFalse();
        assertThat(result.rawIndividualsProcessed()).isEqualTo(2);
        assertThat(result.chapterIndividualsCreated()).isZero();

        verify(chapterIndividualRepository).deleteByChapterId(chapterId);
        verify(chapterIndividualRepository, never()).saveAll(any());
        verify(chapterIndividualRepository, never()).linkChapterToIndividual(any(), any());
        verify(chapterIndividualRepository, never()).linkMentionToChapterIndividual(any(), any(), any());
    }

    @Test
    @DisplayName("Ignores blank normalized names")
    void ignoresBlankNormalizedNames() {
        UUID chapterId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();

        // Mention with blank (whitespace-only) normalizedName and null aliases → filtered out
        IndividualMention blank = mention(id1, "Narrator", "   ", null, chapterId, 0);

        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(individualMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(blank));

        ChapterIndividualConsolidationResult result = service.consolidateChapter(CTX, chapterId);

        assertThat(result.success()).isFalse();
        assertThat(result.rawIndividualsProcessed()).isEqualTo(2);
        assertThat(result.chapterIndividualsCreated()).isZero();

        verify(chapterIndividualRepository).deleteByChapterId(chapterId);
        verify(chapterIndividualRepository, never()).saveAll(any());
        verify(chapterIndividualRepository, never()).linkChapterToIndividual(any(), any());
        verify(chapterIndividualRepository, never()).linkMentionToChapterIndividual(any(), any(), any());
    }

    @Test
    @DisplayName("Returns no-op when chapter has no mentions")
    void returnsNoOpWhenChapterHasNoMentions() {
        UUID chapterId = UUID.randomUUID();

        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(0L);

        ChapterIndividualConsolidationResult result = service.consolidateChapter(CTX, chapterId);

        assertThat(result.success()).isTrue();
        assertThat(result.rawIndividualsProcessed()).isZero();
        assertThat(result.chapterIndividualsCreated()).isZero();

        verify(chapterIndividualRepository).deleteByChapterId(chapterId);
        verify(chapterIndividualRepository, never()).saveAll(any());
        verify(chapterIndividualRepository, never()).linkChapterToIndividual(any(), any());
        verify(chapterIndividualRepository, never()).linkMentionToChapterIndividual(any(), any(), any());
        verifyNoInteractions(individualMentionRepository);
    }
}
