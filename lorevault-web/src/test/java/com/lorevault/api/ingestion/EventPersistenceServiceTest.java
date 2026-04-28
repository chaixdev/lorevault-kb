package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.infrastructure.*;

import com.lorevault.api.content.mention.EventMention;
import com.lorevault.api.content.mention.EventMentionGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.ingestion.triad.TriadAnalysisModels;
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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventPersistenceService")
class EventPersistenceServiceTest {

    @Mock
    private EventMentionGraphRepository eventMentionRepository;

    @InjectMocks
    private EventPersistenceService service;

    @Test
    @DisplayName("Persists one EventMention per extracted block and links mention by sceneIndex")
    void persistsEventsAndLinksMentions() {
        UUID sceneId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Scene persistedScene = new Scene(sceneId, 4, 0L, 10L, "ctx", "text", chapterId, null, null, null, null, null);
        TriadAnalysisModels.EventExtraction extracted =
                new TriadAnalysisModels.EventExtraction(
                        "  The Winter War  ",
                        "war",
                        "A remembered historical war that characters invoke as past context.",
                        "R:temporal.before",
                        "Explicit",
                        "They still speak of the Winter War"
                );
        TriadAnalysisModels.SceneEventExtraction byScene =
                new TriadAnalysisModels.SceneEventExtraction(4, List.of(extracted));

        when(eventMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistExtractedEvents(List.of(persistedScene), List.of(byScene));

        ArgumentCaptor<EventMention> savedCaptor = ArgumentCaptor.forClass(EventMention.class);
        verify(eventMentionRepository).save(savedCaptor.capture());
        EventMention saved = savedCaptor.getValue();
        assertThat(saved.source()).isEqualTo("ai-scene-analysis");
        assertThat(saved.displayName()).isEqualTo("The Winter War");
        assertThat(saved.normalizedName()).isEqualTo("the winter war");
        assertThat(saved.aliases()).containsExactly("The Winter War");
        assertThat(saved.eventType()).isEqualTo("war");
        assertThat(saved.description()).isEqualTo("A remembered historical war that characters invoke as past context.");
        assertThat(saved.sceneRelativeRelation()).isEqualTo("R:temporal.before");
        assertThat(saved.certainty()).isEqualTo("Explicit");
        assertThat(saved.evidence()).isEqualTo("They still speak of the Winter War");
        assertThat(saved.sceneId()).isEqualTo(sceneId);
        assertThat(saved.chapterId()).isEqualTo(chapterId);
        assertThat(saved.bookId()).isNull();
        assertThat(saved.resolutionStatus()).isEqualTo("unresolved");
        assertThat(saved.extractionIndex()).isEqualTo(0);

        verify(eventMentionRepository).linkMentionToScene(eq(sceneId), eq(saved.id()));
    }

    @Test
    @DisplayName("Skips extracted events without non-blank name")
    void skipsEventsWithoutName() {
        Scene persistedScene = new Scene(UUID.randomUUID(), 0, 0L, 10L, "ctx", "text", UUID.randomUUID(), null, null, null, null, null);
        TriadAnalysisModels.EventExtraction invalid =
                new TriadAnalysisModels.EventExtraction(
                        " ",
                        "meeting",
                        "A meeting alluded to without a usable event name.",
                        "R:temporal.overlaps",
                        "WeaklyImplied",
                        ""
                );

        service.persistExtractedEvents(
                List.of(persistedScene),
                List.of(new TriadAnalysisModels.SceneEventExtraction(0, List.of(invalid)))
        );

        verify(eventMentionRepository, never()).save(any());
        verify(eventMentionRepository, never()).linkMentionToScene(any(), any());
    }
}
