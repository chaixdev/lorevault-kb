package com.lorevault.api.ingestion;

import com.lorevault.api.ai.TriadOrchestrationService;
import com.lorevault.api.content.EventMention;
import com.lorevault.api.content.EventMentionGraphRepository;
import com.lorevault.api.content.Scene;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventPersistenceService {

    private static final String SOURCE = "ai-scene-analysis";
    private static final String UNRESOLVED = "unresolved";

    private final EventMentionGraphRepository eventMentionRepository;

    public EventPersistenceService(EventMentionGraphRepository eventMentionRepository) {
        this.eventMentionRepository = eventMentionRepository;
    }

    @Transactional
    public void persistExtractedEvents(
            List<Scene> persistedScenes,
            List<TriadOrchestrationService.TriadSceneEventExtraction> sceneExtractions
    ) {
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return;
        }

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        for (TriadOrchestrationService.TriadSceneEventExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.events() == null || sceneExtraction.events().isEmpty()) {
                continue;
            }

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.events().size(); extractionIndex++) {
                TriadOrchestrationService.TriadEventExtraction extracted = sceneExtraction.events().get(extractionIndex);
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
                        normalizeName(displayName),
                        List.of(displayName),
                        normalizeText(extracted.eventType()),
                        normalizeText(extracted.temporalType()),
                        normalizeText(extracted.certainty()),
                        normalizeText(extracted.evidence()),
                        sceneId,
                        chapterId,
                        null,
                        UNRESOLVED,
                        extractionIndex,
                        null,
                        null
                ));
                eventMentionRepository.linkMentionToScene(sceneId, saved.id());
            }
        }
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
