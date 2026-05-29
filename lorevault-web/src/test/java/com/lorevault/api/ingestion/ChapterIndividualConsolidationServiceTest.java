package com.lorevault.api.ingestion;

import com.lorevault.api.content.association.ChapterIndividual;
import com.lorevault.api.content.association.ChapterIndividualCandidate;
import com.lorevault.api.content.association.ChapterIndividualGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualConsolidationResult;
import java.util.List;
import java.util.UUID;

import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualConsolidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterIndividualConsolidationService")
class ChapterIndividualConsolidationServiceTest {

    @Mock
    private ChapterIndividualGraphRepository chapterIndividualRepository;

    private static final StageExecutionContext CTX = new StageExecutionContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION);

    @InjectMocks
    private ChapterIndividualConsolidationService service;

    @Test
    @DisplayName("Rebuilds one ChapterIndividual per normalized name and relinks mentions")
    void rebuildsChapterIndividualsFromCandidates() {
        UUID chapterId = UUID.randomUUID();
        ChapterIndividualCandidate nyx = candidate("Nyx", "nyx", 2L);
        ChapterIndividualCandidate orion = candidate("Orion", "orion", 1L);

        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(3L);
        when(chapterIndividualRepository.findResolutionCandidates(chapterId)).thenReturn(List.of(nyx, orion));
        when(chapterIndividualRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterIndividualRepository.countChapterIndividualsByChapterId(chapterId)).thenReturn(2L);

        ChapterIndividualConsolidationResult response = service.consolidateChapter(CTX, chapterId);

        assertThat(response.success()).isTrue();
        assertThat(response.rawIndividualsProcessed()).isEqualTo(3);
        assertThat(response.chapterIndividualsCreated()).isEqualTo(2);

        verify(chapterIndividualRepository).deleteByChapterId(chapterId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterIndividual>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterIndividualRepository).saveAll(savedCaptor.capture());
        List<ChapterIndividual> saved = toList(savedCaptor.getValue());
        assertThat(saved)
                .hasSize(2)
                .extracting(ChapterIndividual::displayName, ChapterIndividual::normalizedName, ChapterIndividual::mentionCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Nyx", "nyx", 2),
                        org.assertj.core.groups.Tuple.tuple("Orion", "orion", 1)
                );

        for (ChapterIndividual chapterIndividual : saved) {
            verify(chapterIndividualRepository).linkChapterToIndividual(chapterId, chapterIndividual.id());
            verify(chapterIndividualRepository).linkMentionsToChapterIndividual(
                    chapterId,
                    chapterIndividual.normalizedName(),
                    chapterIndividual.id(),
                    ChapterIndividualConsolidationService.CHAPTER_CONSOLIDATED
            );
        }
    }

    @Test
    @DisplayName("Skips save when there are no candidates")
    void skipsSaveWhenNoCandidates() {
        UUID chapterId = UUID.randomUUID();
        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(chapterIndividualRepository.findResolutionCandidates(chapterId)).thenReturn(List.of());

        ChapterIndividualConsolidationResult response = service.consolidateChapter(CTX, chapterId);

        assertThat(response.success()).isFalse();
        assertThat(response.rawIndividualsProcessed()).isEqualTo(2);
        assertThat(response.chapterIndividualsCreated()).isZero();

        verify(chapterIndividualRepository).deleteByChapterId(chapterId);
        verify(chapterIndividualRepository, never()).saveAll(any());
        verify(chapterIndividualRepository, never()).linkChapterToIndividual(any(), any());
        verify(chapterIndividualRepository, never()).linkMentionsToChapterIndividual(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ignores blank normalized names from candidates")
    void ignoresBlankNormalizedNames() {
        UUID chapterId = UUID.randomUUID();
        ChapterIndividualCandidate blank = candidate("Narrator", "   ", 2L);
        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(chapterIndividualRepository.findResolutionCandidates(chapterId)).thenReturn(List.of(blank));

        ChapterIndividualConsolidationResult response = service.consolidateChapter(CTX, chapterId);

        assertThat(response.success()).isFalse();
        assertThat(response.rawIndividualsProcessed()).isEqualTo(2);
        assertThat(response.chapterIndividualsCreated()).isZero();

        verify(chapterIndividualRepository).deleteByChapterId(chapterId);
        verify(chapterIndividualRepository, never()).saveAll(any());
        verify(chapterIndividualRepository, never()).linkChapterToIndividual(any(), any());
        verify(chapterIndividualRepository, never()).linkMentionsToChapterIndividual(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Returns no-op response when chapter has no mentions")
    void returnsNoOpWhenChapterHasNoMentions() {
        UUID chapterId = UUID.randomUUID();
        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(0L);

        ChapterIndividualConsolidationResult response = service.consolidateChapter(CTX, chapterId);

        assertThat(response.success()).isFalse();
        assertThat(response.rawIndividualsProcessed()).isZero();
        assertThat(response.chapterIndividualsCreated()).isZero();
        verify(chapterIndividualRepository, never()).deleteByChapterId(any());
    }

    private ChapterIndividualCandidate candidate(
            String displayName,
            String normalizedName,
            Long mentionCount
    ) {
        return new ChapterIndividualCandidate(displayName, normalizedName, mentionCount != null ? mentionCount.intValue() : null);
    }

    private List<ChapterIndividual> toList(Iterable<ChapterIndividual> iterable) {
        return iterable == null ? List.of() : org.assertj.core.util.Lists.newArrayList(iterable);
    }
}
