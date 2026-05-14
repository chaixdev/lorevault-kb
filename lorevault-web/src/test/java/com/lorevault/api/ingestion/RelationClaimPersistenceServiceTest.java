package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.infrastructure.*;

import com.lorevault.api.content.relation.RelationClaim;
import com.lorevault.api.content.relation.RelationClaimGraphRepository;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.ingestion.triad.TriadAnalysisModels;
import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationCatalogService;
import com.lorevault.catalog.RelationKindSignature;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelationClaimPersistenceService")
class RelationClaimPersistenceServiceTest {

    @Mock
    private RelationClaimGraphRepository relationClaimRepository;

    @Mock
    private RelationCatalogService catalogService;

    @InjectMocks
    private RelationClaimPersistenceService service;

    // -----------------------------------------------------------------------
    // Null / empty input guards
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Returns without error and does not call repository when persistedScenes is null")
    void nullPersistedScenes() {
        TriadAnalysisModels.RelationClaimExtraction claim = new TriadAnalysisModels.RelationClaimExtraction(
                "R:leads", "Character", "Kaladin", "leads", null,
                "Group", "Bridge Four", "Explicit", null
        );
        TriadAnalysisModels.SceneRelationClaimExtraction extraction =
                new TriadAnalysisModels.SceneRelationClaimExtraction(0, List.of(claim));

        service.persistExtractedRelationClaims(null, List.of(extraction));

        verifyNoInteractions(relationClaimRepository, catalogService);
    }

    @Test
    @DisplayName("Returns without error and does not call repository when persistedScenes is empty")
    void emptyPersistedScenes() {
        TriadAnalysisModels.RelationClaimExtraction claim = new TriadAnalysisModels.RelationClaimExtraction(
                "R:leads", "Character", "Kaladin", "leads", null,
                "Group", "Bridge Four", "Explicit", null
        );
        TriadAnalysisModels.SceneRelationClaimExtraction extraction =
                new TriadAnalysisModels.SceneRelationClaimExtraction(0, List.of(claim));

        service.persistExtractedRelationClaims(List.of(), List.of(extraction));

        verifyNoInteractions(relationClaimRepository, catalogService);
    }

    @Test
    @DisplayName("Returns without error and does not call repository when sceneExtractions is null")
    void nullSceneExtractions() {
        Scene scene = new Scene(UUID.randomUUID(), 0, 0L, 10L, "ctx", "text",
                UUID.randomUUID(), null, null, null, null, null);

        service.persistExtractedRelationClaims(List.of(scene), null);

        verifyNoInteractions(relationClaimRepository, catalogService);
    }

    @Test
    @DisplayName("Returns without error and does not call repository when sceneExtractions is empty")
    void emptySceneExtractions() {
        Scene scene = new Scene(UUID.randomUUID(), 0, 0L, 10L, "ctx", "text",
                UUID.randomUUID(), null, null, null, null, null);

        service.persistExtractedRelationClaims(List.of(scene), List.of());

        verifyNoInteractions(relationClaimRepository, catalogService);
    }

    // -----------------------------------------------------------------------
    // Normal persistence flow
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Persists each claim with catalog resolution and links it to the correct scene")
    void persistsClaimsAndLinksToScene() {
        UUID sceneId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Scene persistedScene = new Scene(sceneId, 0, 0L, 10L, "ctx", "text",
                chapterId, null, null, null, null, null);

        TriadAnalysisModels.RelationClaimExtraction claim1 =
                new TriadAnalysisModels.RelationClaimExtraction(
                        "R:leads", "Character", "Kaladin", "leads", "leads the squad",
                        "Group", "Bridge Four", "Explicit", "Kaladin is in charge");
        TriadAnalysisModels.RelationClaimExtraction claim2 =
                new TriadAnalysisModels.RelationClaimExtraction(
                        "R:studies", "Character", "Shallan", "studies", "studies ancient texts",
                        "Object", "The Notebook", "StronglyImplied", "Shallan is always reading");

        UUID catalogId1 = UUID.randomUUID();
        UUID catalogId2 = UUID.randomUUID();
        RelationCatalogDefinition def1 = new RelationCatalogDefinition(
                new RelationCatalogId(catalogId1), "R:leads", "leads",
                "leads the squad", List.of(new RelationKindSignature("Character", "Group")),
                List.of("leads"), Instant.now(), Instant.now(), Instant.now());
        RelationCatalogDefinition def2 = new RelationCatalogDefinition(
                new RelationCatalogId(catalogId2), "R:studies", "studies",
                "studies ancient texts", List.of(new RelationKindSignature("Character", "Object")),
                List.of("studies"), Instant.now(), Instant.now(), Instant.now());

        when(catalogService.resolve(any())).thenReturn(def1).thenReturn(def2);
        when(relationClaimRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TriadAnalysisModels.SceneRelationClaimExtraction extraction =
                new TriadAnalysisModels.SceneRelationClaimExtraction(0, List.of(claim1, claim2));

        service.persistExtractedRelationClaims(List.of(persistedScene), List.of(extraction));

        ArgumentCaptor<RelationClaim> savedCaptor = ArgumentCaptor.forClass(RelationClaim.class);
        verify(relationClaimRepository, times(2)).save(savedCaptor.capture());
        List<RelationClaim> savedClaims = savedCaptor.getAllValues();

        // -- first claim --
        RelationClaim first = savedClaims.get(0);
        assertThat(first.id()).isNotNull();
        assertThat(first.relationName()).isEqualTo("leads");
        assertThat(first.relationDescription()).isEqualTo("leads the squad");
        assertThat(first.catalogId()).isEqualTo(catalogId1);
        assertThat(first.definitionKey()).isEqualTo("R:leads");
        assertThat(first.subjectKind()).isEqualTo("Character");
        assertThat(first.subjectName()).isEqualTo("Kaladin");
        assertThat(first.objectKind()).isEqualTo("Group");
        assertThat(first.objectName()).isEqualTo("Bridge Four");
        assertThat(first.certainty()).isEqualTo("Explicit");
        assertThat(first.evidenceText()).isEqualTo("Kaladin is in charge");
        assertThat(first.source()).isEqualTo("ai-scene-analysis");
        assertThat(first.sceneId()).isEqualTo(sceneId);
        assertThat(first.chapterId()).isEqualTo(chapterId);
        assertThat(first.bookId()).isNull();
        assertThat(first.extractionIndex()).isEqualTo(0);
        // timestamps are handled by Spring Data listeners (null before persist)
        assertThat(first.createdAt()).isNull();
        assertThat(first.updatedAt()).isNull();

        // -- second claim --
        RelationClaim second = savedClaims.get(1);
        assertThat(second.id()).isNotNull().isNotEqualTo(first.id());
        assertThat(second.relationName()).isEqualTo("studies");
        assertThat(second.relationDescription()).isEqualTo("studies ancient texts");
        assertThat(second.catalogId()).isEqualTo(catalogId2);
        assertThat(second.definitionKey()).isEqualTo("R:studies");
        assertThat(second.subjectName()).isEqualTo("Shallan");
        assertThat(second.objectName()).isEqualTo("The Notebook");
        assertThat(second.certainty()).isEqualTo("StronglyImplied");
        assertThat(second.extractionIndex()).isEqualTo(1);
        assertThat(second.sceneId()).isEqualTo(sceneId);
        assertThat(second.chapterId()).isEqualTo(chapterId);

        // -- link calls --
        verify(relationClaimRepository).linkClaimToScene(sceneId, first.id());
        verify(relationClaimRepository).linkClaimToScene(sceneId, second.id());
        verify(catalogService, times(2)).resolve(any());
    }

    @Test
    @DisplayName("Degrades gracefully when catalog service throws exception")
    void degradesGracefullyOnCatalogFailure() {
        UUID sceneId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Scene persistedScene = new Scene(sceneId, 0, 0L, 10L, "ctx", "text",
                chapterId, null, null, null, null, null);

        TriadAnalysisModels.RelationClaimExtraction claim =
                new TriadAnalysisModels.RelationClaimExtraction(
                        "R:leads", "Character", "Kaladin", "leads", null,
                        "Group", "Bridge Four", "Explicit", null);
        TriadAnalysisModels.SceneRelationClaimExtraction extraction =
                new TriadAnalysisModels.SceneRelationClaimExtraction(0, List.of(claim));

        when(catalogService.resolve(any())).thenThrow(new RuntimeException("Catalog unavailable"));
        when(relationClaimRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistExtractedRelationClaims(List.of(persistedScene), List.of(extraction));

        ArgumentCaptor<RelationClaim> savedCaptor = ArgumentCaptor.forClass(RelationClaim.class);
        verify(relationClaimRepository).save(savedCaptor.capture());
        verify(catalogService).resolve(any());

        RelationClaim saved = savedCaptor.getValue();
        assertThat(saved.catalogId()).isNull();
        assertThat(saved.definitionKey()).isEqualTo("R:leads");
        assertThat(saved.relationName()).isEqualTo("leads");
    }

    // -----------------------------------------------------------------------
    // Idempotency guard
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Skips claim when idempotency guard indicates it already exists")
    void skipsDuplicateClaim() {
        UUID sceneId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Scene persistedScene = new Scene(sceneId, 0, 0L, 10L, "ctx", "text",
                chapterId, null, null, null, null, null);

        TriadAnalysisModels.RelationClaimExtraction claim =
                new TriadAnalysisModels.RelationClaimExtraction(
                        "R:leads", "Character", "Kaladin", "leads", null,
                        "Group", "Bridge Four", "Explicit", null);
        TriadAnalysisModels.SceneRelationClaimExtraction extraction =
                new TriadAnalysisModels.SceneRelationClaimExtraction(0, List.of(claim));

        when(relationClaimRepository.countBySceneIdAndExtractionIndexAndRelationName(
                sceneId, 0, "leads")).thenReturn(1L);

        service.persistExtractedRelationClaims(List.of(persistedScene), List.of(extraction));

        verify(relationClaimRepository, never()).save(any());
        verify(relationClaimRepository, never()).linkClaimToScene(any(), any());
        verifyNoInteractions(catalogService);
    }

    // -----------------------------------------------------------------------
    // Scene-not-found guard
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Skips claims when scene index does not match any persisted scene")
    void skipsClaimsForMissingScene() {
        UUID sceneId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        Scene persistedScene = new Scene(sceneId, 0, 0L, 10L, "ctx", "text",
                chapterId, null, null, null, null, null);

        TriadAnalysisModels.RelationClaimExtraction claim =
                new TriadAnalysisModels.RelationClaimExtraction(
                        "R:leads", "Character", "Kaladin", "leads", null,
                        "Group", "Bridge Four", "Explicit", null);
        // scene index 99 does not exist in persistedScenes
        TriadAnalysisModels.SceneRelationClaimExtraction extraction =
                new TriadAnalysisModels.SceneRelationClaimExtraction(99, List.of(claim));

        service.persistExtractedRelationClaims(List.of(persistedScene), List.of(extraction));

        verify(relationClaimRepository, never()).save(any());
        verify(relationClaimRepository, never()).linkClaimToScene(any(), any());
    }

    @Test
    @DisplayName("Skips claims when scene has null eventId or null sceneIndex (filtered from map)")
    void skipsClaimsForSceneWithNullIdentityFields() {
        // Scene with null id and sceneIndex — will be filtered out by the stream filter
        Scene invalidScene = new Scene(null, null, 0L, 10L, "ctx", "text",
                UUID.randomUUID(), null, null, null, null, null);

        TriadAnalysisModels.RelationClaimExtraction claim =
                new TriadAnalysisModels.RelationClaimExtraction(
                        "R:leads", "Character", "Kaladin", "leads", null,
                        "Group", "Bridge Four", "Explicit", null);
        TriadAnalysisModels.SceneRelationClaimExtraction extraction =
                new TriadAnalysisModels.SceneRelationClaimExtraction(0, List.of(claim));

        service.persistExtractedRelationClaims(List.of(invalidScene), List.of(extraction));

        verify(relationClaimRepository, never()).save(any());
        verify(relationClaimRepository, never()).linkClaimToScene(any(), any());
        verifyNoInteractions(catalogService);
    }
}
