package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.content.relation.RelationClaim;
import com.lorevault.api.content.relation.RelationClaimGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.ingestion.triad.TriadAnalysisModels;
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
public class RelationClaimPersistenceService {

    private static final String SOURCE = "ai-scene-analysis";
    private static final String UNRESOLVED = "unresolved";

    private final RelationClaimGraphRepository relationClaimRepository;

    @Transactional
    public void persistExtractedRelationClaims(
            List<Scene> persistedScenes,
            List<TriadAnalysisModels.SceneRelationClaimExtraction> sceneExtractions
    ) {
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            log.debug("[RELATION_CLAIM_PERSIST] Skipping: no extraction data");
            return;
        }

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        int totalPersisted = 0;
        int totalSkipped = 0;

        for (TriadAnalysisModels.SceneRelationClaimExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.relationClaims() == null || sceneExtraction.relationClaims().isEmpty()) {
                continue;
            }

            UUID sceneId = scene.getEventId();
            UUID chapterId = scene.getChapterId();

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.relationClaims().size(); extractionIndex++) {
                TriadAnalysisModels.RelationClaimExtraction extracted = sceneExtraction.relationClaims().get(extractionIndex);

                // Idempotency guard: skip if a claim with the same deduplication key already exists
                long existing = relationClaimRepository.countBySceneIdAndExtractionIndexAndRelationName(
                        sceneId, extractionIndex, extracted.relationName());
                if (existing > 0) {
                    totalSkipped++;
                    log.debug("[RELATION_CLAIM_PERSIST] Skipping duplicate: sceneId={}, extractionIndex={}, relationName={}",
                            sceneId, extractionIndex, extracted.relationName());
                    continue;
                }

                RelationClaim saved = relationClaimRepository.save(new RelationClaim(
                        UUID.randomUUID(),
                        extracted.relationName(),
                        extracted.relationDescription(),
                        extracted.provisionalRelTypeId(),
                        extracted.subjectKind(),
                        extracted.subjectName(),
                        extracted.objectKind(),
                        extracted.objectName(),
                        extracted.certainty(),
                        extracted.evidence(),
                        SOURCE,
                        sceneId,
                        chapterId,
                        null,
                        extractionIndex,
                        UNRESOLVED,
                        null,
                        null
                ));
                relationClaimRepository.linkClaimToScene(sceneId, saved.id());
                totalPersisted++;
            }
        }

        log.info("[RELATION_CLAIM_PERSIST] Completed: persisted {} claims, skipped {} duplicates — chapterId={}",
                totalPersisted, totalSkipped,
                sceneByIndex.values().stream().map(Scene::getChapterId).findFirst().orElse(null));
    }
}