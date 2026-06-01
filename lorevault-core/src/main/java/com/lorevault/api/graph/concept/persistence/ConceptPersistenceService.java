package com.lorevault.api.graph.concept.persistence;

import com.lorevault.api.common.NameNormalizer;
import com.lorevault.api.graph.event.scene.Scene;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.triad.TriadAnalysisModels;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ConceptPersistenceService {

    private static final String SOURCE = "ai-scene-analysis";
    private static final String UNRESOLVED = "unresolved";

    private final ConceptMentionGraphRepository conceptMentionRepository;

    public ConceptPersistenceService(ConceptMentionGraphRepository conceptMentionRepository) {
        this.conceptMentionRepository = conceptMentionRepository;
    }

    @Transactional
    public Map<String, UUID> persistExtractedConcepts(
            StageExecutionContext ctx,
            List<Scene> persistedScenes,
            List<TriadAnalysisModels.SceneConceptExtraction> sceneExtractions
    ) {
        Map<String, UUID> mentionIds = new HashMap<>();
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return mentionIds;
        }

        log.info("[CONCEPT_PERSIST] Starting concept persistence: stageId={}, sceneCount={}, extractionCount={}",
                ctx.stageId(), persistedScenes != null ? persistedScenes.size() : 0,
                sceneExtractions != null ? sceneExtractions.size() : 0);

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        for (TriadAnalysisModels.SceneConceptExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null) {
                log.warn("[CONCEPT_PERSIST] No persisted scene found for sceneIndex={}", sceneExtraction.sceneIndex());
                continue;
            }
            if (sceneExtraction.concepts() == null || sceneExtraction.concepts().isEmpty()) {
                continue;
            }

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.concepts().size(); extractionIndex++) {
                TriadAnalysisModels.ConceptExtraction extracted = sceneExtraction.concepts().get(extractionIndex);
                if (extracted == null) {
                    continue;
                }
                String displayName = firstNonBlankAlias(extracted);
                if (displayName == null) {
                    continue;
                }

                UUID chapterId = scene.getChapterId();
                UUID sceneId = scene.getEventId();
                // TODO: resolve conceptType through ObjectKindCatalogService when catalog ships
                ConceptMention saved = conceptMentionRepository.save(new ConceptMention(
                        UUID.randomUUID(),
                        SOURCE,
                        displayName,
                        NameNormalizer.normalize(displayName),
                        extracted.aliases(),
                        extracted.conceptType(),
                        extracted.description(),
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
                if (extracted.conceptType() == null || extracted.conceptType().isBlank()) {
                    log.warn("[CONCEPT_PERSIST] conceptType missing for mention '{}'", displayName);
                }

                conceptMentionRepository.linkMentionToScene(sceneId, saved.id());

                for (String alias : extracted.aliases()) {
                    String key = NameNormalizer.normalize(alias);
                    if (key != null) {
                        mentionIds.put(key, saved.id());
                    }
                }
            }
        }
        log.info("[CONCEPT_PERSIST] Completed: {} concept mentions persisted", mentionIds.size());
        return mentionIds;
    }

    private String firstNonBlankAlias(TriadAnalysisModels.ConceptExtraction extracted) {
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

}
