package com.lorevault.api.ingestion;

import com.lorevault.api.ai.TriadOrchestrationService;
import com.lorevault.api.content.Individual;
import com.lorevault.api.content.IndividualGraphRepository;
import com.lorevault.api.content.Scene;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndividualPersistenceService {

    private final IndividualGraphRepository individualRepository;

    public IndividualPersistenceService(IndividualGraphRepository individualRepository) {
        this.individualRepository = individualRepository;
    }

    @Transactional
    public void persistExtractedIndividuals(
            List<Scene> persistedScenes,
            List<TriadOrchestrationService.TriadSceneIndividualExtraction> sceneExtractions
    ) {
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return;
        }

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        for (TriadOrchestrationService.TriadSceneIndividualExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.individuals() == null || sceneExtraction.individuals().isEmpty()) {
                continue;
            }

            for (TriadOrchestrationService.TriadIndividualExtraction extracted : sceneExtraction.individuals()) {
                String displayName = firstNonBlankAlias(extracted.aliases());
                if (displayName == null) {
                    continue;
                }

                Individual saved = individualRepository.save(new Individual(
                        UUID.randomUUID(),
                        true,
                        "ai-pass2",
                        displayName,
                        extracted.aliases(),
                        extracted.description(),
                        extracted.age(),
                        extracted.physicalProperties(),
                        null,
                        null
                ));
                individualRepository.linkMentionedIndividual(scene.getEventId(), saved.id());
            }
        }
    }

    private String firstNonBlankAlias(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
                return alias.trim();
            }
        }
        return null;
    }
}
