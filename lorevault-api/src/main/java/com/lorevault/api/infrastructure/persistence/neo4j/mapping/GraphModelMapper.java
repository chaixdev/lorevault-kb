package com.lorevault.api.infrastructure.persistence.neo4j.mapping;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.infrastructure.persistence.neo4j.model.*;
import org.springframework.stereotype.Component;

/**
 * Simplified mapper during transition; only entity->node needed now.
 */
@Component
public class GraphModelMapper {
    public ChapterNode toChapterNode(Chapter chapter) {
        if (chapter == null) return null;
        ChapterNode node = new ChapterNode();
        node.setId(chapter.getId());
        if (chapter.getCoordinates() != null) {
            node.setUniverse(chapter.getCoordinates().getUniverse());
            node.setSeries(chapter.getCoordinates().getSeries());
            node.setBookNumber(chapter.getCoordinates().getBookNumber());
            node.setChapterNumber(chapter.getCoordinates().getChapterNumber());
        }
        node.setChapterTitle(chapter.getChapterTitle());
        node.setRawText(chapter.getRawText());
        node.setContentHash(chapter.getContentHash());
        return node;
    }

    public IngestionJobNode toJobNode(IngestionJob job) {
        if (job == null) return null;
        IngestionJobNode n = new IngestionJobNode();
        n.setId(job.getId());
        n.setChapterId(job.getChapterId());
        n.setCurrentStatusRecord(toStatusRecordNode(job.getCurrentStatus()));
        n.setCompletedAt(job.getCompletedAt());
        n.setCreatedAt(job.getCreatedAt());
        return n;
    }

    public StatusRecordNode toStatusRecordNode(StatusRecord record) {
        if (record == null) return null;
        StatusRecordNode n = new StatusRecordNode();
        n.setId(record.getId());
        n.setStatus(record.getStatus());
        n.setStepDescription(record.getStepDescription());
        n.setProgressPercent(record.getStatus().getProgressPercentage());
        n.setTimestamp(record.getTimestamp());
        return n;
    }

    public SceneNode toSceneNode(Scene scene) {
        if (scene == null) return null;
        SceneNode n = new SceneNode();
        n.setId(scene.getId());
        n.setSceneIndex(scene.getSceneIndex());
        n.setStartOffset(scene.getStartCharacterOffset());
        n.setEndOffset(scene.getEndCharacterOffset());
        n.setContextSummary(scene.getContextSummary());
        return n;
    }

    public SceneNode toSceneNode(Scene scene, String sceneText) {
        if (scene == null) return null;
        SceneNode n = new SceneNode();
        n.setId(scene.getId());
        n.setSceneIndex(scene.getSceneIndex());
        n.setStartOffset(scene.getStartCharacterOffset());
        n.setEndOffset(scene.getEndCharacterOffset());
        n.setContextSummary(scene.getContextSummary());
        n.setText(sceneText);
        return n;
    }

    public ChunkNode toChunkNode(Chunk chunk) {
        if (chunk == null) return null;
        ChunkNode n = new ChunkNode();
        n.setId(chunk.getId());
    // Legacy positional fields will be deprecated from Chunk; kept temporarily
    n.setChunkNumberInChapter(chunk.getChunkNumberInChapter());
    n.setStartCharInChapter(chunk.getStartCharInChapter());
    n.setEndCharInChapter(chunk.getEndCharInChapter());
        n.setContentHash(chunk.getContentHash());
        return n;
    }

    public ChunkNode toChunkNode(Chunk chunk, String chunkText) {
        if (chunk == null) return null;
        ChunkNode n = new ChunkNode();
        n.setId(chunk.getId());
        // Legacy positional fields will be deprecated from Chunk; kept temporarily
        n.setChunkNumberInChapter(chunk.getChunkNumberInChapter());
        n.setStartCharInChapter(chunk.getStartCharInChapter());
        n.setEndCharInChapter(chunk.getEndCharInChapter());
        n.setContentHash(chunk.getContentHash());
        n.setText(chunkText);
        return n;
    }

    public SceneHasChunk toSceneHasChunk(ChunkNode chunkNode, Integer chunkIndex) {
        if (chunkNode == null) return null;
        SceneHasChunk rel = new SceneHasChunk();
        rel.setChunk(chunkNode);
        rel.setChunkIndex(chunkIndex);
        return rel;
    }

    public StatusRecord toStatusRecord(StatusRecordNode recordNode) {
        StatusRecord statusRecord = new StatusRecord();
        statusRecord.setId(recordNode.getId());
        statusRecord.setStatus(recordNode.getStatus());
        statusRecord.setTimestamp(recordNode.getTimestamp());
        statusRecord.setJobId(recordNode.getJobId());
        statusRecord.setStepDescription(recordNode.getStepDescription());

        return statusRecord;
    }
}
