package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.application.port.TemporalEdgePort;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.TemporalEdgeWriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Neo4j implementation of TemporalEdgePort.
 * Handles idempotent creation of temporal edges using Cypher MERGE operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Neo4jTemporalEdgeAdapter implements TemporalEdgePort {
    
    private final TemporalEdgeWriteRepository temporalEdgeWriteRepository;
    
    @Override
    public int createInChapterDefaults(UUID bookId) {
        return temporalEdgeWriteRepository.mergeInChapterDefaultEdges(bookId);
    }
    
    @Override
    public int createCrossChapterDefault(UUID bookId) {
        return temporalEdgeWriteRepository.mergeCrossChapterDefaultEdge(bookId);
    }
    
    @Override
    public int countTemporalEdgesFromChapter(UUID chapterId) {
        return temporalEdgeWriteRepository.countTemporalEdgesFromChapter(chapterId);
    }

    @Override
    public int countInChapterCycleCandidates(UUID bookId) {
        return temporalEdgeWriteRepository.countInChapterCycleCandidates(bookId);
    }

    @Override
    public int countCrossChapterCycleCandidates(UUID bookId) {
        return temporalEdgeWriteRepository.countCrossChapterCycleCandidates(bookId);
    }

    @Override
    public Long upsertTemporalEdge(UUID fromId, UUID toId, String type, String certainty, Double weight, String source, String rationale, Long evidenceStart, Long evidenceEnd, UUID evidenceChunkId) {
        return temporalEdgeWriteRepository.upsertTemporalEdge(fromId, toId, type, certainty, weight, source, rationale, evidenceStart, evidenceEnd, evidenceChunkId);
    }
}