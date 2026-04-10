package com.lorevault.api.ingestion;

import com.lorevault.api.ai.TriadOrchestrationService;
import com.lorevault.api.content.Individual;
import com.lorevault.api.content.IndividualGraphRepository;
import com.lorevault.api.content.Scene;
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
    private IndividualGraphRepository individualRepository;

    @InjectMocks
    private IndividualPersistenceService service;

    @Test
    @DisplayName("Persists one Individual per extracted block and links mention by sceneIndex")
    void persistsIndividualsAndLinksMentions() {
        UUID sceneId = UUID.randomUUID();
        Scene persistedScene = new Scene(sceneId, 3, 0L, 10L, "ctx", "text", UUID.randomUUID(), null, null, null, null, null);
        TriadOrchestrationService.TriadIndividualExtraction extracted =
                new TriadOrchestrationService.TriadIndividualExtraction(
                        List.of("  Nyx  ", "N."),
                        "tall",
                        "20s",
                        "protagonist"
                );
        TriadOrchestrationService.TriadSceneIndividualExtraction byScene =
                new TriadOrchestrationService.TriadSceneIndividualExtraction(3, List.of(extracted));

        when(individualRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistExtractedIndividuals(List.of(persistedScene), List.of(byScene));

        ArgumentCaptor<Individual> savedCaptor = ArgumentCaptor.forClass(Individual.class);
        verify(individualRepository).save(savedCaptor.capture());
        Individual saved = savedCaptor.getValue();
        assertThat(saved.provisional()).isTrue();
        assertThat(saved.source()).isEqualTo("ai-pass2");
        assertThat(saved.displayName()).isEqualTo("Nyx");
        assertThat(saved.aliases()).containsExactly("  Nyx  ", "N.");

        verify(individualRepository).linkMentionedIndividual(eq(sceneId), eq(saved.id()));
    }

    @Test
    @DisplayName("Skips extracted individuals without non-blank alias")
    void skipsIndividualsWithoutAlias() {
        Scene persistedScene = new Scene(UUID.randomUUID(), 0, 0L, 10L, "ctx", "text", UUID.randomUUID(), null, null, null, null, null);
        TriadOrchestrationService.TriadIndividualExtraction invalid =
                new TriadOrchestrationService.TriadIndividualExtraction(
                        List.of(" ", "\t", ""),
                        "",
                        "",
                        ""
                );
        TriadOrchestrationService.TriadSceneIndividualExtraction byScene =
                new TriadOrchestrationService.TriadSceneIndividualExtraction(0, List.of(invalid));

        service.persistExtractedIndividuals(List.of(persistedScene), List.of(byScene));

        verify(individualRepository, never()).save(any());
        verify(individualRepository, never()).linkMentionedIndividual(any(), any());
    }
}
