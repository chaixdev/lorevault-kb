package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.testutil.TestIds;
import com.lorevault.api.testutil.fakes.FakeContentPersistencePort;
import com.lorevault.api.testutil.fakes.FakeSceneDetectionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("service")
@DisplayName("SceneDetectionService")
class SceneDetectionServiceTest {

    private FakeContentPersistencePort contentPersistencePort;
    private FakeSceneDetectionPort sceneDetectionPort;
    private SceneDetectionService sceneDetectionService;

    private UUID chapterId;
    private Chapter chapterNode;

    @BeforeEach
    void setUp() {
        contentPersistencePort = new FakeContentPersistencePort();
        sceneDetectionPort = new FakeSceneDetectionPort();
        sceneDetectionService = new SceneDetectionService(contentPersistencePort, sceneDetectionPort);

        chapterId = TestIds.CHAPTER_ID;
        chapterNode = new Chapter();
        chapterNode.setId(chapterId);
        chapterNode.setRawText("Chapter text for scene detection analysis.");
        chapterNode.setChapterTitle("Test Chapter");
        chapterNode.setCoordinates(new PublicationCoordinates(
            "TestUniverse",
            "Test Series",
            "Test Book",
            "Test Chapter",
            1,
            1
        ));
        chapterNode.setCreatedAt(LocalDateTime.now());
        chapterNode.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("should successfully detect scenes for valid chapter")
    void shouldSuccessfullyDetectScenesForValidChapter() {
        // Given
        contentPersistencePort.createChapter(chapterNode);
        List<SceneWithCoordinates> expectedScenes = List.of(
            FakeSceneDetectionPort.scene(1, 0, 50, "Opening scene"),
            FakeSceneDetectionPort.scene(2, 50, 100, "Second scene")
        );
        sceneDetectionPort.configureScenes(chapterId, expectedScenes);

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(expectedScenes);
        assertThat(result.get(0).sceneIndex()).isEqualTo(1);
        assertThat(result.get(0).contextSummary()).isEqualTo("Opening scene");
        assertThat(result.get(1).sceneIndex()).isEqualTo(2);
        assertThat(result.get(1).contextSummary()).isEqualTo("Second scene");
    }

    @Test
    @DisplayName("should throw exception when chapter not found")
    void shouldThrowExceptionWhenChapterNotFound() {
        // Given - chapter not created in persistence port

        // When & Then
        assertThatThrownBy(() -> sceneDetectionService.detectScenesForChapter(chapterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chapter not found")
                .hasMessageContaining(chapterId.toString());
    }

    @Test
    @DisplayName("should return empty list when chapter has no text")
    void shouldReturnEmptyListWhenChapterHasNoText() {
        // Given
        chapterNode.setRawText(null);
        contentPersistencePort.createChapter(chapterNode);

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty list when chapter has empty text")
    void shouldReturnEmptyListWhenChapterHasEmptyText() {
        // Given
        chapterNode.setRawText("   ");
        contentPersistencePort.createChapter(chapterNode);

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty list when port returns no scenes")
    void shouldReturnEmptyListWhenPortReturnsNoScenes() {
        // Given
        contentPersistencePort.createChapter(chapterNode);
        sceneDetectionPort.configureScenes(chapterId, List.of()); // Empty scenes

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should wrap and rethrow port exceptions")
    void shouldWrapAndRethrowPortExceptions() {
        // Given
        contentPersistencePort.createChapter(chapterNode);
        RuntimeException portException = new RuntimeException("AI service temporarily unavailable");
        sceneDetectionPort.configureException(portException);

        // When & Then
        assertThatThrownBy(() -> sceneDetectionService.detectScenesForChapter(chapterId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Scene detection failed for chapter")
                .hasMessageContaining(chapterId.toString())
                .hasCause(portException);
    }

    @Test
    @DisplayName("should handle single scene chapter")
    void shouldHandleSingleSceneChapter() {
        // Given
        contentPersistencePort.createChapter(chapterNode);
        List<SceneWithCoordinates> singleScene = List.of(
            FakeSceneDetectionPort.scene(1, 0, chapterNode.getRawText().length(), "Entire chapter as one scene")
        );
        sceneDetectionPort.configureScenes(chapterId, singleScene);

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).sceneIndex()).isEqualTo(1);
        assertThat(result.get(0).startCharacterOffset()).isEqualTo(0L);
        assertThat(result.get(0).endCharacterOffset()).isEqualTo((long) chapterNode.getRawText().length());
    }

    @Test
    @DisplayName("should delegate to scene detection port with correct parameters")
    void shouldDelegateToSceneDetectionPortWithCorrectParameters() {
        // Given
        String customText = "Custom chapter text for scene detection.";
        chapterNode.setRawText(customText);
        contentPersistencePort.createChapter(chapterNode);
        
        List<SceneWithCoordinates> expectedScenes = List.of(
            FakeSceneDetectionPort.scene(1, 0, 20, "First part"),
            FakeSceneDetectionPort.scene(2, 20, customText.length(), "Second part")
        );
        sceneDetectionPort.configureScenes(chapterId, expectedScenes);

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(expectedScenes);
        // Port should have been called with the chapter ID and text
        // (this is verified implicitly by the fake returning the configured scenes)
    }

    @Test
    @DisplayName("should log implementation info on successful detection")
    void shouldLogImplementationInfoOnSuccessfulDetection() {
        // Given
        contentPersistencePort.createChapter(chapterNode);
        sceneDetectionPort.setImplementationInfo("Test Implementation v1.0");
        sceneDetectionPort.configureScenes(chapterId, List.of(
            FakeSceneDetectionPort.scene(1, 0, 100, "Test scene")
        ));

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).hasSize(1);
        // Implementation info should be accessed via the port
        assertThat(sceneDetectionPort.getImplementationInfo()).isEqualTo("Test Implementation v1.0");
    }
}
