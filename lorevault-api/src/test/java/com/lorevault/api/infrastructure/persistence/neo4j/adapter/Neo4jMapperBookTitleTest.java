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
class Neo4jMapperBookTitleTest {

    private Neo4jMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Neo4jMapper();
    }

    @Test
    void shouldMapBookTitleFromCoordinatesToNode() {
        // Given: Chapter with bookTitle in coordinates
        Chapter domain = new Chapter();
        domain.setId(UUID.randomUUID());
        
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse("Test Universe");
        coordinates.setSeries("Test Series");
        coordinates.setBookTitle("The Fellowship of the Ring");
        coordinates.setChapterTitle("A Long Expected Party");
        coordinates.setBookNumber(1);
        coordinates.setChapterNumber(1);
        
        domain.setCoordinates(coordinates);

        // When: Converting to node
        ChapterNode node = mapper.toNode(domain);

        // Then: Node should have bookTitle from coordinates
        assertThat(node.getBookTitle()).isEqualTo("The Fellowship of the Ring");
        assertThat(node.getUniverse()).isEqualTo("Test Universe");
        assertThat(node.getSeries()).isEqualTo("Test Series");
        assertThat(node.getChapterTitle()).isEqualTo("A Long Expected Party");
    }

    @Test
    void shouldMapBookTitleFromNodeToDomain() {
        // Given: Node with bookTitle
        ChapterNode node = new ChapterNode();
        node.setId(UUID.randomUUID());
        node.setUniverse("Test Universe");
        node.setSeries("Test Series");  
        node.setBookTitle("The Two Towers");
        node.setChapterTitle("The Departure of Boromir");
        node.setBookNumber(2);
        node.setChapterNumber(1);

        // When: Converting to domain
        Chapter domain = mapper.toDomain(node);

        // Then: Domain coordinates should have bookTitle
        assertThat(domain.getCoordinates()).isNotNull();
        assertThat(domain.getCoordinates().getBookTitle()).isEqualTo("The Two Towers");
        assertThat(domain.getCoordinates().getUniverse()).isEqualTo("Test Universe");
        assertThat(domain.getCoordinates().getSeries()).isEqualTo("Test Series");
        assertThat(domain.getCoordinates().getChapterTitle()).isEqualTo("The Departure of Boromir");
    }

    @Test
    void shouldRoundTripBookTitleCorrectly() {
        // Given: Original node with bookTitle
        ChapterNode originalNode = new ChapterNode();
        originalNode.setId(UUID.randomUUID());
        originalNode.setUniverse("Middle Earth");
        originalNode.setSeries("The Lord of the Rings");
        originalNode.setBookTitle("The Return of the King");
        originalNode.setChapterTitle("Minas Tirith");
        originalNode.setBookNumber(3);
        originalNode.setChapterNumber(1);

        // When: Converting to domain and back to node
        Chapter domain = mapper.toDomain(originalNode);
        ChapterNode resultNode = mapper.toNode(domain);

        // Then: bookTitle should be preserved
        assertThat(resultNode.getBookTitle()).isEqualTo("The Return of the King");
        assertThat(domain.getCoordinates().getBookTitle()).isEqualTo("The Return of the King");
    }
}