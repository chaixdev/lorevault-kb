package com.lorevault.api.service.timeline;

import com.lorevault.api.application.port.EventOrderingPort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("EventOrderingService")
class EventOrderingServiceTest {

    private final EventOrderingPort port = Mockito.mock(EventOrderingPort.class);
    private final EventOrderingService service = new EventOrderingService(port);

    private Scene scene(UUID id, int sceneIndex) {
        Scene s = new Scene();
        s.setId(id);
        s.setSceneIndex(sceneIndex);
        s.setChapter(new Chapter());
        return s;
    }

    @Test
    @DisplayName("Chapter with default edges → equals sceneIndex order")
    void defaultEdgesEqualSceneIndex() {
        UUID chapterId = UUID.randomUUID();
        Scene s0 = scene(UUID.randomUUID(), 0);
        Scene s1 = scene(UUID.randomUUID(), 1);
        Scene s2 = scene(UUID.randomUUID(), 2);

        when(port.findChapterScenes(chapterId)).thenReturn(List.of(s0, s1, s2));
        when(port.findChapterTemporalEdges(chapterId)).thenReturn(List.of(
                new SimpleEntry<>(s0.getId(), s1.getId()),
                new SimpleEntry<>(s1.getId(), s2.getId())
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

        when(port.findChapterScenes(chapterId)).thenReturn(List.of(a, b, c));
        // c before a, a before b → c, a, b
        when(port.findChapterTemporalEdges(chapterId)).thenReturn(List.of(
                new SimpleEntry<>(c.getId(), a.getId()),
                new SimpleEntry<>(a.getId(), b.getId())
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

        when(port.findChapterScenes(chapterId)).thenReturn(List.of(a, b, c));
        when(port.findChapterTemporalEdges(chapterId)).thenReturn(List.of());

        List<Scene> out = service.orderChapterEvents(chapterId);
        assertThat(out).containsExactly(c, b, a);
    }

    @Test
    @DisplayName("Book up to N → concatenate per-chapter ordered sequences")
    void bookConcatenateChapters() {
        UUID bookId = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();

        when(port.findBookChapterIdsUpTo(bookId, 2)).thenReturn(List.of(c1, c2));

        Scene c1s0 = scene(UUID.randomUUID(), 0);
        Scene c1s1 = scene(UUID.randomUUID(), 1);
        Scene c2s0 = scene(UUID.randomUUID(), 0);

        when(port.findChapterScenes(c1)).thenReturn(List.of(c1s0, c1s1));
        when(port.findChapterTemporalEdges(c1)).thenReturn(List.of(
                new SimpleEntry<>(c1s0.getId(), c1s1.getId())
        ));

        when(port.findChapterScenes(c2)).thenReturn(List.of(c2s0));
        when(port.findChapterTemporalEdges(c2)).thenReturn(List.of());

        List<Scene> out = service.orderBookEventsUpToChapter(bookId, 2);
        assertThat(out).containsExactly(c1s0, c1s1, c2s0);
    }
}
