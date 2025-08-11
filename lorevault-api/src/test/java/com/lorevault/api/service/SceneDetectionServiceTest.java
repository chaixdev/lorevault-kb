package com.lorevault.api.service;

import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.graph.model.ChapterNode;
import com.lorevault.api.graph.port.ContentPersistencePort;
import com.lorevault.api.service.content.SceneCoordinateLocalizer;
import com.lorevault.api.service.content.SceneDetectionClient;
import com.lorevault.api.service.content.SceneDetectionService;
import com.lorevault.api.service.content.SceneDetectionXmlParser;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SceneDetectionService focusing on service orchestration.
 */
@ExtendWith(MockitoExtension.class)
class SceneDetectionServiceTest {

    @Mock private ContentPersistencePort contentPersistencePort;
    @Mock private SceneDetectionClient sceneDetectionClient;
    @Mock private SceneDetectionXmlParser xmlParser;
    @Mock private SceneCoordinateLocalizer coordinateLocalizer;

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
    void detectScenesForChapter_WhenChapterNotFound_ShouldThrow() {
        when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sceneDetectionService.detectScenesForChapter(chapterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chapter not found");
        verifyNoInteractions(sceneDetectionClient, xmlParser, coordinateLocalizer);
    }

    @Test
    void detectScenesForChapter_WhenChapterTextEmpty_ShouldReturnEmptyList() {
        chapterNode.setRawText("   ");
        when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(chapterNode));
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);
        assertThat(result).isEmpty();
        verifyNoInteractions(sceneDetectionClient, xmlParser, coordinateLocalizer);
    }

    @Test
    void detectScenesForChapter_WhenAiReturnsNoScenes_ShouldReturnEmptyList() {
        when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(chapterNode));
        when(sceneDetectionClient.detectScenes(chapterText)).thenReturn("<scenes></scenes>");
        when(xmlParser.parseResponse(any(), eq(chapterText.length()))).thenReturn(List.of());
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);
        assertThat(result).isEmpty();
        verify(sceneDetectionClient).detectScenes(chapterText);
        verify(xmlParser).parseResponse(any(), eq(chapterText.length()));
        verifyNoInteractions(coordinateLocalizer);
    }

    @Test
    void detectScenesForChapter_WhenScenesDetected_ShouldReturnCoordinates() {
        when(contentPersistencePort.findChapterById(chapterId)).thenReturn(Optional.of(chapterNode));
        when(sceneDetectionClient.detectScenes(chapterText)).thenReturn("<scenes><scene></scene></scenes>");
        SceneDetectionResult detection = new SceneDetectionResult(1, "First", "Second", "Intro scene", "shift in location");
        when(xmlParser.parseResponse(any(), eq(chapterText.length()))).thenReturn(List.of(detection));
        SceneWithCoordinates coordinated = new SceneWithCoordinates(1, 0L, 20L, "Intro scene");
        when(coordinateLocalizer.localizeCoordinates(eq(chapterText), any())).thenReturn(List.of(coordinated));

        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(chapterId);

        assertThat(result).hasSize(1);
        SceneWithCoordinates sc = result.get(0);
        assertThat(sc.sceneIndex()).isEqualTo(1);
        assertThat(sc.startCharacterOffset()).isEqualTo(0L);
        assertThat(sc.endCharacterOffset()).isEqualTo(20L);
        assertThat(sc.contextSummary()).isEqualTo("Intro scene");

        verify(sceneDetectionClient).detectScenes(chapterText);
        verify(xmlParser).parseResponse(any(), eq(chapterText.length()));
        verify(coordinateLocalizer).localizeCoordinates(eq(chapterText), any());
    }
}
