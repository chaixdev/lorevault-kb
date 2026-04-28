package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.infrastructure.*;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.mention.IndividualMention;
import com.lorevault.api.content.mention.IndividualMentionGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.ingestion.triad.TriadAnalysisModels;
import com.lorevault.api.testutil.builders.PublicationCoordinatesBuilder;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IndividualPersistenceService")
class IndividualPersistenceServiceTest {

    @Mock
    private IndividualMentionGraphRepository individualMentionRepository;

    @InjectMocks
    private IndividualPersistenceService service;

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

        service.persistExtractedIndividuals(List.of(persistedScene), List.of(byScene));

        ArgumentCaptor<IndividualMention> savedCaptor = ArgumentCaptor.forClass(IndividualMention.class);
        verify(individualMentionRepository).save(savedCaptor.capture());
        IndividualMention saved = savedCaptor.getValue();
        assertThat(saved.source()).isEqualTo("ai-scene-analysis");
        assertThat(saved.displayName()).isEqualTo("Nyx");
        assertThat(saved.normalizedName()).isEqualTo("nyx");
        assertThat(saved.aliases()).containsExactly("  Nyx  ", "N.");
        assertThat(saved.activity()).isEqualTo("protagonist");
        assertThat(saved.sceneId()).isEqualTo(sceneId);
        assertThat(saved.chapterId()).isEqualTo(chapterId);
        assertThat(saved.bookId()).isNull();
        assertThat(saved.resolutionStatus()).isEqualTo("unresolved");
        assertThat(saved.extractionIndex()).isEqualTo(0);

        verify(individualMentionRepository).linkMentionToScene(eq(sceneId), eq(saved.id()));
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
        service.persistExtractedIndividuals(List.of(persistedScene), List.of(byScene));

        verify(individualMentionRepository, never()).save(any());
        verify(individualMentionRepository, never()).linkMentionToScene(any(), any());
    }
}
