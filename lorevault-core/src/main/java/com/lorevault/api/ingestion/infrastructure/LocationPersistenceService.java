package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.ai.application.SceneRelationshipAnalysisService;
import com.lorevault.api.content.entities.LocationMention;
import com.lorevault.api.content.entities.LocationMentionGraphRepository;
import com.lorevault.api.content.entities.Scene;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationPersistenceService {

    private static final String SOURCE = "ai-scene-analysis";
    private static final String UNRESOLVED = "unresolved";

    private final LocationMentionGraphRepository locationMentionRepository;

    @Transactional
    public void persistExtractedLocations(
            List<Scene> persistedScenes,
            List<SceneRelationshipAnalysisService.TriadSceneLocationExtraction> sceneExtractions
    ) {
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return;
        }

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        for (SceneRelationshipAnalysisService.TriadSceneLocationExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.locations() == null || sceneExtraction.locations().isEmpty()) {
                continue;
            }

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.locations().size(); extractionIndex++) {
                SceneRelationshipAnalysisService.TriadLocationExtraction extracted = sceneExtraction.locations().get(extractionIndex);
                String displayName = chooseDisplayName(extracted);
                if (displayName == null) {
                    continue;
                }

                UUID chapterId = scene.getChapterId();
                UUID sceneId = scene.getEventId();
                LocationMention saved = locationMentionRepository.save(new LocationMention(
                        UUID.randomUUID(),
                        SOURCE,
                        displayName,
                        normalizeName(displayName),
                        extracted.aliases(),
                        extracted.kind(),
                        extracted.region(),
                        extracted.description(),
                        sceneId,
                        chapterId,
                        null,
                        UNRESOLVED,
                        extractionIndex,
                        null,
                        null
                ));
                locationMentionRepository.linkMentionToScene(sceneId, saved.id());
            }
        }
    }

    private String chooseDisplayName(SceneRelationshipAnalysisService.TriadLocationExtraction extracted) {
        if (extracted == null) {
            return null;
        }
        if (extracted.primaryName() != null && !extracted.primaryName().isBlank()) {
            return extracted.primaryName().trim();
        }
        if (extracted.aliases() == null || extracted.aliases().isEmpty()) {
            return null;
        }
        for (String alias : extracted.aliases()) {
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
