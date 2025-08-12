package com.lorevault.api.infrastructure.ai.openai;

import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.application.port.SceneDetectionException;
import com.lorevault.api.dto.content.SceneDetectionResult;
import com.lorevault.api.dto.content.SceneWithCoordinates;
import com.lorevault.api.service.content.SceneDetectionClient;
import com.lorevault.api.service.content.SceneDetectionXmlParser;
import com.lorevault.api.service.content.SceneCoordinateLocalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * OpenAI implementation of scene detection capabilities.
 * Adapts the existing SceneDetectionClient to implement the SceneDetectionPort interface.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiSceneDetectionAdapter implements SceneDetectionPort {
    
    private final SceneDetectionClient sceneDetectionClient;
    private final SceneDetectionXmlParser xmlParser;
    private final SceneCoordinateLocalizer coordinateLocalizer;
    
    @Override
    public List<SceneWithCoordinates> detectScenesInText(UUID chapterId, String chapterText) {
        try {
            log.debug("Starting OpenAI scene detection for chapter {} (length={} chars)", chapterId, chapterText.length());
            
            // Call the AI service to get scene detection XML
            String xmlResponse = sceneDetectionClient.detectScenes(chapterText);
            int xmlLen = xmlResponse == null ? 0 : xmlResponse.length();
            String xmlPreview = xmlResponse == null ? "<null>" : xmlResponse.substring(0, Math.min(400, xmlLen)).replaceAll("\n", "\\n");
            log.debug("[LLM] XML response length={} preview={}...", xmlLen, xmlPreview);
            
            // Parse the XML response into scene detection results
            List<SceneDetectionResult> sceneResults = xmlParser.parseResponse(xmlResponse, chapterText.length());
            log.debug("[LLM] Parsed {} scene result elements", sceneResults.size());
            
            // Localize scene coordinates within the chapter text
            List<SceneWithCoordinates> scenes = coordinateLocalizer.localizeCoordinates(chapterText, sceneResults);
            if (log.isTraceEnabled()) {
                scenes.forEach(s -> log.trace("[LLM] Scene localized index={} start={} end={} contextPreview={}...", s.sceneIndex(), s.startCharacterOffset(), s.endCharacterOffset(),
                        s.contextSummary() == null ? "" : s.contextSummary().substring(0, Math.min(80, s.contextSummary().length()))));
            }
            log.info("OpenAI detected {} scenes for chapter {}", scenes.size(), chapterId);
            return scenes;
            
        } catch (Exception e) {
            log.error("OpenAI scene detection failed for chapter {}: {}", chapterId, e.getMessage(), e);
            throw new SceneDetectionException("OpenAI scene detection failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Simple health check - could call a minimal API endpoint
            return sceneDetectionClient != null;
        } catch (Exception e) {
            log.warn("OpenAI scene detection service availability check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getImplementationInfo() {
        return "OpenAI Scene Detection Adapter v1.0 (GPT-based semantic scene analysis)";
    }
}
