package com.lorevault.api.service.content;

import com.lorevault.api.dto.content.SceneDetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser responsible for converting AI XML responses into SceneDetectionResult objects.
 * Uses built-in DOM parser for robust XML handling with CDATA support.
 */
@Component
@Slf4j
public class SceneDetectionXmlParser {
    
    /**
     * Parses the XML response from the AI model into SceneDetectionResult objects.
     * Handles markdown fencing, CDATA sections, and malformed XML gracefully.
     * 
     * @param xmlResponse Raw XML response from AI model
     * @param chapterTextLength Length of the chapter text (for validation)
     * @return List of parsed scene detection results
     */
    public List<SceneDetectionResult> parseResponse(String xmlResponse, int chapterTextLength) {
        try {
            log.trace("Parsing XML response of length: {}", xmlResponse.length());
            
            // Clean up the response - sometimes AI includes markdown formatting
            String cleanXml = cleanupXmlResponse(xmlResponse);
            log.trace("Full cleaned XML response: {}", cleanXml);

            // Validate basic XML structure
            if (!isValidXmlStructure(cleanXml)) {
                return List.of();
            }
            
            // Parse XML using DOM parser
            Document document = parseXmlDocument(cleanXml);
            if (document == null) {
                return List.of();
            }
            
            // Extract scene elements and convert to results
            List<SceneDetectionResult> results = extractSceneResults(document);
            
            log.info("Successfully parsed {} scene detection results", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Failed to parse scene detection XML response: {}", e.getMessage());
            log.debug("Raw response was: {}", xmlResponse);
            
            // Fallback: return empty list to be handled by caller
            log.warn("Parsing failed, returning empty results for manual handling");
            return List.of();
        }
    }
    
    /**
     * Removes markdown formatting and trims whitespace from XML response
     */
    private String cleanupXmlResponse(String xmlResponse) {
        String cleanXml = xmlResponse.trim();
        if (cleanXml.startsWith("```xml")) {
            cleanXml = cleanXml.substring(6);
        }
        if (cleanXml.startsWith("```")) {
            cleanXml = cleanXml.substring(3);
        }
        if (cleanXml.endsWith("```")) {
            cleanXml = cleanXml.substring(0, cleanXml.length() - 3);
        }
        return cleanXml.trim();
    }
    
    /**
     * Validates basic XML structure before attempting to parse
     */
    private boolean isValidXmlStructure(String cleanXml) {
        if (!cleanXml.trim().startsWith("<")) {
            log.error("XML does not start with '<' character. First 50 chars: '{}'", 
                     cleanXml.substring(0, Math.min(50, cleanXml.length())));
            return false;
        }
        return true;
    }
    
    /**
     * Parses XML string into DOM Document with proper encoding handling
     */
    private Document parseXmlDocument(String cleanXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        log.debug("About to parse XML of length: {}", cleanXml.length());
        log.debug("XML starts with: '{}'", cleanXml.substring(0, Math.min(100, cleanXml.length())));
        
        // Log byte array info for encoding debugging
        byte[] xmlBytes = cleanXml.getBytes("UTF-8");
        log.debug("XML byte array length: {}, first 10 bytes: {}", xmlBytes.length, 
                 java.util.Arrays.toString(java.util.Arrays.copyOf(xmlBytes, Math.min(10, xmlBytes.length))));
        
        // Parse the XML string
        Document document = builder.parse(new ByteArrayInputStream(xmlBytes));
        log.debug("XML parsing completed successfully, document is not null: {}", document != null);
        
        if (document == null) {
            log.error("Document is null after parsing!");
            return null;
        }
        
        document.getDocumentElement().normalize();
        log.debug("Document normalized, getting root element...");
        
        Element rootElement = document.getDocumentElement();
        if (rootElement == null) {
            log.error("Root element is null!");
            return null;
        }
        
        log.debug("Root element name: '{}'", rootElement.getNodeName());
        return document;
    }
    
    /**
     * Extracts scene elements from parsed document and converts to SceneDetectionResult objects
     */
    private List<SceneDetectionResult> extractSceneResults(Document document) {
        List<SceneDetectionResult> results = new ArrayList<>();
        Element rootElement = document.getDocumentElement();
        
        // Get all <scene> elements
        NodeList sceneNodes = document.getElementsByTagName("scene");
        log.debug("Found {} scene nodes in document", sceneNodes.getLength());
        
        if (sceneNodes.getLength() == 0) {
            logMissingSceneNodes(rootElement);
            return results;
        }
        
        for (int i = 0; i < sceneNodes.getLength(); i++) {
            Node sceneNode = sceneNodes.item(i);
            
            if (sceneNode.getNodeType() == Node.ELEMENT_NODE) {
                Element sceneElement = (Element) sceneNode;
                // Extract fields with resilience to minor format drift
                int sceneIndex = getIntValue(sceneElement, "index");
                if (sceneIndex <= 0) {
                    // Fallback to sequential index if missing/invalid
                    sceneIndex = i + 1;
                }
                String startAnchor = getStringValue(sceneElement, "start_anchor");
                String contextSummary = getStringValue(sceneElement, "context_summary");
                String breakReason = getStringValue(sceneElement, "break_reason");
                String chronology = getStringValue(sceneElement, "chronology");
                String chronologyCertainty = getStringValue(sceneElement, "chronology_certainty");
                String chronologyMarker = getStringValue(sceneElement, "chronology_marker");

                if (startAnchor != null && contextSummary != null) {
                    results.add(new SceneDetectionResult(
                        sceneIndex,
                        startAnchor,
                        contextSummary,
                        breakReason,
                        chronology,
                        chronologyCertainty,
                        chronologyMarker
                    ));
                } else {
                    log.warn("Skipping incomplete scene: index={}, start={}, context={}, reason={}", 
                            sceneIndex,
                            startAnchor != null ? "present" : "missing",
                            contextSummary != null ? "present" : "missing",
                            breakReason != null ? "present" : "missing");
                }
            }
        }
        
        return results;
    }
    
    /**
     * Logs information about missing scene nodes for debugging
     */
    private void logMissingSceneNodes(Element rootElement) {
        log.warn("No scene nodes found! Root element children:");
        NodeList rootChildren = rootElement.getChildNodes();
        for (int j = 0; j < rootChildren.getLength(); j++) {
            Node child = rootChildren.item(j);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                log.warn("  Child element: '{}'", child.getNodeName());
            }
        }
    }
    
    // Inlined single-scene extraction into extractSceneResults to allow index fallback
    
    /**
     * Extracts integer value from DOM element with the given tag name.
     */
    private int getIntValue(Element parentElement, String tagName) {
        NodeList nodeList = parentElement.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String textContent = nodeList.item(0).getTextContent().trim();
            try {
                return Integer.parseInt(textContent);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse integer value from element '{}': '{}'", tagName, textContent);
                return 0;
            }
        }
        return 0;
    }

    /**
     * Extracts string value from DOM element with the given tag name.
     * Handles both regular text content and CDATA sections.
     */
    private String getStringValue(Element parentElement, String tagName) {
        NodeList nodeList = parentElement.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            String textContent = node.getTextContent();
            return textContent != null ? textContent.trim() : null;
        }
        return null;
    }
}
