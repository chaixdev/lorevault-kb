package com.lorevault.api.service.content;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

/**
 * Parser for triad-based Pass 2 XML with root <scene_analysis>.
 * Extracts timeline_marker and relationships.previous_to_current/current_to_next.
 */
@Component
@Slf4j
public class TriadXmlParser {

    public static record Relation(String temporalType, String certainty, String evidence) {}
    public static record TriadResult(String timelineMarker, Relation prevToCurr, Relation currToNext) {}

    /**
     * Parse the triad XML string. Tolerant to markdown fences and whitespace.
     */
    public TriadResult parse(String xml) {
        if (xml == null || xml.isBlank()) {
            log.warn("Triad XML empty");
            return new TriadResult(null, null, null);
        }
        String clean = cleanup(xml);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(clean.getBytes("UTF-8")));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            if (root == null || !"scene_analysis".equals(root.getNodeName())) {
                log.warn("Unexpected triad root element: {}", root != null ? root.getNodeName() : null);
                return new TriadResult(null, null, null);
            }

            String timelineMarker = getText(root, "timeline_marker");

            Relation prevToCurr = null;
            Relation currToNext = null;

            NodeList rels = root.getElementsByTagName("relationships");
            if (rels.getLength() > 0) {
                Element relsEl = (Element) rels.item(0);
                prevToCurr = readRelation((Element) firstChildByTag(relsEl, "previous_to_current"));
                currToNext = readRelation((Element) firstChildByTag(relsEl, "current_to_next"));
            }

            return new TriadResult(timelineMarker, prevToCurr, currToNext);
        } catch (Exception e) {
            log.warn("Failed to parse triad XML: {}", e.getMessage());
            return new TriadResult(null, null, null);
        }
    }

    private Relation readRelation(Element relEl) {
        if (relEl == null) return null;
        String temporalType = getText(relEl, "temporal_type");
        String certainty = getText(relEl, "certainty");
        String evidence = getText(relEl, "evidence");
        return new Relation(safeTrim(temporalType), safeTrim(certainty), safeTrim(evidence));
    }

    private String getText(Element parent, String tag) {
        Node n = firstChildByTag(parent, tag);
        if (n == null) return null;
        String t = n.getTextContent();
        return t != null ? t.trim() : null;
    }

    private Node firstChildByTag(Element parent, String tag) {
        if (parent == null) return null;
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0);
    }

    private String cleanup(String xml) {
        String s = xml.trim();
        if (s.startsWith("```xml")) s = s.substring(6);
        if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }

    private String safeTrim(String s) { return s == null ? null : s.trim(); }
}
