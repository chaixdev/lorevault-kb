package com.lorevault.api.testutil.fakes;

import com.lorevault.api.application.port.SceneDetectionPort;
import com.lorevault.api.dto.content.SceneWithCoordinates;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Fake SceneDetectionPort for unit/service tests.
 * Allows pre-configuring results per chapter and simulating failures.
 */
public class FakeSceneDetectionPort implements SceneDetectionPort {

    private final Map<UUID, List<SceneWithCoordinates>> scenesByChapter = new ConcurrentHashMap<>();
    private volatile RuntimeException nextException = null;
    private volatile String implementationInfo = "Fake Scene Detection Implementation";
    private volatile int callCount = 0;

    @Override
    public List<SceneWithCoordinates> detectScenesInText(UUID jobId, UUID chapterId, String chapterText) {
        callCount++; // Track calls for testing
        
        if (nextException != null) {
            RuntimeException toThrow = nextException;
            nextException = null; // Reset for next call
            throw toThrow;
        }
        
        return new ArrayList<>(scenesByChapter.getOrDefault(chapterId, List.of()));
    }

    @Override
    public String getImplementationInfo() {
        return implementationInfo;
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available for tests
    }

    // Test configuration methods
    public void configureScenes(UUID chapterId, List<SceneWithCoordinates> scenes) {
        scenesByChapter.put(chapterId, new ArrayList<>(scenes));
    }

    public void configureException(RuntimeException exception) {
        this.nextException = exception;
    }

    public void setImplementationInfo(String info) {
        this.implementationInfo = info;
    }

    public void clear() {
        scenesByChapter.clear();
        nextException = null;
        implementationInfo = "Fake Scene Detection Implementation";
        callCount = 0;
    }

    public int getCallCount() {
        return callCount;
    }

    // Helper to create scene quickly
    public static SceneWithCoordinates scene(int index, long start, long end, String summary) {
        return new SceneWithCoordinates(index, start, end, summary);
    }
}
