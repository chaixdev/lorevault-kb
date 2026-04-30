package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.content.mention.CollectiveMention;
import com.lorevault.api.content.mention.CollectiveMentionGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.ingestion.infrastructure.CollectivePersistenceService;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectivePersistenceService")
class CollectivePersistenceServiceTest {

    @Mock
    private CollectiveMentionGraphRepository collectiveMentionRepository;

    @InjectMocks
    private CollectivePersistenceService service;

    @Test
    @DisplayName("Persists one CollectiveMention per extracted block and links mention by sceneIndex")
    void persistsCollectivesAndLinksMentions() {
        UUID sceneId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Scene persistedScene = new Scene(sceneId, 1, 0L, 10L, "ctx", "text", chapterId, null, null, null, null, null);
        TriadAnalysisModels.CollectiveExtraction extracted =
                new TriadAnalysisModels.CollectiveExtraction(
                        List.of("  Bridge Four  "),
                        "military",
                        "Explicit",
                        "Bridge Four lines up around Kaladin"
                );
        TriadAnalysisModels.SceneCollectiveExtraction byScene =
                new TriadAnalysisModels.SceneCollectiveExtraction(1, List.of(extracted));

        when(collectiveMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistExtractedCollectives(List.of(persistedScene), List.of(byScene));

        ArgumentCaptor<CollectiveMention> savedCaptor = ArgumentCaptor.forClass(CollectiveMention.class);
        verify(collectiveMentionRepository).save(savedCaptor.capture());
        CollectiveMention saved = savedCaptor.getValue();
        assertThat(saved.source()).isEqualTo("ai-scene-analysis");
        assertThat(saved.displayName()).isEqualTo("Bridge Four");
        assertThat(saved.normalizedName()).isEqualTo("bridge four");
        assertThat(saved.aliases()).containsExactly("  Bridge Four  ");
        assertThat(saved.collectiveType()).isEqualTo("military");
        assertThat(saved.certainty()).isEqualTo("Explicit");
        assertThat(saved.evidence()).isEqualTo("Bridge Four lines up around Kaladin");
        assertThat(saved.sceneId()).isEqualTo(sceneId);
        assertThat(saved.chapterId()).isEqualTo(chapterId);
        assertThat(saved.bookId()).isNull();
        assertThat(saved.resolutionStatus()).isEqualTo("unresolved");
        assertThat(saved.extractionIndex()).isEqualTo(0);

        verify(collectiveMentionRepository).linkMentionToScene(eq(sceneId), eq(saved.id()));
    }

    @Test
    @DisplayName("Skips extracted collectives without aliases")
    void skipsCollectivesWithoutAliases() {
        Scene persistedScene = new Scene(UUID.randomUUID(), 0, 0L, 10L, "ctx", "text", UUID.randomUUID(), null, null, null, null, null);
        TriadAnalysisModels.CollectiveExtraction invalid =
                new TriadAnalysisModels.CollectiveExtraction(
                        List.of("", "   "),
                        "organization",
                        "WeaklyImplied",
                        "No concrete collective actor"
                );

        service.persistExtractedCollectives(
                List.of(persistedScene),
                List.of(new TriadAnalysisModels.SceneCollectiveExtraction(0, List.of(invalid)))
        );

        verify(collectiveMentionRepository, never()).save(any());
        verify(collectiveMentionRepository, never()).linkMentionToScene(any(), any());
    }
}
