package com.lorevault.api.graph.event.consolidation.chapter;

import com.lorevault.api.graph.event.persistence.ChapterEvent;
import com.lorevault.api.graph.event.persistence.ChapterEventGraphRepository;
import com.lorevault.api.graph.event.persistence.EventMention;
import com.lorevault.api.graph.event.persistence.EventMentionComponentLookup;
import com.lorevault.api.graph.event.persistence.EventMentionGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;

import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
@DisplayName("ChapterEventConsolidationService")
class ChapterEventConsolidationServiceTest {

    @Mock
    private ChapterEventGraphRepository chapterEventRepository;

    @Mock
    private EventMentionGraphRepository mentionRepository;

    @Mock
    private EventMentionComponentLookup componentLookup;

    private static final StageExecutionContext CTX = new StageExecutionContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            StageKey.CHAPTER_EVENT_CONSOLIDATION);

    @InjectMocks
    private ChapterEventConsolidationService service;

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private EventMentionComponentLookup.SameEventComponentRow componentRow(String mentionId, String componentId) {
        return new EventMentionComponentLookup.SameEventComponentRow(mentionId, componentId);
    }

    private EventMention mention(UUID id, String displayName, String normalizedName,
                                   String eventType, String relation, String evidence, UUID chapterId) {
        return mention(id, displayName, normalizedName, List.of(), eventType, relation, evidence, chapterId);
    }

    private EventMention mention(UUID id, String displayName, String normalizedName, List<String> aliases,
                                 String eventType, String relation, String evidence, UUID chapterId) {
        return new EventMention(id, null, displayName, normalizedName, aliases,
                eventType, displayName + " description", relation, null, evidence, UUID.randomUUID(), null, chapterId, null, null, null, null, null);
    }

    private List<ChapterEvent> captureAllSaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterEvent>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterEventRepository).saveAll(captor.capture());
        return org.assertj.core.util.Lists.newArrayList(captor.getValue());
    }

    @SuppressWarnings("unchecked")
    private List<String> recordStringList(ChapterEvent chapterEvent, String accessor) {
        try {
            Method method = ChapterEvent.class.getMethod(accessor);
            return (List<String>) method.invoke(chapterEvent);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Unable to read ChapterEvent." + accessor, e);
        }
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Returns no-op when chapter has no event mentions")
    void returnsNoOpWhenChapterHasNoMentions() {
        UUID chapterId = UUID.randomUUID();
        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(0L);

        ChapterEventConsolidationResult result = service.consolidateChapter(CTX, chapterId);

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

        ChapterEventConsolidationResult result = service.consolidateChapter(CTX, chapterId);

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

        service.consolidateChapter(CTX, chapterId);

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

        service.consolidateChapter(CTX, chapterId);

        List<ChapterEvent> saved = captureAllSaved();
        ChapterEvent savedEvent = saved.getFirst();
        assertThat(savedEvent.displayName()).isEqualTo("Ambush");
        assertThat(savedEvent.representativeEventType()).isEqualTo("BATTLE");
    }

    @Test
    @DisplayName("Aggregate card includes supported variants, evidence, and scene-relative distribution")
    void aggregateCardIncludesEnrichedEvidence() {
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
                mention(m1, "The Betrayal", "the betrayal", List.of("The Coup", "betrayal"), "BETRAYAL", "FOLLOWS", "He turned his blade on his king", chapterId),
                mention(m2, "The Betrayal", "the betrayal", List.of("the betrayal", "The Coup"), "AMBUSH", "PRECEDES", "The king's guard answered too late", chapterId)
        ));
        when(chapterEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(1L);

        service.consolidateChapter(CTX, chapterId);

        List<ChapterEvent> saved = captureAllSaved();
        ChapterEvent savedEvent = saved.get(0);
        assertThat(savedEvent.aggregateCard()).contains("BETRAYAL");
        assertThat(savedEvent.aggregateCard()).contains("**Supported event type variants:** AMBUSH, BETRAYAL");
        assertThat(savedEvent.aggregateCard()).contains("**Scene-relative relation distribution:**");
        assertThat(savedEvent.aggregateCard()).contains("- FOLLOWS: 1");
        assertThat(savedEvent.aggregateCard()).contains("- PRECEDES: 1");
        assertThat(savedEvent.aggregateCard()).doesNotContain("**Evidence snippets:**");
        assertThat(savedEvent.aggregateCard()).doesNotContain("He turned his blade on his king");
        assertThat(savedEvent.aggregateCard()).doesNotContain("The king's guard answered too late");
        assertThat(saved.get(0).aggregateCard()).contains("**Descriptions:**");
        assertThat(saved.get(0).aggregateCard()).contains("The Betrayal description");
        assertThat(recordStringList(savedEvent, "supportedAliases")).containsExactly("The Betrayal", "The Coup", "betrayal", "the betrayal");
        assertThat(recordStringList(savedEvent, "supportedEventTypes")).containsExactly("AMBUSH", "BETRAYAL");
        assertThat(recordStringList(savedEvent, "identityEvidence")).containsExactly(
                "He turned his blade on his king",
                "The king's guard answered too late"
        );
    }

    @Test
    @DisplayName("ChapterEvent stores deterministic supported lists and filters blanks")
    void chapterEventStoresDeterministicSupportedLists() {
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
                new EventMention(m1, null, "The Accord", "the accord", Arrays.asList("Zeta", "", null, "Alpha", "Alpha"),
                        "TREATY", "The Accord description", "PRECEDES", null, "", UUID.randomUUID(), null, chapterId, null, null, null, null, null),
                new EventMention(m2, null, "The Accord", "the accord", List.of("Beta", "Alpha"),
                        "", "The Accord description", null, null, null, UUID.randomUUID(), null, chapterId, null, null, null, null, null)
        ));
        when(chapterEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chapterEventRepository.countChapterEventsByChapterId(chapterId)).thenReturn(1L);

        service.consolidateChapter(CTX, chapterId);

        ChapterEvent savedEvent = captureAllSaved().getFirst();
        assertThat(recordStringList(savedEvent, "supportedAliases")).containsExactly("Alpha", "Beta", "The Accord", "Zeta", "the accord");
        assertThat(recordStringList(savedEvent, "supportedEventTypes")).containsExactly("TREATY");
        assertThat(recordStringList(savedEvent, "identityEvidence")).isEmpty();
        assertThat(savedEvent.aggregateCard()).contains("**Event type:** TREATY");
        assertThat(savedEvent.aggregateCard()).doesNotContain("Supported event type variants");
    }

    @Test
    @DisplayName("Returns failure result when no components found after mentions exist")
    void returnsFailureWhenNoComponents() {
        UUID chapterId = UUID.randomUUID();
        when(chapterEventRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(componentLookup.findSameEventComponents(chapterId)).thenReturn(List.of());

        ChapterEventConsolidationResult result = service.consolidateChapter(CTX, chapterId);

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

        service.consolidateChapter(CTX, chapterId);

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

        service.consolidateChapter(CTX, chapterId);

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
        verify(chapterEventRepository).linkMentionToChapterEvent(m1, battleEvent.id(), ChapterEventConsolidationService.CHAPTER_CONSOLIDATED);
        verify(chapterEventRepository).linkMentionToChapterEvent(m2, battleEvent.id(), ChapterEventConsolidationService.CHAPTER_CONSOLIDATED);
        verify(chapterEventRepository).linkMentionToChapterEvent(m3, treatyEvent.id(), ChapterEventConsolidationService.CHAPTER_CONSOLIDATED);
    }
}
