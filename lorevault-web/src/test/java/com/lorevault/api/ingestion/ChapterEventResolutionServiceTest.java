package com.lorevault.api.ingestion;

import com.lorevault.api.content.entities.ChapterEvent;
import com.lorevault.api.content.entities.ChapterEventGraphRepository;
import com.lorevault.api.ingestion.application.resolution.ChapterEventResolutionService;
import com.lorevault.api.ingestion.application.result.ChapterEventResolutionResult;

import java.util.List;
import java.util.UUID;
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
@DisplayName("ChapterEventResolutionService")
class ChapterEventResolutionServiceTest {

    @Mock
    private ChapterEventGraphRepository chapterEventRepository;

    @InjectMocks
    private ChapterEventResolutionService service;

    @Test
    @DisplayName("Rebuilds one ChapterEvent per normalized name and relinks mentions")
    void rebuildsChapterEventsFromCandidates() {
        UUID chapterId = UUID.randomUUID();
        ChapterEventGraphRepository.ChapterEventCandidateView coronation = candidate(
                "The Coronation", "the coronation", "CEREMONY", 3L,
                List.of("Nyx was crowned", "The ceremony began"),
                List.of("CEREMONY"),
                List.of("PRECEDES")
        );
        ChapterEventGraphRepository.ChapterEventCandidateView battle = candidate(
                "Battle of the Vale", "battle of the vale", "BATTLE", 2L,
                List.of("Swords clashed at dawn"),
                List.of("BATTLE"),
                List.of("FOLLOWS")
        );

        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(5L);
        when(chapterEventRepository.findResolutionCandidates(chapterId)).thenReturn(List.of(coronation, battle));
        when(chapterEventRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(2L);

        ChapterEventResolutionResult result = service.resolveChapter(chapterId);

        assertThat(result.success()).isTrue();
        assertThat(result.rawMentionsProcessed()).isEqualTo(5);
        assertThat(result.chapterEventsCreated()).isEqualTo(2);

        verify(chapterEventRepository).deleteByChapterId(chapterId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterEvent>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterEventRepository).saveAll(savedCaptor.capture());
        List<ChapterEvent> saved = toList(savedCaptor.getValue());
        assertThat(saved)
                .hasSize(2)
                .extracting(ChapterEvent::normalizedName)
                .containsExactlyInAnyOrder("the coronation", "battle of the vale");

        // Each saved event should have an aggregate card
        for (ChapterEvent ce : saved) {
            assertThat(ce.aggregateCard()).isNotBlank();
            assertThat(ce.aggregateCard()).contains(ce.displayName());
        }

        for (ChapterEvent ce : saved) {
            verify(chapterEventRepository).linkChapterToEvent(chapterId, ce.id());
            verify(chapterEventRepository).linkMentionsToChapterEvent(
                    chapterId,
                    ce.normalizedName(),
                    ce.id(),
                    ChapterEventResolutionService.CHAPTER_RESOLVED
            );
        }
    }

    @Test
    @DisplayName("Aggregate card includes event type and evidence snippets")
    void aggregateCardContainsEventTypeAndEvidence() {
        UUID chapterId = UUID.randomUUID();
        ChapterEventGraphRepository.ChapterEventCandidateView c = candidate(
                "The Betrayal", "the betrayal", "BETRAYAL", 1L,
                List.of("He turned his blade on his own king"),
                List.of("BETRAYAL"),
                List.of("FOLLOWS")
        );

        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(1L);
        when(chapterEventRepository.findResolutionCandidates(chapterId)).thenReturn(List.of(c));
        when(chapterEventRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(1L);

        service.resolveChapter(chapterId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterEvent>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterEventRepository).saveAll(savedCaptor.capture());
        ChapterEvent saved = toList(savedCaptor.getValue()).get(0);

        assertThat(saved.aggregateCard()).contains("BETRAYAL");
        assertThat(saved.aggregateCard()).contains("He turned his blade on his own king");
        assertThat(saved.aggregateCard()).contains("FOLLOWS");
    }

    @Test
    @DisplayName("Skips save when there are no candidates")
    void skipsSaveWhenNoCandidates() {
        UUID chapterId = UUID.randomUUID();
        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(chapterEventRepository.findResolutionCandidates(chapterId)).thenReturn(List.of());

        ChapterEventResolutionResult result = service.resolveChapter(chapterId);

        assertThat(result.success()).isFalse();
        assertThat(result.rawMentionsProcessed()).isEqualTo(2);
        assertThat(result.chapterEventsCreated()).isZero();

        verify(chapterEventRepository).deleteByChapterId(chapterId);
        verify(chapterEventRepository, never()).saveAll(any());
        verify(chapterEventRepository, never()).linkChapterToEvent(any(), any());
        verify(chapterEventRepository, never()).linkMentionsToChapterEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ignores blank normalized names from candidates")
    void ignoresBlankNormalizedNames() {
        UUID chapterId = UUID.randomUUID();
        ChapterEventGraphRepository.ChapterEventCandidateView blank = candidate(
                "Unknown event", "   ", null, 1L, List.of(), List.of(), List.of()
        );
        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(1L);
        when(chapterEventRepository.findResolutionCandidates(chapterId)).thenReturn(List.of(blank));

        ChapterEventResolutionResult result = service.resolveChapter(chapterId);

        assertThat(result.success()).isFalse();
        assertThat(result.rawMentionsProcessed()).isEqualTo(1);

        verify(chapterEventRepository).deleteByChapterId(chapterId);
        verify(chapterEventRepository, never()).saveAll(any());
        verify(chapterEventRepository, never()).linkMentionsToChapterEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Returns no-op response when chapter has no event mentions")
    void returnsNoOpWhenChapterHasNoMentions() {
        UUID chapterId = UUID.randomUUID();
        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(0L);

        ChapterEventResolutionResult result = service.resolveChapter(chapterId);

        assertThat(result.success()).isFalse();
        assertThat(result.rawMentionsProcessed()).isZero();
        assertThat(result.chapterEventsCreated()).isZero();
        verify(chapterEventRepository, never()).deleteByChapterId(any());
    }

    private ChapterEventGraphRepository.ChapterEventCandidateView candidate(
            String displayName,
            String normalizedName,
            String representativeEventType,
            Long mentionCount,
            List<String> evidenceSnippets,
            List<String> eventTypes,
            List<String> sceneRelativeRelations
    ) {
        return new ChapterEventGraphRepository.ChapterEventCandidateView() {
            @Override public String getDisplayName() { return displayName; }
            @Override public String getNormalizedName() { return normalizedName; }
            @Override public String getRepresentativeEventType() { return representativeEventType; }
            @Override public Long getMentionCount() { return mentionCount; }
            @Override public List<String> getEvidenceSnippets() { return evidenceSnippets; }
            @Override public List<String> getEventTypes() { return eventTypes; }
            @Override public List<String> getSceneRelativeRelations() { return sceneRelativeRelations; }
        };
    }

    private List<ChapterEvent> toList(Iterable<ChapterEvent> iterable) {
        return iterable == null ? List.of() : org.assertj.core.util.Lists.newArrayList(iterable);
    }
}
