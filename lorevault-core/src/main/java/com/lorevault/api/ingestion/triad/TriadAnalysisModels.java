package com.lorevault.api.ingestion.triad;

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

    public record EventExtraction(
            String name,
            String eventType,
            String description,
            String temporalType,
            String certainty,
            String evidence
    ) {
    }

    public record SceneIndividualExtraction(int sceneIndex, List<IndividualExtraction> individuals) {
    }

    public record SceneLocationExtraction(int sceneIndex, List<LocationExtraction> locations) {
    }

    public record SceneObjectExtraction(int sceneIndex, List<ObjectExtraction> objects) {
    }

    public record SceneCollectiveExtraction(int sceneIndex, List<CollectiveExtraction> collectives) {
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
            List<SceneCollectiveExtraction> sceneCollectiveExtractions,
            List<SceneObjectExtraction> sceneObjectExtractions,
            List<SceneLocationExtraction> sceneLocationExtractions,
            List<SceneEventExtraction> sceneEventExtractions
    ) {
        public SceneRelationshipOutcome(
                List<SceneRelationshipAnalysis> triadAnalyses,
                List<SceneIndividualExtraction> sceneIndividualExtractions,
                List<SceneLocationExtraction> sceneLocationExtractions
        ) {
            this(triadAnalyses, sceneIndividualExtractions, List.of(), List.of(), sceneLocationExtractions, List.of());
        }

        public SceneRelationshipOutcome(
                List<SceneRelationshipAnalysis> triadAnalyses,
                List<SceneIndividualExtraction> sceneIndividualExtractions,
                List<SceneLocationExtraction> sceneLocationExtractions,
                List<SceneEventExtraction> sceneEventExtractions
        ) {
            this(triadAnalyses, sceneIndividualExtractions, List.of(), List.of(), sceneLocationExtractions, sceneEventExtractions);
        }

        public SceneRelationshipOutcome(
                List<SceneRelationshipAnalysis> triadAnalyses,
                List<SceneIndividualExtraction> sceneIndividualExtractions,
                List<SceneObjectExtraction> sceneObjectExtractions,
                List<SceneLocationExtraction> sceneLocationExtractions,
                List<SceneEventExtraction> sceneEventExtractions
        ) {
            this(
                    triadAnalyses,
                    sceneIndividualExtractions,
                    List.of(),
                    sceneObjectExtractions,
                    sceneLocationExtractions,
                    sceneEventExtractions
            );
        }
    }
}
