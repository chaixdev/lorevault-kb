package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.infrastructure.*;

import com.lorevault.api.content.mention.LocationMention;
import com.lorevault.api.content.mention.LocationMentionGraphRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationPersistenceService")
class LocationPersistenceServiceTest {

    @Mock
    private LocationMentionGraphRepository locationMentionRepository;

    @InjectMocks
    private LocationPersistenceService service;

    @Test
    @DisplayName("Persists one LocationMention per extracted block and links mention by sceneIndex")
    void persistsLocationsAndLinksMentions() {
        UUID sceneId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Scene persistedScene = new Scene(sceneId, 2, 0L, 10L, "ctx", "text", chapterId, null, null, null, null, null);
        TriadAnalysisModels.LocationExtraction extracted =
                new TriadAnalysisModels.LocationExtraction(
                        "  Urithiru  ",
                        List.of("the tower", "Urithiru"),
                        "city",
                        "Roshar",
                        "ancient tower city"
                );
        TriadAnalysisModels.SceneLocationExtraction byScene =
                new TriadAnalysisModels.SceneLocationExtraction(2, List.of(extracted));

        when(locationMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistExtractedLocations(List.of(persistedScene), List.of(byScene));

        ArgumentCaptor<LocationMention> savedCaptor = ArgumentCaptor.forClass(LocationMention.class);
        verify(locationMentionRepository).save(savedCaptor.capture());
        LocationMention saved = savedCaptor.getValue();
        assertThat(saved.source()).isEqualTo("ai-scene-analysis");
        assertThat(saved.displayName()).isEqualTo("Urithiru");
        assertThat(saved.normalizedName()).isEqualTo("urithiru");
        assertThat(saved.aliases()).containsExactly("the tower", "Urithiru");
        assertThat(saved.kind()).isEqualTo("city");
        assertThat(saved.region()).isEqualTo("Roshar");
        assertThat(saved.description()).isEqualTo("ancient tower city");
        assertThat(saved.sceneId()).isEqualTo(sceneId);
        assertThat(saved.chapterId()).isEqualTo(chapterId);
        assertThat(saved.bookId()).isNull();
        assertThat(saved.resolutionStatus()).isEqualTo("unresolved");
        assertThat(saved.extractionIndex()).isEqualTo(0);

        verify(locationMentionRepository).linkMentionToScene(eq(sceneId), eq(saved.id()));
    }

    @Test
    @DisplayName("Falls back to first non blank alias when primary name is missing")
    void fallsBackToAliasWhenPrimaryNameMissing() {
        Scene persistedScene = new Scene(UUID.randomUUID(), 0, 0L, 10L, "ctx", "text", UUID.randomUUID(), null, null, null, null, null);
        TriadAnalysisModels.LocationExtraction extracted =
                new TriadAnalysisModels.LocationExtraction(
                        " ",
                        List.of("", " Kharbranth "),
                        "city",
                        null,
                        null
                );

        when(locationMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistExtractedLocations(
                List.of(persistedScene),
                List.of(new TriadAnalysisModels.SceneLocationExtraction(0, List.of(extracted)))
        );

        ArgumentCaptor<LocationMention> savedCaptor = ArgumentCaptor.forClass(LocationMention.class);
        verify(locationMentionRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().displayName()).isEqualTo("Kharbranth");
        assertThat(savedCaptor.getValue().normalizedName()).isEqualTo("kharbranth");
    }
}
