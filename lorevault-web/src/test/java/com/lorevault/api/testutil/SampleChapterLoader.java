package com.lorevault.api.testutil;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.web.command.ingestion.SubmitChapterRequest;
import lombok.experimental.UtilityClass;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Utility class for loading sample chapters and test data from test resources.
 * Provides realistic test data for integration tests and unit test utilities.
 * Consolidates all test data loading functionality following the testing strategy guidelines.
 */
@UtilityClass
public class SampleChapterLoader {

    private static final UUID DEATHWORLDERS_BOOK_ID = UUID.nameUUIDFromBytes("Deathworlders".getBytes(StandardCharsets.UTF_8));

    /**
     * Loads all available sample chapters from test resources.
     * 
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
     * 
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

        return new SubmitChapterRequest(
                DEATHWORLDERS_BOOK_ID,
                1,
                "The Kevin Jenkins Experience",
                content
        );
    }

    private static SubmitChapterRequest loadRunLittleMonster() {
        String content = loadFileContent("sample-chapters/005_reddit-Hambone3110 - Run, little monster.txt");

        return new SubmitChapterRequest(
                DEATHWORLDERS_BOOK_ID,
                3,
                "Run, little monster",
                content
        );
    }

    private static SubmitChapterRequest loadAftermath() {
        String content = loadFileContent("sample-chapters/007_reddit-Hambone3110 - Aftermath.txt");

        return new SubmitChapterRequest(
                DEATHWORLDERS_BOOK_ID,
                3,
                "Aftermath",
                content
        );
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
     * 
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
    stats.put("bookIds", chapters.stream()
        .map(SubmitChapterRequest::getBookId)
        .distinct()
        .toList());

        return stats;
    }

    // Additional Test Data Loading Functionality
    // =========================================

    /**
     * Load XML test data from the scene-detection test resources.
     * 
     * @param filename The filename to load (e.g., "000_chapter-segmentation_output.xml", "000_scene-analysis_output.xml")
     * @return The XML content as a string
     */
    public static String loadSceneDetectionXml(String filename) {
        return loadResourceAsString("scene-detection/" + filename);
    }

    /**
     * Load a resource file as a string using InputStream (more reliable than ResourceUtils).
     * 
     * @param resourcePath The path to the resource file
     * @return The file content as a string
     * @throws RuntimeException if the file cannot be loaded
     */
    public static String loadResourceAsString(String resourcePath) {
        try (InputStream inputStream = SampleChapterLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + resourcePath, e);
        }
    }

    /**
     * Get sample chapter text for testing (first 1000 chars of Kevin Jenkins).
     * Useful for unit tests that need realistic data but want to keep tests fast.
     * 
     * @return Sample chapter text (truncated for performance)
     */
    public static String getSampleChapterText() {
        String fullText = loadResourceAsString("sample-chapters/000_deathworlders - The Kevin Jenkins Experience.txt");
        // Return first 1000 characters for unit tests to keep them fast
        return fullText.length() > 1000 ? fullText.substring(0, 1000) : fullText;
    }

    /**
     * Get full chapter text for a specific sample chapter (for integration tests).
     * 
     * @param chapterName The name of the chapter ("kevin_jenkins", "aftermath", "run_little_monster")
     * @return Full chapter text
     */
    public static String getFullChapterText(String chapterName) {
        return loadSampleChapter(chapterName).getChapterText();
    }
}
