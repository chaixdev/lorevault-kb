package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.content.entities.IndividualMention;
import com.lorevault.api.content.entities.IndividualMentionGraphRepository;
import com.lorevault.api.content.entities.Scene;
import com.lorevault.api.ingestion.domain.triad.TriadAnalysisModels;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IndividualPersistenceService {

    private static final String SOURCE = "ai-scene-analysis";
    private static final String UNRESOLVED = "unresolved";

    private final IndividualMentionGraphRepository individualMentionRepository;

    @Transactional
    public void persistExtractedIndividuals(
            List<Scene> persistedScenes,
            List<TriadAnalysisModels.SceneIndividualExtraction> sceneExtractions
    ) {
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return;
        }

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        for (TriadAnalysisModels.SceneIndividualExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.individuals() == null || sceneExtraction.individuals().isEmpty()) {
                continue;
            }

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.individuals().size(); extractionIndex++) {
                TriadAnalysisModels.IndividualExtraction extracted = sceneExtraction.individuals().get(extractionIndex);
                String displayName = firstNonBlankAlias(extracted.aliases());
                if (displayName == null) {
                    continue;
                }

                UUID chapterId = scene.getChapterId();
                UUID sceneId = scene.getEventId();
                IndividualMention saved = individualMentionRepository.save(new IndividualMention(
                        UUID.randomUUID(),
                        SOURCE,
                        displayName,
                        normalizeName(displayName),
                        extracted.aliases(),
                        extracted.activity(),
                        extracted.age(),
                        extracted.physicalProperties(),
                        sceneId,
                        chapterId,
                        null,
                        UNRESOLVED,
                        extractionIndex,
                        null,
                        null
                ));
                individualMentionRepository.linkMentionToScene(sceneId, saved.id());
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

    private String normalizeName(String displayName) {
        return displayName == null ? null : displayName.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
