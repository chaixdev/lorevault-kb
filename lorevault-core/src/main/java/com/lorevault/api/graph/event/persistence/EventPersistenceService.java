package com.lorevault.api.graph.event.persistence;

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
public class EventPersistenceService {

    private static final String SOURCE = "ai-scene-analysis";
    private static final String UNRESOLVED = "unresolved";

    private final EventMentionGraphRepository eventMentionRepository;

    @Transactional
    public Map<String, UUID> persistExtractedEvents(
            StageExecutionContext ctx,
            List<Scene> persistedScenes,
            List<TriadAnalysisModels.SceneEventExtraction> sceneExtractions
    ) {
        Map<String, UUID> mentionIds = new HashMap<>();
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return mentionIds;
        }

        log.info("[EVENT_PERSIST] Persisting {} extractions from {} scenes for chapterId={}",
                sceneExtractions.size(), persistedScenes.size(), ctx.chapterId());

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        for (TriadAnalysisModels.SceneEventExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.events() == null || sceneExtraction.events().isEmpty()) {
                continue;
            }

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.events().size(); extractionIndex++) {
                TriadAnalysisModels.EventExtraction extracted = sceneExtraction.events().get(extractionIndex);
                if (extracted == null) {
                    continue;
                }
                String displayName = normalizeText(extracted.name());
                if (displayName == null) {
                    continue;
                }

                UUID chapterId = scene.getChapterId();
                UUID sceneId = scene.getEventId();
                EventMention saved = eventMentionRepository.save(new EventMention(
                        UUID.randomUUID(),
                        SOURCE,
                        displayName,
                        NameNormalizer.normalize(displayName),
                        List.of(displayName),
                        normalizeText(extracted.eventType()),
                        normalizeText(extracted.description()),
                        normalizeText(extracted.temporalType()),
                        normalizeText(extracted.certainty()),
                        normalizeText(extracted.evidence()),
                        ctx.stageId(),
                        sceneId,
                        chapterId,
                        null,
                        UNRESOLVED,
                        extractionIndex,
                        null,
                        null
                ));
                eventMentionRepository.linkMentionToScene(sceneId, saved.id());

                String key = NameNormalizer.normalize(displayName);
                if (key != null) {
                    mentionIds.put(key, saved.id());
                }
            }
        }
        log.info("[EVENT_PERSIST] Completed: {} mentions persisted", mentionIds.size());
        return mentionIds;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
