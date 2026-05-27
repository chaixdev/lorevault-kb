package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.content.association.ChapterIndividual;
import com.lorevault.api.content.association.ChapterIndividualGraphRepository;
import com.lorevault.api.content.mention.IndividualMention;
import com.lorevault.api.content.mention.IndividualMentionGraphRepository;
import com.lorevault.api.ingestion.resolution.consolidation.ChapterEntityGuardService;
import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualResolutionResult;
import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualResolutionService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterIndividualResolutionService")
class ChapterIndividualResolutionServiceTest {

    @Mock
    private ChapterIndividualGraphRepository chapterIndividualRepository;

    @Mock
    private ChapterEntityGuardService chapterEntityGuardService;

    @Mock
    private IndividualMentionGraphRepository individualMentionRepository;

    @InjectMocks
    private ChapterIndividualResolutionService service;

    @Test
    @DisplayName("Checks whether a chapter exists via guard service")
    void checksWhetherChapterExistsViaGuardService() {
        UUID chapterId = UUID.randomUUID();
        when(chapterEntityGuardService.chapterExists(chapterId)).thenReturn(true);
        when(chapterEntityGuardService.chapterExists(null)).thenReturn(false);

        assertThat(service.chapterExists(chapterId)).isTrue();
        assertThat(service.chapterExists(null)).isFalse();
    }

    @Test
    @DisplayName("Merges individual mentions through shared aliases and exact normalized names")
    void mergesIndividualsThroughSharedAliasesAndExactNames() {
        UUID chapterId = UUID.randomUUID();
        UUID nyxId = UUID.randomUUID();
        UUID nightId = UUID.randomUUID();
        UUID orionId = UUID.randomUUID();

        IndividualMention nyx = mention(nyxId, chapterId, "Nyx", "nyx", List.of("Goddess of Night"), "deity", 0);
        IndividualMention night = mention(nightId, chapterId, "Goddess of Night", "goddess of night", List.of("Nyx"), null, 1);
        IndividualMention orion = mention(orionId, chapterId, "Orion", "orion", List.of(), "mortal", 2);

        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(3L);
        when(individualMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(orion, night, nyx));
        when(chapterIndividualRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterIndividualRepository.countChapterIndividualsByChapterId(chapterId)).thenReturn(2L);

        ChapterIndividualResolutionResult response = service.resolveChapter(chapterId);

        assertThat(response.success()).isTrue();
        assertThat(response.rawIndividualsProcessed()).isEqualTo(3);
        assertThat(response.chapterIndividualsCreated()).isEqualTo(2);

        verify(chapterIndividualRepository).deleteByChapterId(chapterId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterIndividual>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterIndividualRepository).saveAll(savedCaptor.capture());
        List<ChapterIndividual> saved = org.assertj.core.util.Lists.newArrayList(savedCaptor.getValue());

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(ChapterIndividual::displayName, ChapterIndividual::normalizedName, ChapterIndividual::mentionCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Goddess of Night", "goddess of night", 2),
                        org.assertj.core.groups.Tuple.tuple("Orion", "orion", 1)
                );

        ChapterIndividual goddessCluster = saved.stream()
                .filter(ind -> "goddess of night".equals(ind.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(goddessCluster.aliases()).containsExactlyInAnyOrder("Nyx", "Goddess of Night");

        ChapterIndividual orionCluster = saved.stream()
                .filter(ind -> "orion".equals(ind.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(orionCluster.aliases()).isEmpty();

        verify(chapterIndividualRepository).linkMentionsToChapterIndividual(
                eq(List.of(nightId, nyxId)),
                eq(goddessCluster.id()),
                eq(ChapterIndividualResolutionService.CHAPTER_RESOLVED)
        );
        verify(chapterIndividualRepository).linkMentionsToChapterIndividual(
                eq(List.of(orionId)),
                eq(orionCluster.id()),
                eq(ChapterIndividualResolutionService.CHAPTER_RESOLVED)
        );
    }

    @Test
    @DisplayName("Returns no-op response when chapter has no individual mentions")
    void returnsNoOpWhenChapterHasNoMentions() {
        UUID chapterId = UUID.randomUUID();
        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(0L);

        ChapterIndividualResolutionResult response = service.resolveChapter(chapterId);

        assertThat(response.success()).isFalse();
        assertThat(response.rawIndividualsProcessed()).isZero();
        assertThat(response.chapterIndividualsCreated()).isZero();

        verify(chapterIndividualRepository, never()).deleteByChapterId(any());
        verify(individualMentionRepository, never()).findByChapterId(any());
    }

    @Test
    @DisplayName("Skips save when individual mentions are present but none are resolvable")
    void skipsSaveWhenNoResolvableMentionsExist() {
        UUID chapterId = UUID.randomUUID();
        IndividualMention blank = mention(UUID.randomUUID(), chapterId, " ", " ", List.of(), null, 0);

        when(chapterIndividualRepository.countMentionsByChapterId(chapterId)).thenReturn(1L);
        when(individualMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(blank));

        ChapterIndividualResolutionResult response = service.resolveChapter(chapterId);

        assertThat(response.success()).isFalse();
        assertThat(response.rawIndividualsProcessed()).isEqualTo(1);
        assertThat(response.chapterIndividualsCreated()).isZero();

        verify(chapterIndividualRepository).deleteByChapterId(chapterId);
        verify(chapterIndividualRepository, never()).saveAll(any());
        verify(chapterIndividualRepository, never()).linkMentionsToChapterIndividual(any(), any(), any());
    }

    private IndividualMention mention(
            UUID id,
            UUID chapterId,
            String displayName,
            String normalizedName,
            List<String> aliases,
            String activity,
            int extractionIndex
    ) {
        return new IndividualMention(
                id,
                "ai-scene-analysis",
                displayName,
                normalizedName,
                aliases,
                activity,
                null,
                null,
                UUID.randomUUID(),
                chapterId,
                UUID.randomUUID(),
                "unresolved",
                extractionIndex,
                null,
                null
        );
    }
}
