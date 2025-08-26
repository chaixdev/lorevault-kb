package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.testutil.TestIds;
import com.lorevault.api.testutil.fakes.FakeContentPersistencePort;
import com.lorevault.api.testutil.fakes.FakeSceneDetectionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("SceneProcessingService")
class SceneProcessingServiceTest {

    private FakeContentPersistencePort contentPersistencePort;
    private FakeSceneDetectionPort sceneDetectionPort;
    private SceneProcessingService sceneProcessingService;

    private UUID chapterId;
    private Chapter chapterNode;

    @BeforeEach
    void setUp() {
        contentPersistencePort = new FakeContentPersistencePort();
        sceneDetectionPort = new FakeSceneDetectionPort();
        sceneProcessingService = new SceneProcessingService(contentPersistencePort, sceneDetectionPort);

        chapterId = TestIds.CHAPTER_ID;
        chapterNode = createTestChapter();
        contentPersistencePort.createChapter(chapterNode);
    }

    @Nested
    @DisplayName("High-Level Workflow Methods")
    class HighLevelWorkflowMethods {

        @Test
        @DisplayName("should detect and persist scenes successfully")
        void shouldDetectAndPersistScenesSuccessfully() {
            // Given
            List<SceneWithCoordinates> expectedScenesWithCoords = List.of(
                new SceneWithCoordinates(0, 0, 50, "Opening scene"),
                new SceneWithCoordinates(1, 50, 100, "Second scene")
            );
            sceneDetectionPort.configureScenes(chapterId, expectedScenesWithCoords);

            // When
            List<Scene> result = sceneProcessingService.detectAndPersistScenes(chapterId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSceneIndex()).isEqualTo(0);
            assertThat(result.get(0).getContextSummary()).isEqualTo("Opening scene");
            assertThat(result.get(1).getSceneIndex()).isEqualTo(1);
            assertThat(result.get(1).getContextSummary()).isEqualTo("Second scene");

            // Verify scenes were persisted
            List<Scene> persistedScenes = contentPersistencePort.findScenesByChapterId(chapterId);
            assertThat(persistedScenes).hasSize(2);
        }

        @Test
        @DisplayName("should return existing scenes if already present")
        void shouldReturnExistingScenesIfAlreadyPresent() {
            // Given - scenes already exist
            Scene existingScene = new Scene();
            existingScene.setSceneIndex(0);
            existingScene.setContextSummary("Existing scene");
            contentPersistencePort.addScenesToChapter(chapterId, List.of(existingScene));

            // When
            List<Scene> result = sceneProcessingService.detectAndPersistScenes(chapterId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContextSummary()).isEqualTo("Existing scene");

            // Verify no new AI detection was performed
            assertThat(sceneDetectionPort.getCallCount()).isZero();
        }

        @Test
        @DisplayName("should handle empty scene detection results")
        void shouldHandleEmptySceneDetectionResults() {
            // Given
            sceneDetectionPort.configureScenes(chapterId, List.of());

            // When
            List<Scene> result = sceneProcessingService.detectAndPersistScenes(chapterId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should throw exception for non-existent chapter")
        void shouldThrowExceptionForNonExistentChapter() {
            // Given
            UUID nonExistentChapterId = UUID.randomUUID();

            // When & Then
            assertThatThrownBy(() -> sceneProcessingService.detectAndPersistScenes(nonExistentChapterId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chapter not found");
        }

        @Test
        @DisplayName("should retrieve scenes by chapter ID")
        void shouldRetrieveScenesByChapterId() {
            // Given
            Scene scene1 = createTestScene(0, "First scene");
            Scene scene2 = createTestScene(1, "Second scene");
            contentPersistencePort.addScenesToChapter(chapterId, List.of(scene1, scene2));

            // When
            List<Scene> result = sceneProcessingService.getScenesByChapterId(chapterId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContextSummary()).isEqualTo("First scene");
            assertThat(result.get(1).getContextSummary()).isEqualTo("Second scene");
        }

        @Test
        @DisplayName("should delete scenes by chapter ID")
        void shouldDeleteScenesByChapterId() {
            // Given
            Scene existingScene = createTestScene(0, "Scene to delete");
            contentPersistencePort.addScenesToChapter(chapterId, List.of(existingScene));

            // When
            sceneProcessingService.deleteScenesByChapterId(chapterId);

            // Then
            List<Scene> remainingScenes = contentPersistencePort.findScenesByChapterId(chapterId);
            assertThat(remainingScenes).isEmpty();
        }
    }

    @Nested
    @DisplayName("Granular Processing Methods")
    class GranularProcessingMethods {

        @Test
        @DisplayName("should detect scenes without persistence")
        void shouldDetectScenesWithoutPersistence() {
            // Given
            List<SceneWithCoordinates> expectedScenes = List.of(
                new SceneWithCoordinates(0, 0, 25, "Scene one"),
                new SceneWithCoordinates(1, 25, 50, "Scene two")
            );
            sceneDetectionPort.configureScenes(chapterId, expectedScenes);

            // When
            List<SceneWithCoordinates> result = sceneProcessingService.detectScenesForChapter(chapterId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyElementsOf(expectedScenes);

            // Verify no persistence occurred
            List<Scene> persistedScenes = contentPersistencePort.findScenesByChapterId(chapterId);
            assertThat(persistedScenes).isEmpty();
        }

        @Test
        @DisplayName("should persist detected scenes")
        void shouldPersistDetectedScenes() {
            // Given
            List<SceneWithCoordinates> scenesToPersist = List.of(
                new SceneWithCoordinates(0, 0, 30, "Persist scene one"),
                new SceneWithCoordinates(1, 30, 60, "Persist scene two")
            );

            // When
            List<Scene> result = sceneProcessingService.persistDetectedScenes(chapterId, scenesToPersist);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContextSummary()).isEqualTo("Persist scene one");
            assertThat(result.get(1).getContextSummary()).isEqualTo("Persist scene two");

            // Verify persistence
            List<Scene> persistedScenes = contentPersistencePort.findScenesByChapterId(chapterId);
            assertThat(persistedScenes).hasSize(2);
        }

        @Test
        @DisplayName("should parse XML scene detection response")
        void shouldParseXmlSceneDetectionResponse() {
            // Given
            String xmlResponse = """
                <scenes>
                    <scene>
                        <index>0</index>
                        <start_anchor>Chapter text for scene detection analysis</start_anchor>
                        <context_summary>Test scene parsing</context_summary>
                        <break_reason>Scene boundary</break_reason>
                        <chronology>R:temporal.start</chronology>
                        <chronology_certainty>Explicit</chronology_certainty>
                        <chronology_marker>Beginning</chronology_marker>
                    </scene>
                </scenes>
                """;

            // When
            List<SceneDetectionResult> result = sceneProcessingService.parseSceneDetectionXml(
                xmlResponse, chapterNode.getRawText().length());

            // Then
            assertThat(result).hasSize(1);
            SceneDetectionResult scene = result.get(0);
            assertThat(scene.sceneIndex()).isEqualTo(0);
            assertThat(scene.startAnchor()).isEqualTo("Chapter text for scene detection analysis");
            assertThat(scene.contextSummary()).isEqualTo("Test scene parsing");
        }

        @Test
        @DisplayName("should localize scene coordinates from detection results")
        void shouldLocalizeSceneCoordinatesFromDetectionResults() {
            // Given
            String chapterText = chapterNode.getRawText();
            List<SceneDetectionResult> detectionResults = List.of(
                new SceneDetectionResult(0, "Chapter text", "First scene", "Opening", 
                    "R:temporal.start", "Explicit", "Start"),
                new SceneDetectionResult(1, "scene detection", "Second scene", "Continuation",
                    "R:temporal.continues", "Heuristic", "Middle")
            );

            // When
            List<SceneWithCoordinates> result = sceneProcessingService.localizeSceneCoordinates(
                chapterText, detectionResults);

            // Then
            assertThat(result).hasSize(2);
            // Verify coordinates are properly calculated
            assertThat(result.get(0).startCharacterOffset()).isGreaterThanOrEqualTo(0L);
            assertThat(result.get(0).endCharacterOffset()).isGreaterThan(result.get(0).startCharacterOffset());
            assertThat(result.get(1).startCharacterOffset()).isGreaterThanOrEqualTo(result.get(0).endCharacterOffset());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should handle AI detection failures gracefully")
        void shouldHandleAiDetectionFailuresGracefully() {
            // Given
            RuntimeException aiException = new RuntimeException("AI service unavailable");
            sceneDetectionPort.configureException(aiException);

            // When & Then
            assertThatThrownBy(() -> sceneProcessingService.detectAndPersistScenes(chapterId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Scene detection failed")
                    .hasCause(aiException);
        }

        @Test
        @DisplayName("should handle malformed XML responses")
        void shouldHandleMalformedXmlResponses() {
            // Given
            String malformedXml = "<invalid>xml without proper structure</incomplete>";

            // When
            List<SceneDetectionResult> result = sceneProcessingService.parseSceneDetectionXml(
                malformedXml, 100);

            // Then
            assertThat(result).isEmpty(); // Should return empty list for malformed XML
        }

        @Test
        @DisplayName("should handle chapters with empty text")
        void shouldHandleChaptersWithEmptyText() {
            // Given
            chapterNode.setRawText("");
            contentPersistencePort.updateChapter(chapterNode);

            // When
            List<Scene> result = sceneProcessingService.detectAndPersistScenes(chapterId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Integration with Transaction Boundaries")
    class TransactionBoundaries {

        @Test
        @DisplayName("should maintain transaction isolation between detection and persistence")
        void shouldMaintainTransactionIsolationBetweenDetectionAndPersistence() {
            // Given
            List<SceneWithCoordinates> detectedScenes = List.of(
                new SceneWithCoordinates(0, 0, 25, "Transactional scene")
            );
            sceneDetectionPort.configureScenes(chapterId, detectedScenes);

            // When - simulate detection succeeds but persistence might fail
            List<SceneWithCoordinates> detected = sceneProcessingService.detectScenesForChapter(chapterId);
            assertThat(detected).hasSize(1);

            // Then persistence is separate operation
            List<Scene> persisted = sceneProcessingService.persistDetectedScenes(chapterId, detected);
            assertThat(persisted).hasSize(1);
        }
    }

    // Test helper methods
    private Chapter createTestChapter() {
        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setRawText("Chapter text for scene detection analysis. More content here.");
        chapter.setChapterTitle("Test Chapter");
        chapter.setCoordinates(new PublicationCoordinates(
            "TestUniverse", "Test Series", "Test Book", "Test Chapter", 1, 1));
        chapter.setCreatedAt(LocalDateTime.now());
        chapter.setUpdatedAt(LocalDateTime.now());
        return chapter;
    }

    private Scene createTestScene(int sceneIndex, String contextSummary) {
        Scene scene = new Scene();
        scene.setSceneIndex(sceneIndex);
        scene.setContextSummary(contextSummary);
        scene.setStartCharacterOffset(sceneIndex * 25L);
        scene.setEndCharacterOffset((sceneIndex + 1) * 25L);
        scene.setText("Scene text content");
        return scene;
    }
}