package com.lorevault.api.ingestion.infrastructure;

import com.lorevault.api.content.relation.RelationClaim;
import com.lorevault.api.content.relation.RelationClaimGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.ingestion.triad.TriadAnalysisModels;
import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogService;
import com.lorevault.catalog.RelationQuery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelationClaimPersistenceService {

    private static final String SOURCE = "ai-scene-analysis";

    private final RelationClaimGraphRepository relationClaimRepository;
    private final RelationCatalogService catalogService;

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

                // Resolve catalog identity
                UUID catalogId = null;
                String definitionKey = extracted.definitionKey();
                try {
                    RelationQuery query = new RelationQuery(
                            extracted.definitionKey(),
                            extracted.relationName(),
                            extracted.subjectKind(),
                            extracted.objectKind(),
                            extracted.relationDescription(),
                            extracted.certainty(),
                            null,  // evidenceReference — not available at extraction time
                            chapterId,
                            sceneId,
                            Optional.ofNullable(extracted.evidence()).map(e -> e.length() > 500 ? e.substring(0, 500) : e)
                    );
                    RelationCatalogDefinition definition = catalogService.resolve(query);
                    catalogId = definition.id().value();
                    definitionKey = definition.definitionKey();
                } catch (UnsupportedOperationException | DataAccessException e) {
                    log.warn("[RELATION_CLAIM_PERSIST] Catalog resolution failed, persisting without catalog identity: {}", e.getMessage());
                    // catalogId remains null, definitionKey keeps the extracted value
                }

                RelationClaim saved = relationClaimRepository.save(new RelationClaim(
                        UUID.randomUUID(),
                        extracted.relationName(),
                        extracted.relationDescription(),
                        catalogId,
                        definitionKey,
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