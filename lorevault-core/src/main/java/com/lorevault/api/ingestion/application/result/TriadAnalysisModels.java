package com.lorevault.api.ingestion.application.result;

import java.util.List;
import java.util.UUID;

public final class TriadAnalysisModels {

    private TriadAnalysisModels() {
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

    public record EventExtraction(
            String name,
            String eventType,
            String temporalType,
            String certainty,
            String evidence
    ) {
    }

    public record SceneIndividualExtraction(int sceneIndex, List<IndividualExtraction> individuals) {
    }

    public record SceneLocationExtraction(int sceneIndex, List<LocationExtraction> locations) {
    }

    public record SceneEventExtraction(int sceneIndex, List<EventExtraction> events) {
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

    public record SceneRelationshipOutcome(
            List<SceneRelationshipAnalysis> triadAnalyses,
            List<SceneIndividualExtraction> sceneIndividualExtractions,
            List<SceneLocationExtraction> sceneLocationExtractions,
            List<SceneEventExtraction> sceneEventExtractions
    ) {
        public SceneRelationshipOutcome(
                List<SceneRelationshipAnalysis> triadAnalyses,
                List<SceneIndividualExtraction> sceneIndividualExtractions,
                List<SceneLocationExtraction> sceneLocationExtractions
        ) {
            this(triadAnalyses, sceneIndividualExtractions, sceneLocationExtractions, List.of());
        }
    }
}
