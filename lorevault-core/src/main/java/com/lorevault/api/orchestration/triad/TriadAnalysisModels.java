package com.lorevault.api.orchestration.triad;

import java.util.List;
import java.util.UUID;

import lombok.Builder;

public final class TriadAnalysisModels {

    private TriadAnalysisModels() {
    }

    public sealed interface SceneExtraction permits
            SceneIndividualExtraction,
            SceneLocationExtraction,
            SceneObjectExtraction,
            SceneCollectiveExtraction,
            SceneConceptExtraction,
            SceneEventExtraction,
            SceneRelationClaimExtraction {
        int sceneIndex();
    }

    public record IndividualExtraction(
            List<String> aliases,
            String physicalProperties,
            String age,
            String activity
    ) {
    }

    public record LocationExtraction(
            String primaryName,
            List<String> aliases,
            String kind,
            String region,
            String description
    ) {
    }

    public record ObjectExtraction(
            List<String> aliases,
            String type,
            String material,
            String purpose,
            String description
    ) {
    }

    public record CollectiveExtraction(
            List<String> aliases,
            String collectiveType,
            String certainty,
            String evidence
    ) {
    }

    public record ConceptExtraction(
            List<String> aliases,
            String conceptType,
            String description,
            String certainty,
            String evidence
    ) {
    }

    public record EventExtraction(
            String name,
            String eventType,
            String description,
            String temporalType,
            String certainty,
            String evidence
    ) {
    }

    public record SceneIndividualExtraction(int sceneIndex, List<IndividualExtraction> individuals) implements SceneExtraction {
    }

    public record SceneLocationExtraction(int sceneIndex, List<LocationExtraction> locations) implements SceneExtraction {
    }

    public record SceneObjectExtraction(int sceneIndex, List<ObjectExtraction> objects) implements SceneExtraction {
    }

    public record SceneCollectiveExtraction(int sceneIndex, List<CollectiveExtraction> collectives) implements SceneExtraction {
    }

    public record SceneConceptExtraction(int sceneIndex, List<ConceptExtraction> concepts) implements SceneExtraction {
    }

    public record SceneEventExtraction(int sceneIndex, List<EventExtraction> events) implements SceneExtraction {
    }

    public record RelationClaimExtraction(
            String definitionKey,
            String subjectKind,
            String subjectName,
            String relationName,
            String relationDescription,
            String objectKind,
            String objectName,
            String certainty,
            String evidence
    ) {
    }

    public record SceneRelationClaimExtraction(int sceneIndex, List<RelationClaimExtraction> relationClaims) implements SceneExtraction {
    }

    public record SceneRelationshipAnalysis(
            UUID previousSceneId,
            UUID currentSceneId,
            UUID nextSceneId,
            Integer previousSceneIndex,
            Integer currentSceneIndex,
            Integer nextSceneIndex,
            String timelineMarker,
            String prevToCurrType,
            String prevToCurrCertainty,
            String prevToCurrEvidence,
            String currToNextType,
            String currToNextCertainty,
            String currToNextEvidence,
            String currVsPrevInverted
    ) {
    }

    @Builder
    public record SceneRelationshipOutcome(
            List<SceneRelationshipAnalysis> triadAnalyses,
            List<SceneIndividualExtraction> sceneIndividualExtractions,
            List<SceneCollectiveExtraction> sceneCollectiveExtractions,
            List<SceneConceptExtraction> sceneConceptExtractions,
            List<SceneObjectExtraction> sceneObjectExtractions,
            List<SceneLocationExtraction> sceneLocationExtractions,
            List<SceneEventExtraction> sceneEventExtractions,
            List<SceneRelationClaimExtraction> sceneRelationClaimExtractions
    ) {
    }
}
