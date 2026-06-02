package com.lorevault.api.graph.object.persistence;

import com.lorevault.api.graph.event.scene.Scene;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.triad.TriadAnalysisModels;

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
@DisplayName("ObjectPersistenceService")
class ObjectPersistenceServiceTest {

    @Mock
    private ObjectMentionGraphRepository objectMentionRepository;

    @InjectMocks
    private ObjectPersistenceService service;

    private static final StageExecutionContext CTX =
            new StageExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), StageKey.SCENE_SEGMENTATION);

    @Test
    @DisplayName("Persists one ObjectMention per extracted block and links mention by sceneIndex")
    void persistsObjectsAndLinksMentions() {
        UUID sceneId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Scene persistedScene = new Scene(sceneId, 1, 0L, 10L, "ctx", "text", chapterId, null, null, null, null);
        TriadAnalysisModels.ObjectExtraction extracted =
                new TriadAnalysisModels.ObjectExtraction(
                        List.of("  Nightblood  "),
                        "sentient sword",
                        "awakened steel",
                        "destroy evil",
                        "An impossibly dangerous blade"
                );
        TriadAnalysisModels.SceneObjectExtraction byScene =
                new TriadAnalysisModels.SceneObjectExtraction(1, List.of(extracted));

        when(objectMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistExtractedObjects(CTX, List.of(persistedScene), List.of(byScene));

        ArgumentCaptor<ObjectMention> savedCaptor = ArgumentCaptor.forClass(ObjectMention.class);
        verify(objectMentionRepository).save(savedCaptor.capture());
        ObjectMention saved = savedCaptor.getValue();
        assertThat(saved.source()).isEqualTo("ai-scene-analysis");
        assertThat(saved.displayName()).isEqualTo("Nightblood");
        assertThat(saved.normalizedName()).isEqualTo("nightblood");
        assertThat(saved.aliases()).containsExactly("  Nightblood  ");
        assertThat(saved.type()).isEqualTo("sentient sword");
        assertThat(saved.material()).isEqualTo("awakened steel");
        assertThat(saved.purpose()).isEqualTo("destroy evil");
        assertThat(saved.description()).isEqualTo("An impossibly dangerous blade");
        assertThat(saved.sceneId()).isEqualTo(sceneId);
        assertThat(saved.chapterId()).isEqualTo(chapterId);
        assertThat(saved.bookId()).isNull();
        assertThat(saved.resolutionStatus()).isEqualTo("unresolved");
        assertThat(saved.extractionIndex()).isEqualTo(0);

        verify(objectMentionRepository).linkMentionToScene(eq(sceneId), eq(saved.id()));
    }

    @Test
    @DisplayName("Uses first non-blank alias as display name")
    void usesFirstNonBlankAliasAsDisplayName() {
        Scene persistedScene = new Scene(UUID.randomUUID(), 0, 0L, 10L, "ctx", "text", UUID.randomUUID(), null, null, null, null);
        TriadAnalysisModels.ObjectExtraction invalid =
                new TriadAnalysisModels.ObjectExtraction(
                        List.of("", "   ", "  pulse gun  "),
                        null,
                        null,
                        null,
                        null
                );

        when(objectMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistExtractedObjects(
                CTX,
                List.of(persistedScene),
                List.of(new TriadAnalysisModels.SceneObjectExtraction(0, List.of(invalid)))
        );

        ArgumentCaptor<ObjectMention> savedCaptor = ArgumentCaptor.forClass(ObjectMention.class);
        verify(objectMentionRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().displayName()).isEqualTo("pulse gun");
        assertThat(savedCaptor.getValue().normalizedName()).isEqualTo("pulse gun");
    }

    @Test
    @DisplayName("Skips extracted objects without alias or type")
    void skipsObjectsWithoutAliasOrType() {
        Scene persistedScene = new Scene(UUID.randomUUID(), 0, 0L, 10L, "ctx", "text", UUID.randomUUID(), null, null, null, null);
        TriadAnalysisModels.ObjectExtraction invalid =
                new TriadAnalysisModels.ObjectExtraction(
                        List.of("", "   "),
                        " ",
                        null,
                        null,
                        null
                );

        service.persistExtractedObjects(
                CTX,
                List.of(persistedScene),
                List.of(new TriadAnalysisModels.SceneObjectExtraction(0, List.of(invalid)))
        );

        verify(objectMentionRepository, never()).save(any());
        verify(objectMentionRepository, never()).linkMentionToScene(any(), any());
    }
}
