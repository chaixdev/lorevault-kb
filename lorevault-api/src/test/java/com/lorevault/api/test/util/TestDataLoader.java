package com.lorevault.api.test.util;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility for loading test data files from resources.
 * Provides centralized access to test data following the testing strategy guidelines.
 */
@UtilityClass
public class TestDataLoader {

    /**
     * Load XML test data from the scene-detection test resources.
     * 
     * @param filename The filename to load (e.g., "000_pass1.xml", "000_pass2.xml")
     * @return The XML content as a string
     */
    public static String loadSceneDetectionXml(String filename) {
        return loadResourceAsString("scene-detection/" + filename);
    }

    /**
     * Load a resource file as a string.
     * 
     * @param resourcePath The path to the resource file
     * @return The file content as a string
     * @throws RuntimeException if the file cannot be loaded
     */
    public static String loadResourceAsString(String resourcePath) {
        try (InputStream inputStream = TestDataLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
     * 
     * @return Sample chapter text
     */
    public static String getSampleChapterText() {
        String fullText = loadResourceAsString("sample-chapters/000_deathworlders - The Kevin Jenkins Experience.txt");
        // Return first 1000 characters for unit tests to keep them fast
        return fullText.length() > 1000 ? fullText.substring(0, 1000) : fullText;
    }
}
