package com.lorevault.api.timeline;

import com.lorevault.api.graph.event.scene.Scene;
import com.lorevault.api.library.chapter.ChapterReadRepository;
import com.lorevault.api.graph.event.scene.SceneGraphRepository;
import com.lorevault.api.graph.timeline.application.EventOrderingService;
import com.lorevault.api.graph.timeline.infrastructure.TemporalReadRepository;
import com.lorevault.api.graph.timeline.infrastructure.TemporalReadRepository.TemporalEdgePair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("EventOrderingService")
class EventOrderingServiceTest {

    private final SceneGraphRepository sceneRepo = Mockito.mock(SceneGraphRepository.class);
    private final ChapterReadRepository chapterReadRepo = Mockito.mock(ChapterReadRepository.class);
    private final TemporalReadRepository temporalReadRepo = Mockito.mock(TemporalReadRepository.class);
    private final EventOrderingService service = new EventOrderingService(sceneRepo, chapterReadRepo, temporalReadRepo);

    private Scene scene(UUID id, int sceneIndex) {
        return new Scene(
                id,
                sceneIndex,
                0L,
                1L,
                "ctx",
                null,
                null,
                null,
                "text",
                null,
                null,
                null,
                null,
                null
        );
    }

    private Scene scene(UUID id, UUID chapterId, int sceneIndex) {
        return new Scene(
                id,
                sceneIndex,
                0L,
                1L,
                "ctx",
                null,
                null,
                null,
                "text",
                chapterId,
                null,
                null,
                null,
                null
        );
    }

    private static TemporalEdgePair edge(UUID from, UUID to) {
        return new TemporalEdgePair() {
            @Override public UUID getFromId() { return from; }
            @Override public UUID getToId()   { return to; }
        };
    }

    @Test
    @DisplayName("Chapter with default edges → equals sceneIndex order")
    void defaultEdgesEqualSceneIndex() {
        UUID chapterId = UUID.randomUUID();
        Scene s0 = scene(UUID.randomUUID(), 0);
        Scene s1 = scene(UUID.randomUUID(), 1);
        Scene s2 = scene(UUID.randomUUID(), 2);

        when(sceneRepo.findByChapterId(chapterId)).thenReturn(List.of(s0, s1, s2));
        when(temporalReadRepo.findChapterEventEdges(chapterId)).thenReturn(List.of(
                edge(s0.getEventId(), s1.getEventId()),
                edge(s1.getEventId(), s2.getEventId())
        ));

        List<Scene> out = service.orderChapterEvents(chapterId);
        assertThat(out).containsExactly(s0, s1, s2);
    }

    @Test
    @DisplayName("Chapter with upgraded edges → reflects edges ordering")
    void upgradedEdgesReorder() {
        UUID chapterId = UUID.randomUUID();
        Scene a = scene(UUID.randomUUID(), 0);
        Scene b = scene(UUID.randomUUID(), 1);
        Scene c = scene(UUID.randomUUID(), 2);

        when(sceneRepo.findByChapterId(chapterId)).thenReturn(List.of(a, b, c));
        // c before a, a before b → c, a, b
        when(temporalReadRepo.findChapterEventEdges(chapterId)).thenReturn(List.of(
                edge(c.getEventId(), a.getEventId()),
                edge(a.getEventId(), b.getEventId())
        ));

        List<Scene> out = service.orderChapterEvents(chapterId);
        assertThat(out).containsExactly(c, a, b);
    }

    @Test
    @DisplayName("Chapter with missing edges → falls back to sceneIndex, UUID tiebreaker stable")
    void missingEdgesFallback() {
        UUID chapterId = UUID.randomUUID();
        // No edges → sort by sceneIndex, then UUID
        Scene a = scene(UUID.fromString("00000000-0000-0000-0000-000000000001"), 2);
        Scene b = scene(UUID.fromString("00000000-0000-0000-0000-000000000000"), 2);
        Scene c = scene(UUID.fromString("00000000-0000-0000-0000-000000000010"), 1);

        when(sceneRepo.findByChapterId(chapterId)).thenReturn(List.of(a, b, c));
        when(temporalReadRepo.findChapterEventEdges(chapterId)).thenReturn(List.of());

        List<Scene> out = service.orderChapterEvents(chapterId);
        assertThat(out).containsExactly(c, b, a);
    }

    @Test
    @DisplayName("Book up to N without cross-chapter edges → preserves chapter order fallback")
    void bookPreservesChapterOrderFallback() {
        UUID bookId = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();

        when(chapterReadRepo.findChapterIdsUpTo(bookId, 2)).thenReturn(List.of(c1, c2));

        Scene c1s0 = scene(UUID.randomUUID(), c1, 0);
        Scene c1s1 = scene(UUID.randomUUID(), c1, 1);
        Scene c2s0 = scene(UUID.randomUUID(), c2, 0);

        when(sceneRepo.findByChapterId(c1)).thenReturn(List.of(c1s0, c1s1));
        when(sceneRepo.findByChapterId(c2)).thenReturn(List.of(c2s0));
        when(temporalReadRepo.findBookEventEdgesUpToChapter(bookId, 2)).thenReturn(List.of(
                edge(c1s0.getEventId(), c1s1.getEventId())
        ));

        List<Scene> out = service.orderBookEventsUpToChapter(bookId, 2);
        assertThat(out).containsExactly(c1s0, c1s1, c2s0);
    }

    @Test
    @DisplayName("Book up to N with cross-chapter edge → uses one temporal graph across chapters")
    void bookUsesCrossChapterTemporalEdges() {
        UUID bookId = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();

        when(chapterReadRepo.findChapterIdsUpTo(bookId, 2)).thenReturn(List.of(c1, c2));

        Scene c1s0 = scene(UUID.randomUUID(), c1, 0);
        Scene c1s1 = scene(UUID.randomUUID(), c1, 1);
        Scene c2s0 = scene(UUID.randomUUID(), c2, 0);

        when(sceneRepo.findByChapterId(c1)).thenReturn(List.of(c1s0, c1s1));
        when(sceneRepo.findByChapterId(c2)).thenReturn(List.of(c2s0));
        when(temporalReadRepo.findBookEventEdgesUpToChapter(bookId, 2)).thenReturn(List.of(
                edge(c1s0.getEventId(), c1s1.getEventId()),
                edge(c2s0.getEventId(), c1s1.getEventId())
        ));

        List<Scene> out = service.orderBookEventsUpToChapter(bookId, 2);
        assertThat(out).containsExactly(c1s0, c2s0, c1s1);
    }
}
