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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RelationClaimPersistenceService {

    private final RelationClaimGraphRepository relationClaimRepository;

    @Transactional
    public void persistExtractedRelationClaims(
            List<Scene> persistedScenes,
            List<TriadAnalysisModels.SceneRelationClaimExtraction> sceneExtractions
    ) {
        if (persistedScenes == null || persistedScenes.isEmpty() || sceneExtractions == null || sceneExtractions.isEmpty()) {
            return;
        }

        Map<Integer, Scene> sceneByIndex = persistedScenes.stream()
                .filter(scene -> scene.getSceneIndex() != null && scene.getEventId() != null)
                .collect(Collectors.toMap(Scene::getSceneIndex, scene -> scene, (a, b) -> a));

        for (TriadAnalysisModels.SceneRelationClaimExtraction sceneExtraction : sceneExtractions) {
            Scene scene = sceneByIndex.get(sceneExtraction.sceneIndex());
            if (scene == null || sceneExtraction.relationClaims() == null || sceneExtraction.relationClaims().isEmpty()) {
                continue;
            }

            UUID sceneId = scene.getEventId();
            UUID chapterId = scene.getChapterId();

            for (int extractionIndex = 0; extractionIndex < sceneExtraction.relationClaims().size(); extractionIndex++) {
                TriadAnalysisModels.RelationClaimExtraction extracted = sceneExtraction.relationClaims().get(extractionIndex);

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
                        "ai-scene-analysis",
                        sceneId,
                        chapterId,
                        null,
                        extractionIndex,
                        "unresolved",
                        null,
                        null
                ));
                relationClaimRepository.linkClaimToScene(sceneId, saved.id());
            }
        }
    }
}
