package com.lorevault.api.testutil;

import com.lorevault.api.dto.SubmitChapterRequest;
import com.lorevault.api.model.PublicationCoordinates;
import lombok.experimental.UtilityClass;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for loading sample chapters from test resources.
 * Provides realistic test data for integration tests.
 */
@UtilityClass
public class SampleChapterLoader {

    /**
     * Loads all available sample chapters from test resources.
     * @return List of SubmitChapterRequest objects with real chapter data
     */
    public static List<SubmitChapterRequest> loadAllSampleChapters() {
        List<SubmitChapterRequest> chapters = new ArrayList<>();
        
        try {
            // Load Kevin Jenkins Experience
            chapters.add(loadKevinJenkinsExperience());
            
            // Load Aftermath
            chapters.add(loadAftermath());
            
            // Load Run Little Monster
            chapters.add(loadRunLittleMonster());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to load sample chapters", e);
        }
        
        return chapters;
    }
    
    /**
     * Loads a specific sample chapter by name.
     * @param chapterName The name of the chapter to load
     * @return SubmitChapterRequest with the chapter data
     */
    public static SubmitChapterRequest loadSampleChapter(String chapterName) {
        return switch (chapterName.toLowerCase()) {
            case "kevin_jenkins" -> loadKevinJenkinsExperience();
            case "aftermath" -> loadAftermath();
            case "run_little_monster" -> loadRunLittleMonster();
            default -> throw new IllegalArgumentException("Unknown sample chapter: " + chapterName);
        };
    }
    
    private static SubmitChapterRequest loadKevinJenkinsExperience() {
        String content = loadFileContent("sample-chapters/000_deathworlders - The Kevin Jenkins Experience.txt");
        
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setCoordinates(new PublicationCoordinates("Deathworlders", "Main Series", 1, null, 0));
        request.setChapterTitle("The Kevin Jenkins Experience");
        request.setChapterText(content);
        
        return request;
    }
    
    private static SubmitChapterRequest loadAftermath() {
        String content = loadFileContent("sample-chapters/007_reddit-Hambone3110 - Aftermath.txt");
        
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setCoordinates(new PublicationCoordinates("Deathworlders", "Side Stories", 1, null, 7));
        request.setChapterTitle("Aftermath");
        request.setChapterText(content);
        
        return request;
    }
    
    private static SubmitChapterRequest loadRunLittleMonster() {
        String content = loadFileContent("sample-chapters/005_reddit-Hambone3110 - Run, little monster.txt");
        
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setCoordinates(new PublicationCoordinates("Deathworlders", "Side Stories", 1, null, 5));
        request.setChapterTitle("Run, little monster");
        request.setChapterText(content);
        
        return request;
    }
    
    private static String loadFileContent(String resourcePath) {
        try {
            Path path = ResourceUtils.getFile("classpath:" + resourcePath).toPath();
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + resourcePath, e);
        }
    }
    
    /**
     * Creates a map of chapter metadata for testing purposes.
     * @return Map with chapter statistics and metadata
     */
    public static Map<String, Object> getSampleChapterStats() {
        Map<String, Object> stats = new HashMap<>();
        List<SubmitChapterRequest> chapters = loadAllSampleChapters();
        
        stats.put("totalChapters", chapters.size());
        stats.put("averageLength", chapters.stream()
            .mapToInt(ch -> ch.getChapterText().length())
            .average()
            .orElse(0.0));
        stats.put("universes", chapters.stream()
            .map(ch -> ch.getCoordinates().getUniverse())
            .distinct()
            .toList());
        stats.put("series", chapters.stream()
            .map(ch -> ch.getCoordinates().getSeries())
            .distinct()
            .toList());
            
        return stats;
    }
}
