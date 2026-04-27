package com.lorevault.api.ingestion;

import com.lorevault.api.content.entities.ChapterEvent;
import com.lorevault.api.content.entities.ChapterEventGraphRepository;
import com.lorevault.api.content.entities.EventMention;
import com.lorevault.api.content.entities.EventMentionComponentLookup;
import com.lorevault.api.content.entities.EventMentionGraphRepository;
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

    @Mock
    private EventMentionGraphRepository mentionRepository;

    @Mock
    private EventMentionComponentLookup componentLookup;

    @InjectMocks
    private ChapterEventResolutionService service;

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private EventMentionComponentLookup.SameEventComponentRow componentRow(String mentionId, String componentId) {
        return new EventMentionComponentLookup.SameEventComponentRow(mentionId, componentId);
    }

    private EventMention mention(UUID id, String displayName, String normalizedName,
                                  String eventType, String relation, String evidence, UUID chapterId) {
        return new EventMention(id, null, displayName, normalizedName, List.of(),
                eventType, displayName + " description", relation, null, evidence, null, chapterId, null, null, null, null, null);
    }

    private List<ChapterEvent> captureAllSaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterEvent>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterEventRepository).saveAll(captor.capture());
        return org.assertj.core.util.Lists.newArrayList(captor.getValue());
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Returns no-op when chapter has no event mentions")
    void returnsNoOpWhenChapterHasNoMentions() {
        UUID chapterId = UUID.randomUUID();
        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(0L);

        ChapterEventResolutionResult result = service.resolveChapter(chapterId);

        assertThat(result.success()).isFalse();
        assertThat(result.rawMentionsProcessed()).isZero();
        assertThat(result.chapterEventsCreated()).isZero();
        assertThat(result.failedCorefWindowCount()).isZero();
        verify(chapterEventRepository, never()).deleteByChapterId(any());
    }

    @Test
    @DisplayName("Builds one ChapterEvent per connected component from SAME_EVENT links")
    void buildsOneChapterEventPerComponent() {
        UUID chapterId = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        UUID m3 = UUID.randomUUID();
        String comp1 = m1.toString();
        String comp2 = m3.toString();

        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(3L);
        when(componentLookup.findSameEventComponents(chapterId)).thenReturn(List.of(
                componentRow(m1.toString(), comp1),
                componentRow(m2.toString(), comp1),
                componentRow(m3.toString(), comp2)
        ));
        when(mentionRepository.findByChapterIdOrdered(chapterId)).thenReturn(List.of(
                mention(m1, "The Coronation", "the coronation", "CEREMONY", "PRECEDES", "She was crowned", chapterId),
                mention(m2, "The Coronation", "the coronation", "CEREMONY", "PRECEDES", "The ceremony ended", chapterId),
                mention(m3, "The Betrayal", "the betrayal", "BETRAYAL", "FOLLOWS", "He drew his blade", chapterId)
        ));
        when(chapterEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(2L);

        ChapterEventResolutionResult result = service.resolveChapter(chapterId);

        assertThat(result.success()).isTrue();
        assertThat(result.rawMentionsProcessed()).isEqualTo(3);
        assertThat(result.chapterEventsCreated()).isEqualTo(2);
        assertThat(result.failedCorefWindowCount()).isZero();

        List<ChapterEvent> saved = captureAllSaved();
        assertThat(saved).hasSize(2);

        // Each node gets a chapter link
        for (ChapterEvent ce : saved) {
            verify(chapterEventRepository).linkChapterToEvent(chapterId, ce.id());
        }

        verify(chapterEventRepository).deleteByChapterId(chapterId);
    }

    @Test
    @DisplayName("Canonical label is the most-frequent displayName in the component")
    void canonicalLabelIsMostFrequent() {
        UUID chapterId = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        UUID m3 = UUID.randomUUID();
        String comp = m1.toString();

        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(3L);
        when(componentLookup.findSameEventComponents(chapterId)).thenReturn(List.of(
                componentRow(m1.toString(), comp),
                componentRow(m2.toString(), comp),
                componentRow(m3.toString(), comp)
        ));
        when(mentionRepository.findByChapterIdOrdered(chapterId)).thenReturn(List.of(
                mention(m1, "Battle", "battle", "BATTLE", "PRECEDES", null, chapterId),
                mention(m2, "Great Battle", "great battle", "BATTLE", "PRECEDES", null, chapterId),
                mention(m3, "Great Battle", "great battle", "BATTLE", "PRECEDES", null, chapterId)
        ));
        when(chapterEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(1L);

        service.resolveChapter(chapterId);

        List<ChapterEvent> saved = captureAllSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).displayName()).isEqualTo("Great Battle");
    }

    @Test
    @DisplayName("Canonical label tie breaks lexicographically for deterministic rebuilds")
    void canonicalLabelTieBreaksLexicographically() {
        UUID chapterId = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        String comp = m1.toString();

        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(componentLookup.findSameEventComponents(chapterId)).thenReturn(List.of(
                componentRow(m1.toString(), comp),
                componentRow(m2.toString(), comp)
        ));
        when(mentionRepository.findByChapterIdOrdered(chapterId)).thenReturn(List.of(
                mention(m1, "Siege", "siege", "BATTLE", "PRECEDES", null, chapterId),
                mention(m2, "Ambush", "ambush", "BETRAYAL", "PRECEDES", null, chapterId)
        ));
        when(chapterEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(1L);

        service.resolveChapter(chapterId);

        List<ChapterEvent> saved = captureAllSaved();
        ChapterEvent savedEvent = saved.getFirst();
        assertThat(savedEvent.displayName()).isEqualTo("Ambush");
        assertThat(savedEvent.representativeEventType()).isEqualTo("BATTLE");
    }

    @Test
    @DisplayName("Aggregate card includes event type and evidence")
    void aggregateCardIncludesTypeAndEvidence() {
        UUID chapterId = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        String comp = m1.toString();

        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(1L);
        when(componentLookup.findSameEventComponents(chapterId)).thenReturn(List.of(
                componentRow(m1.toString(), comp)
        ));
        when(mentionRepository.findByChapterIdOrdered(chapterId)).thenReturn(List.of(
                mention(m1, "The Betrayal", "the betrayal", "BETRAYAL", "FOLLOWS", "He turned his blade on his king", chapterId)
        ));
        when(chapterEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(1L);

        service.resolveChapter(chapterId);

        List<ChapterEvent> saved = captureAllSaved();
        assertThat(saved.get(0).aggregateCard()).contains("BETRAYAL");
        assertThat(saved.get(0).aggregateCard()).contains("He turned his blade on his king");
    }

    @Test
    @DisplayName("Returns failure result when no components found after mentions exist")
    void returnsFailureWhenNoComponents() {
        UUID chapterId = UUID.randomUUID();
        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(componentLookup.findSameEventComponents(chapterId)).thenReturn(List.of());

        ChapterEventResolutionResult result = service.resolveChapter(chapterId);

        assertThat(result.success()).isFalse();
        assertThat(result.failedCorefWindowCount()).isZero();
        verify(chapterEventRepository).deleteByChapterId(chapterId);
        verify(chapterEventRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Each saved ChapterEvent carries the componentId used to key it (HIGH-2)")
    void savedChapterEventCarriesComponentId() {
        UUID chapterId = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        String comp = m1.toString();

        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(componentLookup.findSameEventComponents(chapterId)).thenReturn(List.of(
                componentRow(m1.toString(), comp),
                componentRow(m2.toString(), comp)
        ));
        when(mentionRepository.findByChapterIdOrdered(chapterId)).thenReturn(List.of(
                mention(m1, "The Siege", "the siege", "SIEGE", "PRECEDES", "Walls crumbled", chapterId),
                mention(m2, "The Siege", "the siege", "SIEGE", "PRECEDES", "Defenders fled", chapterId)
        ));
        when(chapterEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(1L);

        service.resolveChapter(chapterId);

        List<ChapterEvent> saved = captureAllSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).componentId()).isEqualTo(comp);
    }

    @Test
    @DisplayName("Mentions are linked to the ChapterEvent keyed by componentId, not save order (HIGH-2)")
    void mentionsLinkedByComponentIdNotSaveOrder() {
        UUID chapterId = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        UUID m3 = UUID.randomUUID();
        // Use lexicographically smallest of the two component roots to be consistent with Cypher
        String comp1 = m1.toString();
        String comp2 = m3.toString();

        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(3L);
        when(componentLookup.findSameEventComponents(chapterId)).thenReturn(List.of(
                componentRow(m1.toString(), comp1),
                componentRow(m2.toString(), comp1),
                componentRow(m3.toString(), comp2)
        ));
        when(mentionRepository.findByChapterIdOrdered(chapterId)).thenReturn(List.of(
                mention(m1, "Battle", "battle", "BATTLE", null, null, chapterId),
                mention(m2, "Battle", "battle", "BATTLE", null, null, chapterId),
                mention(m3, "Treaty", "treaty", "TREATY", null, null, chapterId)
        ));
        when(chapterEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(2L);

        service.resolveChapter(chapterId);

        List<ChapterEvent> saved = captureAllSaved();
        assertThat(saved).hasSize(2);

        // Find ChapterEvent for comp1 (m1+m2) and comp2 (m3) by their embedded componentId
        ChapterEvent battleEvent = saved.stream()
                .filter(e -> comp1.equals(e.componentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ChapterEvent for comp1"));
        ChapterEvent treatyEvent = saved.stream()
                .filter(e -> comp2.equals(e.componentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ChapterEvent for comp2"));

        // m1 and m2 must be linked to battleEvent, m3 to treatyEvent
        verify(chapterEventRepository).linkMentionToChapterEvent(m1, battleEvent.id(), ChapterEventResolutionService.CHAPTER_RESOLVED);
        verify(chapterEventRepository).linkMentionToChapterEvent(m2, battleEvent.id(), ChapterEventResolutionService.CHAPTER_RESOLVED);
        verify(chapterEventRepository).linkMentionToChapterEvent(m3, treatyEvent.id(), ChapterEventResolutionService.CHAPTER_RESOLVED);
    }
}
