package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.content.entities.ObjectMention;
import com.lorevault.api.content.entities.ObjectMentionGraphRepository;
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
public class ObjectPersistenceService {

    private static final String SOURCE = "ai-scene-analysis";
    private static final String UNRESOLVED = "unresolved";

    private final ObjectMentionGraphRepository objectMentionRepository;

    @Transactional
    public void persistExtractedObjects(
            List<Scene> persistedScenes,
            List<TriadAnalysisModels.SceneObjectExtraction> sceneExtractions
    ) {
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return;
        }

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        for (TriadAnalysisModels.SceneObjectExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.objects() == null || sceneExtraction.objects().isEmpty()) {
                continue;
            }

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.objects().size(); extractionIndex++) {
                TriadAnalysisModels.ObjectExtraction extracted = sceneExtraction.objects().get(extractionIndex);
                String displayName = chooseDisplayName(extracted);
                if (displayName == null) {
                    continue;
                }

                UUID chapterId = scene.getChapterId();
                UUID sceneId = scene.getEventId();
                ObjectMention saved = objectMentionRepository.save(new ObjectMention(
                        UUID.randomUUID(),
                        SOURCE,
                        displayName,
                        normalizeName(displayName),
                        extracted.aliases(),
                        extracted.type(),
                        extracted.material(),
                        extracted.purpose(),
                        extracted.description(),
                        sceneId,
                        chapterId,
                        null,
                        UNRESOLVED,
                        extractionIndex,
                        null,
                        null
                ));
                objectMentionRepository.linkMentionToScene(sceneId, saved.id());
            }
        }
    }

    private String chooseDisplayName(TriadAnalysisModels.ObjectExtraction extracted) {
        if (extracted == null) {
            return null;
        }
        if (extracted.aliases() != null) {
            for (String alias : extracted.aliases()) {
                if (alias != null && !alias.isBlank()) {
                    return alias.trim();
                }
            }
        }
        return normalizeText(extracted.type());
    }

    private String normalizeName(String displayName) {
        return displayName == null ? null : displayName.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
