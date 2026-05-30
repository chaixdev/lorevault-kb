package com.lorevault.api.graph.collective.persistence;

import com.lorevault.api.graph.event.scene.Scene;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.triad.TriadAnalysisModels;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectivePersistenceService {

    private static final String SOURCE = "ai-scene-analysis";
    private static final String UNRESOLVED = "unresolved";

    private final CollectiveMentionGraphRepository collectiveMentionRepository;

    @Transactional
    public void persistExtractedCollectives(
            StageExecutionContext ctx,
            List<Scene> persistedScenes,
            List<TriadAnalysisModels.SceneCollectiveExtraction> sceneExtractions
    ) {
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return;
        }

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        for (TriadAnalysisModels.SceneCollectiveExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.collectives() == null || sceneExtraction.collectives().isEmpty()) {
                continue;
            }

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.collectives().size(); extractionIndex++) {
                TriadAnalysisModels.CollectiveExtraction extracted = sceneExtraction.collectives().get(extractionIndex);
                String displayName = chooseDisplayName(extracted);
                if (displayName == null) {
                    continue;
                }

                UUID chapterId = scene.getChapterId();
                UUID sceneId = scene.getEventId();
                CollectiveMention saved = collectiveMentionRepository.save(new CollectiveMention(
                        UUID.randomUUID(),
                        SOURCE,
                        displayName,
                        normalizeName(displayName),
                        extracted.aliases(),
                        extracted.collectiveType(),
                        extracted.certainty(),
                        extracted.evidence(),
                        ctx.stageId(),
                        sceneId,
                        chapterId,
                        null,
                        UNRESOLVED,
                        extractionIndex,
                        null,
                        null
                ));
                collectiveMentionRepository.linkMentionToScene(sceneId, saved.id());
            }
        }
    }

    private String chooseDisplayName(TriadAnalysisModels.CollectiveExtraction extracted) {
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

    private String normalizeName(String displayName) {
        return displayName == null ? null : displayName.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
