package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.IngestionJobNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.StatusRecordNode;
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
        node.setChapterTitle(domain.getChapterTitle());
        node.setRawText(domain.getRawText());
        node.setContentHash(domain.getContentHash());
        if (domain.getCoordinates() != null) {
            node.setUniverse(domain.getCoordinates().getUniverse());
            node.setSeries(domain.getCoordinates().getSeries());
            node.setBookTitle(domain.getCoordinates().getBookTitle());
            node.setBookNumber(domain.getCoordinates().getBookNumber());
            node.setChapterNumber(domain.getCoordinates().getChapterNumber());
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

    // IngestionJob
    public IngestionJob toDomain(IngestionJobNode node) {
        if (node == null) return null;
        IngestionJob domain = new IngestionJob();
        domain.setId(node.getId());
        domain.setChapterId(node.getChapterId());
        domain.setCreatedAt(node.getCreatedAt());
        domain.setCompletedAt(node.getCompletedAt());
        if (node.getCurrentStatusRecord() != null) {
            domain.setCurrentStatus(toDomain(node.getCurrentStatusRecord()));
        }
        return domain;
    }

    public IngestionJobNode toNode(IngestionJob domain) {
        if (domain == null) return null;
        IngestionJobNode node = new IngestionJobNode();
        node.setId(domain.getId());
        node.setChapterId(domain.getChapterId());
        node.setCreatedAt(domain.getCreatedAt());
        node.setCompletedAt(domain.getCompletedAt());
        if (domain.getCurrentStatus() != null) {
            node.setCurrentStatusRecord(toNode(domain.getCurrentStatus()));
        }
        return node;
    }

    // StatusRecord
    public StatusRecord toDomain(StatusRecordNode node) {
        if (node == null) return null;
        StatusRecord domain = new StatusRecord();
        domain.setId(node.getId());
        domain.setJobId(node.getJobId());
        domain.setTimestamp(node.getTimestamp());
        domain.setStatus(node.getStatus());
        domain.setStepDescription(node.getStepDescription());
        domain.setProgressPercent(node.getProgressPercent());
    // properties are not stored on the node currently
        return domain;
    }

    public StatusRecordNode toNode(StatusRecord domain) {
        if (domain == null) return null;
        StatusRecordNode node = new StatusRecordNode();
        node.setId(domain.getId());
        node.setJobId(domain.getJobId());
        node.setTimestamp(domain.getTimestamp());
        node.setStatus(domain.getStatus());
        node.setStepDescription(domain.getStepDescription());
        node.setProgressPercent(domain.getProgressPercent());
    // properties are not stored on the node currently
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

    public List<IngestionJob> toIngestionJobDomainList(List<IngestionJobNode> nodes) {
        return nodes.stream().map(this::toDomain).collect(Collectors.toList());
    }

    public List<StatusRecord> toStatusRecordDomainList(List<StatusRecordNode> nodes) {
        return nodes.stream().map(this::toDomain).collect(Collectors.toList());
    }
}
