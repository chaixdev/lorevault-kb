package com.lorevault.api.service;

import com.lorevault.api.dto.SceneDetectionResult;
import com.lorevault.api.dto.SceneWithCoordinates;
import com.lorevault.api.model.Chapter;
import com.lorevault.api.repository.ChapterRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SceneDetectionService focusing on service orchestration.
 */
@ExtendWith(MockitoExtension.class)
class SceneDetectionServiceTest {

    @Mock
    private SceneDetectionClient client;
    
    @Mock
    private SceneDetectionXmlParser xmlParser;
    
    @Mock
    private SceneCoordinateLocalizer coordinateLocalizer;
    
    @Mock
    private ChapterRepository chapterRepository;
    
    @InjectMocks
    private SceneDetectionService sceneDetectionService;
    
    private Chapter sampleChapter;
    private UUID sampleChapterId;
    
    @BeforeEach
    void setUp() {
        // Sample chapter data
        sampleChapterId = UUID.randomUUID();
        sampleChapter = new Chapter();
        sampleChapter.setId(sampleChapterId);
        sampleChapter.setRawText("Sample chapter text for testing");
    }
    
    @Test
    void detectScenesForChapter_ShouldOrchestrateDependencies() {
        // Given
        String xmlResponse = "<scenes><scene>...</scene></scenes>";
        
        List<SceneDetectionResult> parseResults = List.of(
            new SceneDetectionResult(1, "Scene 1 summary", "Start anchor 1", "End anchor 1", "Scene break reason 1"),
            new SceneDetectionResult(2, "Scene 2 summary", "Start anchor 2", "End anchor 2", "Scene break reason 2")
        );
        
        List<SceneWithCoordinates> expectedCoordinates = List.of(
            new SceneWithCoordinates(1, 0L, 100L, "Scene 1 summary"),
            new SceneWithCoordinates(2, 101L, 200L, "Scene 2 summary")
        );
        
        // Mock the dependencies
        when(chapterRepository.findById(sampleChapterId)).thenReturn(Optional.of(sampleChapter));
        when(client.detectScenes(any())).thenReturn(xmlResponse);
        when(xmlParser.parseResponse(anyString(), anyInt())).thenReturn(parseResults);
        when(coordinateLocalizer.localizeCoordinates(any(), any())).thenReturn(expectedCoordinates);
        
        // When
        List<SceneWithCoordinates> result = sceneDetectionService.detectScenesForChapter(sampleChapterId);
        
        // Then
        assertThat(result).isEqualTo(expectedCoordinates);
        
        // Verify the dependencies were called correctly
        Mockito.verify(chapterRepository).findById(sampleChapterId);
        Mockito.verify(client).detectScenes(sampleChapter.getRawText());
        Mockito.verify(xmlParser).parseResponse(eq(xmlResponse), anyInt());
        Mockito.verify(coordinateLocalizer).localizeCoordinates(sampleChapter.getRawText(), parseResults);
    }
}
