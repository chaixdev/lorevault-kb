package com.lorevault.api.service;

import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.service.content.SceneDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SceneDetectionService focusing on service orchestration.
 * Now tests the port-based architecture.
 */
@ExtendWith(MockitoExtension.class)
class SceneDetectionServiceTest {

    @Mock private ContentPersistencePort contentPersistencePort;
    @Mock private SceneDetectionPort sceneDetectionPort;

    @InjectMocks private SceneDetectionService sceneDetectionService;

    private UUID chapterId;
    private ChapterNode chapterNode;
    private String chapterText;

    @BeforeEach
    void setUp() {
        chapterId = UUID.randomUUID();
        chapterText = "First scene start... some text ... Second scene start ... end";
        chapterNode = new ChapterNode();
        chapterNode.setId(chapterId);
        chapterNode.setRawText(chapterText);
        chapterNode.setChapterTitle("Test Chapter");
        chapterNode.setUniverse("TestU");
        chapterNode.setSeries("Series");
        chapterNode.setBookNumber(1);
        chapterNode.setChapterNumber(1);
        chapterNode.setCreatedAt(LocalDateTime.now());
        chapterNode.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void detectScenesForChapter_WhenChapterNotFound_ShouldThrowException() {
        // Given
        when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> sceneDetectionService.detectScenesForChapter(chapterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chapter not found");

        verifyNoInteractions(sceneDetectionPort);
    }

    @Test
    void detectScenesForChapter_WhenChapterHasNoText_ShouldReturnEmptyList() {
        // Given
        chapterNode.setRawText("");
        when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(chapterNode));

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(sceneDetectionPort);
    }

    @Test
    void detectScenesForChapter_WhenPortReturnsEmpty_ShouldReturnEmptyList() {
        // Given
        when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(chapterNode));
        when(sceneDetectionPort.detectScenesInText(chapterId, chapterText)).thenReturn(List.of());

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).isEmpty();
        verify(sceneDetectionPort).detectScenesInText(chapterId, chapterText);
    }

    @Test
    void detectScenesForChapter_WhenPortReturnsScenes_ShouldReturnScenes() {
        // Given
        when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(chapterNode));
        
        SceneWithCoordinates coordinated = new SceneWithCoordinates(
                1, 0L, 25L, "Intro scene"
        );
        when(sceneDetectionPort.detectScenesInText(chapterId, chapterText)).thenReturn(List.of(coordinated));

        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).sceneIndex()).isEqualTo(1);
        assertThat(result.get(0).contextSummary()).isEqualTo("Intro scene");
        assertThat(result.get(0).startCharacterOffset()).isEqualTo(0L);
        assertThat(result.get(0).endCharacterOffset()).isEqualTo(25L);

        verify(sceneDetectionPort).detectScenesInText(chapterId, chapterText);
    }

    @Test
    void detectScenesForChapter_WhenPortThrowsException_ShouldWrapAndRethrow() {
        // Given
        when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(chapterNode));
        when(sceneDetectionPort.detectScenesInText(chapterId, chapterText))
                .thenThrow(new RuntimeException("AI service error"));

        // When & Then
        assertThatThrownBy(() -> sceneDetectionService.detectScenesForChapter(chapterId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Scene detection failed for chapter")
                .hasMessageContaining(chapterId.toString());

        verify(sceneDetectionPort).detectScenesInText(chapterId, chapterText);
    }
}
