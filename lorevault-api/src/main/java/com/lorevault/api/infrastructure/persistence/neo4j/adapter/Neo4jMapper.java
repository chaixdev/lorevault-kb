package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.model.BookNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SeriesNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.UniverseNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class Neo4jMapper {

    // Chapter
    public Chapter toDomain(ChapterNode node) {
        if (node == null) return null;

        Chapter domain = new Chapter();
        domain.setId(node.getId());

        if (node.getBook() != null) {
            domain.setBookId(node.getBook().getId());
            domain.setSeriesId(node.getBook().getSeriesId());
            domain.setUniverseId(node.getBook().getUniverseId());
        }

        domain.setChapterTitle(node.getChapterTitle());
        domain.setRawText(node.getRawText());
        domain.setContentHash(node.getContentHash());

        PublicationCoordinates coords = new PublicationCoordinates();
        coords.setUniverse(node.getUniverse());
        coords.setSeries(node.getSeries());
        coords.setBookTitle(node.getBookTitle());
        coords.setChapterTitle(node.getChapterTitle());
        coords.setBookNumber(node.getBookNumber());
        coords.setChapterNumber(node.getChapterNumber());
        domain.setCoordinates(coords);

        domain.setCreatedAt(node.getCreatedAt());
        domain.setUpdatedAt(node.getUpdatedAt());
        return domain;
    }

    public ChapterNode toNode(Chapter domain) {
        if (domain == null) return null;

        ChapterNode node = new ChapterNode();
        node.setId(domain.getId());
        node.setRawText(domain.getRawText());
        node.setContentHash(domain.getContentHash());

        if (domain.getBookId() != null) {
            BookNode bookRef = new BookNode();
            bookRef.setId(domain.getBookId());
            node.setBook(bookRef);
        }

        PublicationCoordinates coords = domain.getCoordinates();
        if (coords != null) {
            node.setUniverse(coords.getUniverse());
            node.setSeries(coords.getSeries());
            node.setBookTitle(coords.getBookTitle());
            node.setBookNumber(coords.getBookNumber());
            node.setChapterNumber(coords.getChapterNumber());
            node.setChapterTitle(coords.getChapterTitle() != null ? coords.getChapterTitle() : domain.getChapterTitle());
        } else {
            node.setChapterTitle(domain.getChapterTitle());
        }

        node.setCreatedAt(domain.getCreatedAt());
        node.setUpdatedAt(domain.getUpdatedAt());
        return node;
    }

    // Scene
    public Scene toDomain(SceneNode node) {
        if (node == null) return null;

        Scene domain = new Scene();
        domain.setId(node.getId());
        domain.setSceneIndex(node.getSceneIndex());
        domain.setContextSummary(node.getContextSummary());
        domain.setStartCharacterOffset(node.getStartOffset());
        domain.setEndCharacterOffset(node.getEndOffset());
        domain.setText(node.getText());
        domain.setCreatedAt(node.getCreatedAt());
        domain.setUpdatedAt(node.getUpdatedAt());
        return domain;
    }

    public SceneNode toNode(Scene domain) {
        if (domain == null) return null;

        SceneNode node = new SceneNode();
        node.setId(domain.getId());
        node.setSceneIndex(domain.getSceneIndex());
        node.setContextSummary(domain.getContextSummary());
        node.setStartOffset(domain.getStartCharacterOffset());
        node.setEndOffset(domain.getEndCharacterOffset());
        node.setText(domain.getText());

        // LV-083-1: Scene is also an Event in the graph.
        node.setLabels(List.of("Event"));

        node.setCreatedAt(domain.getCreatedAt());
        node.setUpdatedAt(domain.getUpdatedAt());
        return node;
    }

    // Chunk
    public Chunk toDomain(ChunkNode node) {
        if (node == null) return null;

        Chunk domain = new Chunk();
        domain.setId(node.getId());
        domain.setChunkNumberInChapter(node.getChunkNumberInChapter());
        domain.setStartCharInChapter(node.getStartCharInChapter());
        domain.setEndCharInChapter(node.getEndCharInChapter());
        domain.setText(node.getText());
        domain.setContentHash(node.getContentHash());
        domain.setEmbedding(node.getEmbedding());
        domain.setEmbeddingHash(node.getEmbeddingHash());
        domain.setEmbeddedAt(node.getEmbeddedAt());
        domain.setCreatedAt(node.getCreatedAt());
        domain.setUpdatedAt(node.getUpdatedAt());
        return domain;
    }

    public ChunkNode toNode(Chunk domain) {
        if (domain == null) return null;

        ChunkNode node = new ChunkNode();
        node.setId(domain.getId());
        node.setChunkNumberInChapter(domain.getChunkNumberInChapter());
        node.setStartCharInChapter(domain.getStartCharInChapter());
        node.setEndCharInChapter(domain.getEndCharInChapter());
        node.setText(domain.getText());
        node.setContentHash(domain.getContentHash());
        node.setEmbedding(domain.getEmbedding());
        node.setEmbeddingHash(domain.getEmbeddingHash());
        node.setEmbeddedAt(domain.getEmbeddedAt());
        node.setCreatedAt(domain.getCreatedAt());
        node.setUpdatedAt(domain.getUpdatedAt());
        return node;
    }

    // List mappers
    public List<Chapter> toChapterDomainList(List<ChapterNode> nodes) {
        return nodes.stream().map(this::toDomain).collect(Collectors.toList());
    }

    public List<Scene> toSceneDomainList(List<SceneNode> nodes) {
        return nodes.stream().map(this::toDomain).collect(Collectors.toList());
    }

    public List<SceneNode> toSceneNodeList(List<Scene> domains) {
        return domains.stream().map(this::toNode).collect(Collectors.toList());
    }

    public List<Chunk> toChunkDomainList(List<ChunkNode> nodes) {
        return nodes.stream().map(this::toDomain).collect(Collectors.toList());
    }

    public List<ChunkNode> toChunkNodeList(List<Chunk> domains) {
        return domains.stream().map(this::toNode).collect(Collectors.toList());
    }

    // Publication hierarchy
    public Universe toDomain(UniverseNode node) {
        if (node == null) return null;
        return new Universe(node.getId(), node.getName(), node.getSlug(), node.getCreatedAt(), node.getUpdatedAt());
    }

    public UniverseNode toNode(Universe domain) {
        if (domain == null) return null;
        return new UniverseNode(domain.getId(), domain.getName(), domain.getSlug(), domain.getCreatedAt(), domain.getUpdatedAt());
    }

    public Series toDomain(SeriesNode node) {
        if (node == null) return null;
        return new Series(node.getId(), node.getUniverseId(), node.getUniverseName(), node.getName(), node.getCreatedAt(), node.getUpdatedAt());
    }

    public SeriesNode toNode(Series domain) {
        if (domain == null) return null;
        return new SeriesNode(domain.getId(), domain.getUniverseId(), domain.getUniverseName(), domain.getName(), domain.getCreatedAt(), domain.getUpdatedAt());
    }

    public Book toDomain(BookNode node) {
        if (node == null) return null;
        return new Book(
                node.getId(),
                node.getUniverseId(),
                node.getSeriesId(),
                node.getUniverse(),
                node.getSeries(),
                node.getBookNumber(),
                node.getTitle(),
                node.getCreatedAt(),
                node.getUpdatedAt()
        );
    }

    public BookNode toNode(Book domain) {
        if (domain == null) return null;
        return new BookNode(
                domain.getId(),
                domain.getUniverseId(),
                domain.getSeriesId(),
                domain.getUniverse(),
                domain.getSeries(),
                domain.getBookNumber(),
                domain.getTitle(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
