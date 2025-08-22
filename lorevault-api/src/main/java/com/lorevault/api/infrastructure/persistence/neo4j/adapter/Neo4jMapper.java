package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.LlmCallRecord;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.IngestionJobNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.LlmCallRecordNode;
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
        node.setRawText(domain.getRawText());
        node.setContentHash(domain.getContentHash());
        if (domain.getCoordinates() != null) {
            node.setUniverse(domain.getCoordinates().getUniverse());
            node.setSeries(domain.getCoordinates().getSeries());
            node.setBookTitle(domain.getCoordinates().getBookTitle());
            // Prefer coordinates chapterTitle, fallback to domain chapterTitle
            node.setChapterTitle(domain.getCoordinates().getChapterTitle() != null 
                ? domain.getCoordinates().getChapterTitle() 
                : domain.getChapterTitle());
            node.setBookNumber(domain.getCoordinates().getBookNumber());
            node.setChapterNumber(domain.getCoordinates().getChapterNumber());
        } else {
            // No coordinates - use domain chapterTitle
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
        
        // LV-083-1: Set dual labels for Scene→Event dual-write
        node.setLabels(List.of("Event"));
        
        node.setCreatedAt(domain.getCreatedAt());
        node.setUpdatedAt(domain.getUpdatedAt());
        return node;
    }    // Chunk
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

    // LlmCallRecord
    public LlmCallRecord toDomain(LlmCallRecordNode node) {
        if (node == null) return null;
        LlmCallRecord d = new LlmCallRecord();
        d.setId(node.getId());
        d.setJobId(node.getJobId());
        d.setStatusRecordId(node.getStatusRecordId());
        d.setStep(node.getStep());
        d.setProvider(node.getProvider());
        d.setModel(node.getModel());
        d.setTemperature(node.getTemperature());
        d.setTopP(node.getTopP());
        d.setMaxTokens(node.getMaxTokens());
        d.setLatencyMs(node.getLatencyMs());
        d.setInputTokens(node.getInputTokens());
        d.setOutputTokens(node.getOutputTokens());
        d.setTokensEstimated(node.getTokensEstimated());
        d.setPromptTemplateId(node.getPromptTemplateId());
        d.setStoreRenderedPrompt(node.getStoreRenderedPrompt());
        d.setRenderedPrompt(node.getRenderedPrompt());
        d.setInputPreview(node.getInputPreview());
        d.setResponseBody(node.getResponseBody());
        d.setResponseHash(node.getResponseHash());
        d.setTruncated(node.getTruncated());
        d.setCreatedAt(node.getCreatedAt());
        return d;
    }

    public LlmCallRecordNode toNode(LlmCallRecord d) {
        if (d == null) return null;
        LlmCallRecordNode n = new LlmCallRecordNode();
        n.setId(d.getId());
        n.setJobId(d.getJobId());
        n.setStatusRecordId(d.getStatusRecordId());
        n.setStep(d.getStep());
        n.setProvider(d.getProvider());
        n.setModel(d.getModel());
        n.setTemperature(d.getTemperature());
        n.setTopP(d.getTopP());
        n.setMaxTokens(d.getMaxTokens());
        n.setLatencyMs(d.getLatencyMs());
        n.setInputTokens(d.getInputTokens());
        n.setOutputTokens(d.getOutputTokens());
        n.setTokensEstimated(d.getTokensEstimated());
        n.setPromptTemplateId(d.getPromptTemplateId());
        n.setStoreRenderedPrompt(d.getStoreRenderedPrompt());
        n.setRenderedPrompt(d.getRenderedPrompt());
        n.setInputPreview(d.getInputPreview());
        n.setResponseBody(d.getResponseBody());
        n.setResponseHash(d.getResponseHash());
        n.setTruncated(d.getTruncated());
        n.setCreatedAt(d.getCreatedAt());
        return n;
    }
}
