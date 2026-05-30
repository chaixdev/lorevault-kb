package com.lorevault.api.graph.individual.persistence;

import com.lorevault.api.library.chapter.Chapter;
import com.lorevault.api.graph.event.scene.Scene;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.triad.TriadAnalysisModels;
import com.lorevault.api.testutil.builders.PublicationCoordinatesBuilder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IndividualPersistenceService")
class IndividualPersistenceServiceTest {

    @Mock
    private IndividualMentionGraphRepository individualMentionRepository;

    @InjectMocks
    private IndividualPersistenceService service;

    private static final StageExecutionContext CTX =
            new StageExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), StageKey.SCENE_SEGMENTATION);

    @Test
    @DisplayName("Persists one IndividualMention per extracted block and links mention by sceneIndex")
    void persistsIndividualsAndLinksMentions() {
        UUID sceneId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        Chapter chapter = Chapter.createStandalone(
                bookId,
                UUID.randomUUID(),
                PublicationCoordinatesBuilder.coordinates()
                        .withUniverse("cosmere")
                        .withSeries("standalone")
                        .withBookTitle("sunlit")
                        .withChapterTitle("chapter-4")
                        .withBookNumber(1)
                        .withChapterNumber(4)
                        .build(),
                "Chapter 4",
                "raw",
                "hash"
        );
        Scene persistedScene = new Scene(sceneId, 3, 0L, 10L, "ctx", "text", chapterId, null, null, null, null, chapter);
        TriadAnalysisModels.IndividualExtraction extracted =
                new TriadAnalysisModels.IndividualExtraction(
                        List.of("  Nyx  ", "N."),
                        "tall",
                        "20s",
                        "protagonist"
                );
        TriadAnalysisModels.SceneIndividualExtraction byScene =
                new TriadAnalysisModels.SceneIndividualExtraction(3, List.of(extracted));

        when(individualMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistExtractedIndividuals(CTX, List.of(persistedScene), List.of(byScene));
    }

    @Test
    @DisplayName("Skips extracted individuals without non-blank alias")
    void skipsIndividualsWithoutAlias() {
        Scene persistedScene = new Scene(UUID.randomUUID(), 0, 0L, 10L, "ctx", "text", UUID.randomUUID(), null, null, null, null, null);
        TriadAnalysisModels.IndividualExtraction invalid =
                new TriadAnalysisModels.IndividualExtraction(
                        List.of(" ", "\t", ""),
                        "",
                        "",
                        ""
                );
        TriadAnalysisModels.SceneIndividualExtraction byScene =
                new TriadAnalysisModels.SceneIndividualExtraction(0, List.of(invalid));
        service.persistExtractedIndividuals(CTX, List.of(persistedScene), List.of(byScene));

        verify(individualMentionRepository, never()).save(any());
        verify(individualMentionRepository, never()).linkMentionToScene(any(), any());
    }
}
