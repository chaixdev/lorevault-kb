package com.lorevault.api.graph.event.consolidation.book;

import com.lorevault.api.graph.event.persistence.ChapterEvent;
import static com.lorevault.api.common.error.ExceptionSanitizer.sanitizeMessage;

import com.lorevault.api.orchestration.job.IngestionFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-chapter ANN candidate generation for ChapterEvent similarity.
 *
 * <p>For each embedded ChapterEvent in a chapter this service queries the
 * {@code chapter_event_embedding_idx} vector index for the top-K nearest neighbours,
 * applies threshold filtering, deduplicates symmetric pairs, and caps the number of
 * candidates per source event.
 *
 * <p>Results are purely in-memory — no {@code BookEventCandidate} nodes are persisted in
 * Stage 4.  Stage 5 receives the candidate list and decides on merge vs. link actions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookEventAnnCandidateService {

    private final Neo4jClient neo4jClient;
    private final BookEventAnnProperties annProperties;

    /**
     * Generate ANN candidate pairs for all embedded ChapterEvents in a chapter.
     *
     * @param chapterEvents all ChapterEvent nodes for the chapter (including those just embedded)
     * @param chapterId     used for logging only
     * @return deduplicated list of candidate pairs above {@code annFloor}, ordered by score DESC
     */
    public List<BookEventCandidatePair> generateCandidates(List<ChapterEvent> chapterEvents, UUID chapterId) {
        List<ChapterEvent> embedded = chapterEvents.stream()
                .filter(e -> e.embedding() != null && e.embedding().length > 0)
                .toList();

        if (embedded.isEmpty()) {
            log.info("[EventAnn] No embedded ChapterEvents for chapter={}", chapterId);
            return List.of();
        }

        log.info("[EventAnn] Generating ANN candidates for {} embedded events chapter={}", embedded.size(), chapterId);

        // stable pair dedup: key -> best score
        Map<String, BookEventCandidatePair> pairMap = new LinkedHashMap<>();

        for (ChapterEvent source : embedded) {
            List<BookEventCandidatePair> neighbours = queryNeighbours(source, chapterId);
            for (BookEventCandidatePair pair : neighbours) {
                String key = pairKey(pair);
                BookEventCandidatePair existing = pairMap.get(key);
                if (existing == null || pair.annScore() > existing.annScore()) {
                    pairMap.put(key, pair);
                }
            }
        }

        // cap per source event
        List<BookEventCandidatePair> result = capPerSourceEvent(new ArrayList<>(pairMap.values()));

        log.info("[EventAnn] {} candidate pairs generated for chapter={}", result.size(), chapterId);
        return result;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private List<BookEventCandidatePair> queryNeighbours(ChapterEvent source, UUID chapterId) {
        int limit = annProperties.topK() * annProperties.oversampleFactor();
        List<Double> embeddingList = toDoubleList(source.embedding());

        // Guard: reject source vectors whose dimension does not match the configured index dimension.
        // A mismatch would cause Neo4j to throw a dimension error on the ANN call.
        if (source.embedding().length != annProperties.embeddingDimension()) {
            log.warn(
                    "[EventAnn] Skipping source event with wrong embedding dimension: source={} chapter={} actual={} expected={}",
                    source.id(), chapterId, source.embedding().length, annProperties.embeddingDimension()
            );
            return List.of();
        }

        try {
            List<BookEventCandidatePair> pairs = neo4jClient.query("""
                        MATCH (:Chapter {id: $chapterId})-[:IN_BOOK]->(book:Book)
                        CALL db.index.vector.queryNodes($indexName, $limit, $embedding)
                        YIELD node, score
                        WITH book, node AS candidate, score
                        MATCH (:Chapter {id: candidate.chapterId})-[:IN_BOOK]->(book)
                        WHERE candidate.id <> $sourceId
                          AND score >= $annFloor
                        WITH candidate, vector.similarity.cosine(candidate.embedding, $embedding) AS cosineScore
                        WHERE cosineScore >= $annFloor
                        RETURN candidate.id AS candidateId, cosineScore AS score
                        ORDER BY score DESC
                        LIMIT $topK
                        """)
                    .bind(ChapterEvent.VECTOR_INDEX_NAME).to("indexName")
                    .bind(limit).to("limit")
                    .bind(embeddingList).to("embedding")
                    .bind(source.id().toString()).to("sourceId")
                    .bind(chapterId.toString()).to("chapterId")
                    .bind(annProperties.annFloor()).to("annFloor")
                    .bind(annProperties.topK()).to("topK")
                    .fetchAs(BookEventCandidatePair.class)
                    .mappedBy((ts, record) -> {
                        UUID candidateId = UUID.fromString(record.get("candidateId").asString());
                        double score = record.get("score").asDouble();
                        return BookEventCandidatePair.of(source.id(), candidateId, score);
                    })
                    .all()
                    .stream()
                    .collect(Collectors.toList());

            if (pairs.size() < annProperties.topK()) {
                log.warn(
                        "[EventAnn] Recall may be insufficient: only {}/{} candidates survived book-filter for source={} chapter={} — consider increasing oversampleFactor (current={})",
                        pairs.size(), annProperties.topK(), source.id(), chapterId, annProperties.oversampleFactor()
                );
            }

            return pairs;
        } catch (Exception e) {
            log.warn("[EventAnn] ANN query failed for source={} chapter={} error={}", source.id(), chapterId, sanitizeMessage(e));
            throw annQueryFailure(source.id(), chapterId, e);
        }
    }

    private BookEventAnnCandidateException annQueryFailure(UUID sourceId, UUID chapterId, Exception cause) {
        IngestionFailure failure = IngestionFailure.builder(
                        "EVENT_ANN_QUERY_FAILED",
                        "ChapterEvent ANN query failed while generating book-event candidates")
                .exceptionType(BookEventAnnCandidateException.class.getSimpleName())
                .stage("EVENT_EMBEDDING")
                .detail("sourceEventId", sourceId)
                .detail("chapterId", chapterId)
                .build();
        return new BookEventAnnCandidateException(failure, cause);
    }

    /**
     * Cap each source event to at most {@code maxCandidatesPerEvent} neighbours.
     * Iterates through pairs sorted by score DESC and counts neighbours per node.
     */
    private List<BookEventCandidatePair> capPerSourceEvent(List<BookEventCandidatePair> pairs) {
        int max = annProperties.maxCandidatesPerEvent();

        // Sort by score descending so we keep the best-scoring pairs first
        pairs.sort((a, b) -> Double.compare(b.annScore(), a.annScore()));

        Map<UUID, Integer> countPerEvent = new HashMap<>();
        List<BookEventCandidatePair> result = new ArrayList<>();

        for (BookEventCandidatePair pair : pairs) {
            int count1 = countPerEvent.getOrDefault(pair.eventId1(), 0);
            int count2 = countPerEvent.getOrDefault(pair.eventId2(), 0);
            if (count1 < max && count2 < max) {
                result.add(pair);
                countPerEvent.merge(pair.eventId1(), 1, Integer::sum);
                countPerEvent.merge(pair.eventId2(), 1, Integer::sum);
            }
        }

        return result;
    }

    private static String pairKey(BookEventCandidatePair pair) {
        return pair.eventId1() + ":" + pair.eventId2();
    }

    private static List<Double> toDoubleList(double[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (double v : arr) list.add(v);
        return list;
    }
}
