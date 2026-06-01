package com.lorevault.api.graph.relation;

import com.lorevault.api.common.NameNormalizer;
import com.lorevault.api.graph.event.scene.Scene;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.triad.TriadAnalysisModels;
import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogService;
import com.lorevault.catalog.RelationQuery;
import java.util.List;
import java.util.Map;
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
            StageExecutionContext ctx,
            List<Scene> persistedScenes,
            List<TriadAnalysisModels.SceneRelationClaimExtraction> sceneExtractions,
            UUID bookId,
            Map<String, UUID> individualIds,
            Map<String, UUID> collectiveIds,
            Map<String, UUID> conceptIds,
            Map<String, UUID> objectIds,
            Map<String, UUID> locationIds,
            Map<String, UUID> eventIds
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
        int totalLinked = 0;
        int totalUnlinked = 0;

        for (TriadAnalysisModels.SceneRelationClaimExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.relationClaims() == null || sceneExtraction.relationClaims().isEmpty()) {
                continue;
            }

            UUID sceneId = scene.getEventId();
            UUID chapterId = scene.getChapterId();

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.relationClaims().size(); extractionIndex++) {
                TriadAnalysisModels.RelationClaimExtraction extracted = sceneExtraction.relationClaims().get(extractionIndex);

                // Idempotency guard: skip if a claim with the same content identity already exists
                long existing = relationClaimRepository.countBySceneIdAndContentIdentity(
                        sceneId, extracted.subjectName(), extracted.relationName(), extracted.objectName());
                if (existing > 0) {
                    totalSkipped++;
                    log.debug("[RELATION_CLAIM_PERSIST] Skipping duplicate: sceneId={}, subjectName={}, relationName={}, objectName={}",
                            sceneId, extracted.subjectName(), extracted.relationName(), extracted.objectName());
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
                            extracted.certainty()
                    );
                    RelationCatalogDefinition definition = catalogService.resolve(query);
                    catalogId = definition.id().value();
                    definitionKey = definition.definitionKey();
                } catch (UnsupportedOperationException | DataAccessException e) {
                    log.warn("[RELATION_CLAIM_PERSIST] Catalog resolution failed, persisting without catalog identity: {}", e.getMessage());
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
                        ctx.stageId(),
                        sceneId,
                        chapterId,
                        bookId,
                        extractionIndex,
                        null,
                        null
                ));
                relationClaimRepository.linkClaimToScene(sceneId, saved.id());

                // Layer 1: link to subject mention node via in-memory lookup
                UUID subjectMentionId = resolveMentionId(
                        extracted.subjectKind(), extracted.subjectName(),
                        individualIds, collectiveIds, conceptIds, objectIds, locationIds, eventIds);
                if (subjectMentionId != null) {
                    relationClaimRepository.linkSubjectMention(saved.id(), subjectMentionId);
                    totalLinked++;
                } else {
                    totalUnlinked++;
                }

                // Layer 1: link to object mention node via in-memory lookup
                UUID objectMentionId = resolveMentionId(
                        extracted.objectKind(), extracted.objectName(),
                        individualIds, collectiveIds, conceptIds, objectIds, locationIds, eventIds);
                if (objectMentionId != null) {
                    relationClaimRepository.linkObjectMention(saved.id(), objectMentionId);
                    totalLinked++;
                } else {
                    totalUnlinked++;
                }

                totalPersisted++;
            }
        }

        log.info("[RELATION_CLAIM_PERSIST] Completed: persisted {} claims, skipped {} duplicates, linked {} edges, unlinked {} refs — chapterId={}",
                totalPersisted, totalSkipped, totalLinked, totalUnlinked,
                sceneByIndex.values().stream().map(Scene::getChapterId).findFirst().orElse(null));
    }

    /**
     * Resolve a mention ID from the in-memory maps by entity kind and normalized name.
     * Events are deferred to Phase 4; the eventIds map is collected but not routed here.
     */
    private UUID resolveMentionId(
            String kind, String name,
            Map<String, UUID> individualIds,
            Map<String, UUID> collectiveIds,
            Map<String, UUID> conceptIds,
            Map<String, UUID> objectIds,
            Map<String, UUID> locationIds,
            Map<String, UUID> eventIds
    ) {
        if (kind == null || name == null) {
            return null;
        }
        String key = NameNormalizer.normalize(name);
        if (key == null) {
            return null;
        }
        Map<String, UUID> map = switch (kind) {
            case "Individual" -> individualIds;
            case "Collective" -> collectiveIds;
            case "Concept" -> conceptIds;
            case "Object" -> objectIds;
            case "Location" -> locationIds;
            // Event: deferred to Phase 4 (collected but not routed)
            // TODO: Event claim-entity linking (Phase 4)
            default -> null;
        };
        if (map == null) {
            return null;
        }
        UUID id = map.get(key);
        if (id == null) {
            log.debug("[RELATION_CLAIM_PERSIST] No mention found for kind={}, name={}", kind, name);
        }
        return id;
    }
}
