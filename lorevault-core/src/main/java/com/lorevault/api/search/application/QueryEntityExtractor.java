package com.lorevault.api.search.application;

import com.lorevault.api.search.infrastructure.KnownEntityTrie;
import com.lorevault.api.search.infrastructure.OpenNlpNounPhraseExtractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Extracts entity candidates from a query string for use in search re-ranking.
 *
 * <p>Two strategies run in sequence:</p>
 * <ol>
 *   <li><b>Strategy A — Aho-Corasick trie</b>: scans the query against all known
 *       {@code IndividualMention} and {@code LocationMention} display names loaded
 *       from Neo4j at startup. O(n) regardless of how many names exist. ~0.02ms.</li>
 *   <li><b>Strategy B — OpenNLP NP chunking</b>: tokenize → POS → chunk to find
 *       noun phrases not already caught by Strategy A. Requires model files on the
 *       classpath; gracefully disabled if models are absent. ~2ms.</li>
 * </ol>
 *
 * <p>Total budget: 2–4ms on a warm JVM, well within the 20ms query budget.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryEntityExtractor {

    private final KnownEntityTrie           knownEntityTrie;
    private final OpenNlpNounPhraseExtractor openNlpExtractor;

    /**
     * Extracts entity candidates from the given query.
     *
     * @param query the user's question string (5–30 words expected)
     * @return extraction result with known entities and any discovered noun phrases
     */
    public ExtractionResult extract(String query) {
        if (query == null || query.isBlank()) {
            return ExtractionResult.empty();
        }

        long start = System.nanoTime();

        // Strategy A: known-name trie (always runs)
        List<String> known = knownEntityTrie.match(query);

        // Strategy B: OpenNLP NP chunking (runs only when models are present)
        Set<String> knownSet = ExtractionResult.of(known, List.of()).allCandidates();
        List<String> discovered = openNlpExtractor.extractNounPhrases(query, knownSet);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.debug("Entity extraction: {}ms — known={}, discovered={} — query='{}'",
                elapsedMs, known, discovered, query);

        return ExtractionResult.of(known, discovered);
    }

    /**
     * Rebuilds the known-entity trie from the current Neo4j data.
     * Call this after a new ingestion job completes to pick up freshly
     * persisted entity names without restarting the application.
     */
    public void refreshKnownEntities() {
        log.info("QueryEntityExtractor: refreshing known-entity trie");
        knownEntityTrie.refresh();
    }
}
