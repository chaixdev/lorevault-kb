package com.lorevault.api.graph.object.persistence;

import com.lorevault.api.common.NameNormalizer;
import com.lorevault.api.graph.event.scene.Scene;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.triad.TriadAnalysisModels;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObjectPersistenceService {

    private static final String SOURCE = "ai-scene-analysis";
    private static final String UNRESOLVED = "unresolved";

    private final ObjectMentionGraphRepository objectMentionRepository;

    @Transactional
    public Map<String, UUID> persistExtractedObjects(
            StageExecutionContext ctx,
            List<Scene> persistedScenes,
            List<TriadAnalysisModels.SceneObjectExtraction> sceneExtractions
    ) {
        Map<String, UUID> mentionIds = new HashMap<>();
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return mentionIds;
        }

        log.info("[OBJECT_PERSIST] Persisting {} extractions from {} scenes for chapterId={}",
                sceneExtractions.size(), persistedScenes.size(), ctx.chapterId());

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
                if (extracted == null) {
                    continue;
                }
                String displayName = firstNonBlankAlias(extracted);
                if (displayName == null) {
                    continue;
                }

                UUID chapterId = scene.getChapterId();
                UUID sceneId = scene.getEventId();
                ObjectMention saved = objectMentionRepository.save(new ObjectMention(
                        UUID.randomUUID(),
                        SOURCE,
                        displayName,
                        NameNormalizer.normalize(displayName),
                        extracted.aliases(),
                        extracted.type(),
                        extracted.material(),
                        extracted.purpose(),
                        extracted.description(),
                        ctx.stageId(),
                        sceneId,
                        chapterId,
                        null,
                        UNRESOLVED,
                        extractionIndex,
                        null,
                        null
                ));
                objectMentionRepository.linkMentionToScene(sceneId, saved.id());

                for (String alias : extracted.aliases()) {
                    String key = NameNormalizer.normalize(alias);
                    if (key != null) {
                        mentionIds.put(key, saved.id());
                    }
                }
            }
        }
        log.info("[OBJECT_PERSIST] Completed: {} mentions persisted", mentionIds.size());
        return mentionIds;
    }

    private String firstNonBlankAlias(TriadAnalysisModels.ObjectExtraction extracted) {
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
        return null;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
