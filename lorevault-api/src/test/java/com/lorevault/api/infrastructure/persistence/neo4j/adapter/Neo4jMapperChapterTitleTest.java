package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class Neo4jMapperChapterTitleTest {

    private Neo4jMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Neo4jMapper();
    }

    @Test
    void shouldMapChapterTitleFromCoordinatesToNode() {
        // Given: Chapter with chapterTitle in coordinates
        Chapter domain = new Chapter();
        domain.setId(UUID.randomUUID());
        domain.setChapterTitle("Domain Chapter Title");
        
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse("Test Universe");
        coordinates.setSeries("Test Series");
        coordinates.setBookTitle("Test Book");
        coordinates.setChapterTitle("Coordinates Chapter Title");
        coordinates.setBookNumber(1);
        coordinates.setChapterNumber(5);
        
        domain.setCoordinates(coordinates);

        // When: Converting to node
        ChapterNode node = mapper.toNode(domain);

        // Then: Node should have chapterTitle from coordinates (preferred) or domain
        assertThat(node.getChapterTitle()).isEqualTo("Coordinates Chapter Title");
        assertThat(node.getUniverse()).isEqualTo("Test Universe");
        assertThat(node.getSeries()).isEqualTo("Test Series");
        assertThat(node.getBookTitle()).isEqualTo("Test Book");
        assertThat(node.getBookNumber()).isEqualTo(1);
        assertThat(node.getChapterNumber()).isEqualTo(5);
    }

    @Test
    void shouldMapChapterTitleFromDomainWhenCoordinatesChapterTitleIsNull() {
        // Given: Chapter with chapterTitle in domain but null in coordinates
        Chapter domain = new Chapter();
        domain.setId(UUID.randomUUID());
        domain.setChapterTitle("Domain Chapter Title");
        
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse("Test Universe");
        coordinates.setSeries("Test Series");
        coordinates.setBookTitle("Test Book");
        coordinates.setChapterTitle(null);  // Null in coordinates
        coordinates.setBookNumber(1);
        coordinates.setChapterNumber(5);
        
        domain.setCoordinates(coordinates);

        // When: Converting to node
        ChapterNode node = mapper.toNode(domain);

        // Then: Node should fallback to domain chapterTitle
        assertThat(node.getChapterTitle()).isEqualTo("Domain Chapter Title");
    }

    @Test
    void shouldMapChapterTitleFromDomainWhenCoordinatesIsNull() {
        // Given: Chapter with no coordinates
        Chapter domain = new Chapter();
        domain.setId(UUID.randomUUID());
        domain.setChapterTitle("Domain Chapter Title");
        domain.setCoordinates(null);

        // When: Converting to node
        ChapterNode node = mapper.toNode(domain);

        // Then: Node should use domain chapterTitle
        assertThat(node.getChapterTitle()).isEqualTo("Domain Chapter Title");
    }

    @Test
    void shouldRoundTripChapterTitleCorrectly() {
        // Given: Original node with chapterTitle
        ChapterNode originalNode = new ChapterNode();
        originalNode.setId(UUID.randomUUID());
        originalNode.setUniverse("Test Universe");
        originalNode.setSeries("Test Series");
        originalNode.setBookTitle("Test Book");
        originalNode.setChapterTitle("Original Chapter Title");
        originalNode.setBookNumber(1);
        originalNode.setChapterNumber(5);

        // When: Converting to domain and back to node
        Chapter domain = mapper.toDomain(originalNode);
        ChapterNode resultNode = mapper.toNode(domain);

        // Then: chapterTitle should be preserved
        assertThat(resultNode.getChapterTitle()).isEqualTo("Original Chapter Title");
        assertThat(domain.getChapterTitle()).isEqualTo("Original Chapter Title");
        assertThat(domain.getCoordinates().getChapterTitle()).isEqualTo("Original Chapter Title");
    }
}