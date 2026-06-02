package com.lorevault.api.graph.object.consolidation.chapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.graph.object.persistence.ChapterObject;
import com.lorevault.api.graph.object.persistence.ChapterObjectGraphRepository;
import com.lorevault.api.graph.object.persistence.ObjectMention;
import com.lorevault.api.graph.object.persistence.ObjectMentionGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.library.chapter.ChapterGraphRepository;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterObjectConsolidationService")
class ChapterObjectConsolidationServiceTest {

    @Mock
    private ChapterObjectGraphRepository chapterObjectRepository;

    @Mock
    private ObjectMentionGraphRepository objectMentionRepository;

    @Spy
    private ConsolidationEngine consolidationEngine = new ConsolidationEngine();

    @Mock
    private ChapterGraphRepository chapterGraphRepository;

    private static final StageExecutionContext CTX = new StageExecutionContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            StageKey.CHAPTER_OBJECT_CONSOLIDATION);

    @InjectMocks
    private ChapterObjectConsolidationService service;

    @Test
    @DisplayName("Groups object mentions by normalized name only while preserving representative metadata")
    void groupsObjectMentionsByNormalizedNameOnly() {
        UUID chapterId = UUID.randomUUID();
        UUID swordId = UUID.randomUUID();
        UUID bladeId = UUID.randomUUID();
        UUID doorId = UUID.randomUUID();

        ObjectMention sword = mention(swordId, chapterId, "Silver Sword", "silver sword", List.of("Moonblade"), "weapon", "silver", "duel", "A named blade", 0);
        ObjectMention blade = mention(bladeId, chapterId, "Moonblade", "silver sword", List.of("Silver Sword"), null, null, "ritual", "Alias mention", 1);
        ObjectMention door = mention(doorId, chapterId, "Stone Door", "stone door", List.of("gate"), "door", "stone", "barrier", "Blocks the hall", 2);

        when(chapterObjectRepository.countMentionsByChapterId(chapterId)).thenReturn(3L);
        when(objectMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(door, blade, sword));
        when(chapterObjectRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterObjectRepository.countChapterObjectsByChapterId(chapterId)).thenReturn(2L);

        ChapterObjectConsolidationResult response = service.consolidateChapter(CTX, chapterId);

        assertThat(response.success()).isTrue();
        assertThat(response.rawObjectsProcessed()).isEqualTo(3);
        assertThat(response.chapterObjectsCreated()).isEqualTo(2);

        verify(chapterObjectRepository).deleteByChapterId(chapterId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterObject>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterObjectRepository).saveAll(savedCaptor.capture());
        List<ChapterObject> saved = org.assertj.core.util.Lists.newArrayList(savedCaptor.getValue());

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(ChapterObject::displayName, ChapterObject::normalizedName, ChapterObject::mentionCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Moonblade", "silver sword", 2),
                        org.assertj.core.groups.Tuple.tuple("Stone Door", "stone door", 1)
                );

        ChapterObject swordCluster = saved.stream()
                .filter(object -> "silver sword".equals(object.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(swordCluster.aliases()).containsExactly("Silver Sword", "Moonblade");
        assertThat(swordCluster.type()).isEqualTo("weapon");
        assertThat(swordCluster.material()).isEqualTo("silver");
        assertThat(swordCluster.purpose()).isEqualTo("ritual");
        assertThat(swordCluster.description()).isEqualTo("Alias mention");

        ChapterObject doorCluster = saved.stream()
                .filter(object -> "stone door".equals(object.normalizedName()))
                .findFirst()
                .orElseThrow();

        verify(chapterObjectRepository).linkMentionsToChapterObject(
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(swordId, bladeId))),
                eq(swordCluster.id()),
                eq(ChapterObjectConsolidationService.CHAPTER_CONSOLIDATED)
        );
        verify(chapterObjectRepository).linkMentionsToChapterObject(List.of(doorId), doorCluster.id(), ChapterObjectConsolidationService.CHAPTER_CONSOLIDATED);
    }

    @Test
    @DisplayName("Returns successful zero-count result when chapter has no object mentions")
    void returnsSuccessfulNoOpWhenChapterHasNoObjectMentions() {
        UUID chapterId = UUID.randomUUID();
        when(chapterObjectRepository.countMentionsByChapterId(chapterId)).thenReturn(0L);

        ChapterObjectConsolidationResult response = service.consolidateChapter(CTX, chapterId);

        assertThat(response.success()).isTrue();
        assertThat(response.rawObjectsProcessed()).isZero();
        assertThat(response.chapterObjectsCreated()).isZero();

        verify(chapterObjectRepository).deleteByChapterId(chapterId);
        verify(objectMentionRepository, never()).findByChapterId(any());
    }

    @Test
    @DisplayName("Merges object mentions through shared aliases when normalized names differ")
    void mergesObjectsThroughSharedAliases() {
        UUID chapterId = UUID.randomUUID();
        UUID swordId = UUID.randomUUID();
        UUID daggerId = UUID.randomUUID();

        ObjectMention sword = mention(swordId, chapterId, "Silver Sword", "silver sword", List.of("Moonblade"), "weapon", "silver", "duel", "A named blade", 0);
        ObjectMention dagger = mention(daggerId, chapterId, "Ceremonial Dagger", "ceremonial dagger", List.of("Moonblade"), "weapon", "silver", "duel", "Shares an alias", 1);

        when(chapterObjectRepository.countMentionsByChapterId(chapterId)).thenReturn(2L);
        when(objectMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(dagger, sword));
        when(chapterObjectRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterObjectRepository.countChapterObjectsByChapterId(chapterId)).thenReturn(1L);

        ChapterObjectConsolidationResult response = service.consolidateChapter(CTX, chapterId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterObjectsCreated()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterObject>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterObjectRepository).saveAll(savedCaptor.capture());
        List<ChapterObject> saved = org.assertj.core.util.Lists.newArrayList(savedCaptor.getValue());

        assertThat(saved).hasSize(1);
        ChapterObject merged = saved.get(0);
        assertThat(merged.normalizedName()).isEqualTo("ceremonial dagger");
        assertThat(merged.mentionCount()).isEqualTo(2);
        assertThat(merged.aliases()).containsExactly("Moonblade");
        assertThat(merged.type()).isEqualTo("weapon");
        assertThat(merged.material()).isEqualTo("silver");
        assertThat(merged.purpose()).isEqualTo("duel");
        assertThat(merged.description()).isEqualTo("Shares an alias");

        verify(chapterObjectRepository).linkMentionsToChapterObject(
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(swordId, daggerId))),
                eq(merged.id()),
                eq(ChapterObjectConsolidationService.CHAPTER_CONSOLIDATED)
        );
    }

    @Test
    @DisplayName("Skips save when object mentions are present but none are resolvable")
    void skipsSaveWhenNoResolvableObjectMentionsExist() {
        UUID chapterId = UUID.randomUUID();
        // Both normalizedName and aliases are blank/null — NameKeys.from() returns empty set
        ObjectMention blank = mention(UUID.randomUUID(), chapterId, " ", " ", null, null, null, null, null, 0);

        when(chapterObjectRepository.countMentionsByChapterId(chapterId)).thenReturn(1L);
        when(objectMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(blank));

        ChapterObjectConsolidationResult response = service.consolidateChapter(CTX, chapterId);

        assertThat(response.success()).isFalse();
        assertThat(response.rawObjectsProcessed()).isEqualTo(1);
        assertThat(response.chapterObjectsCreated()).isZero();

        verify(chapterObjectRepository).deleteByChapterId(chapterId);
        verify(chapterObjectRepository, never()).saveAll(any());
        verify(chapterObjectRepository, never()).linkChapterToObject(any(), any());
        verify(chapterObjectRepository, never()).linkMentionsToChapterObject(any(), any(), any());
    }

    private ObjectMention mention(
            UUID id,
            UUID chapterId,
            String displayName,
            String normalizedName,
            List<String> aliases,
            String type,
            String material,
            String purpose,
            String description,
            int extractionIndex
    ) {
        return new ObjectMention(
                id,
                "ai-scene-analysis",
                displayName,
                normalizedName,
                aliases,
                type,
                material,
                purpose,
                description,
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
