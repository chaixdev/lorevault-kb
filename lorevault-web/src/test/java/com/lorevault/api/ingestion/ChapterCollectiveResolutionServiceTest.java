package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.content.association.ChapterCollective;
import com.lorevault.api.content.association.ChapterCollectiveGraphRepository;
import com.lorevault.api.content.mention.CollectiveMention;
import com.lorevault.api.ingestion.resolution.consolidation.ChapterEntityGuardService;
import com.lorevault.api.content.mention.CollectiveMentionGraphRepository;
import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionResult;
import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterCollectiveResolutionService")
class ChapterCollectiveResolutionServiceTest {

    @Mock
    private ChapterCollectiveGraphRepository chapterCollectiveRepository;

    @Mock
    private ChapterEntityGuardService chapterEntityGuardService;

    @Mock
    private CollectiveMentionGraphRepository collectiveMentionRepository;

    @InjectMocks
    private ChapterCollectiveResolutionService service;

    @Test
    @DisplayName("Checks whether a chapter exists via guard service")
    void checksWhetherChapterExists() {
        UUID chapterId = UUID.randomUUID();
        when(chapterEntityGuardService.chapterExists(chapterId)).thenReturn(true);
        when(chapterEntityGuardService.chapterExists(null)).thenReturn(false);

        assertThat(service.chapterExists(chapterId)).isTrue();
        assertThat(service.chapterExists(null)).isFalse();
    }

    @Test
    @DisplayName("Groups collective mentions by normalized name only while preserving representative metadata")
    void groupsCollectiveMentionsByNormalizedNameOnly() {
        UUID chapterId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        UUID squadId = UUID.randomUUID();
        UUID councilId = UUID.randomUUID();

        CollectiveMention crew = mention(
                crewId,
                chapterId,
                "Bridge Four",
                "bridge four",
                List.of("Bridge Four"),
                "military",
                "Explicit",
                "Bridge Four forms up",
                0
        );
        CollectiveMention squad = mention(
                squadId,
                chapterId,
                "The Fourth Bridge Crew",
                "bridge four",
                List.of("Fourth Bridge"),
                null,
                null,
                "They rally together",
                1
        );
        CollectiveMention council = mention(
                councilId,
                chapterId,
                "Kholin Council",
                "kholin council",
                List.of("High Council"),
                "government",
                "StronglyImplied",
                "Council convenes",
                2
        );

        when(chapterCollectiveRepository.countMentionsByChapterId(chapterId)).thenReturn(3L);
        when(collectiveMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(council, squad, crew));
        when(chapterCollectiveRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterCollectiveRepository.countChapterCollectivesByChapterId(chapterId)).thenReturn(2L);

        ChapterCollectiveResolutionResult response = service.resolveChapter(chapterId);

        assertThat(response.success()).isTrue();
        assertThat(response.rawCollectivesProcessed()).isEqualTo(3);
        assertThat(response.chapterCollectivesCreated()).isEqualTo(2);

        verify(chapterCollectiveRepository).deleteByChapterId(chapterId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterCollective>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterCollectiveRepository).saveAll(savedCaptor.capture());
        List<ChapterCollective> saved = org.assertj.core.util.Lists.newArrayList(savedCaptor.getValue());

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(ChapterCollective::displayName, ChapterCollective::normalizedName, ChapterCollective::mentionCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Bridge Four", "bridge four", 2),
                        org.assertj.core.groups.Tuple.tuple("Kholin Council", "kholin council", 1)
                );

        ChapterCollective bridgeFour = saved.stream()
                .filter(collective -> "bridge four".equals(collective.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(bridgeFour.aliases()).containsExactly("Bridge Four", "Fourth Bridge");
        assertThat(bridgeFour.collectiveType()).isEqualTo("military");
        assertThat(bridgeFour.certainty()).isEqualTo("Explicit");
        assertThat(bridgeFour.evidence()).isEqualTo("Bridge Four forms up");

        ChapterCollective kholinCouncil = saved.stream()
                .filter(collective -> "kholin council".equals(collective.normalizedName()))
                .findFirst()
                .orElseThrow();

        verify(chapterCollectiveRepository).linkMentionsToChapterCollective(
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(crewId, squadId))),
                eq(bridgeFour.id()),
                eq(ChapterCollectiveResolutionService.CHAPTER_RESOLVED)
        );
        verify(chapterCollectiveRepository).linkMentionsToChapterCollective(
                List.of(councilId),
                kholinCouncil.id(),
                ChapterCollectiveResolutionService.CHAPTER_RESOLVED
        );
    }

    @Test
    @DisplayName("Returns successful zero-count result when chapter has no collective mentions")
    void returnsSuccessfulNoOpWhenChapterHasNoCollectiveMentions() {
        UUID chapterId = UUID.randomUUID();
        when(chapterCollectiveRepository.countMentionsByChapterId(chapterId)).thenReturn(0L);

        ChapterCollectiveResolutionResult response = service.resolveChapter(chapterId);

        assertThat(response.success()).isTrue();
        assertThat(response.rawCollectivesProcessed()).isZero();
        assertThat(response.chapterCollectivesCreated()).isZero();

        verify(chapterCollectiveRepository, never()).deleteByChapterId(any());
        verify(collectiveMentionRepository, never()).findByChapterId(any());
    }

    private CollectiveMention mention(
            UUID id,
            UUID chapterId,
            String displayName,
            String normalizedName,
            List<String> aliases,
            String collectiveType,
            String certainty,
            String evidence,
            int extractionIndex
    ) {
        return new CollectiveMention(
                id,
                "ai-scene-analysis",
                displayName,
                normalizedName,
                aliases,
                collectiveType,
                certainty,
                evidence,
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
