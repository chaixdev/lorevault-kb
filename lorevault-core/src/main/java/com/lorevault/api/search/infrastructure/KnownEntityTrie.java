package com.lorevault.api.search.infrastructure;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Aho-Corasick trie for matching known entity names (Strategy A).
 *
 * <p>The trie is built once at application startup from all {@code IndividualMention}
 * and {@code LocationMention} display names stored in Neo4j.
 * It is thread-safe after construction; call {@link #refresh()} to rebuild
 * when new content is ingested.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KnownEntityTrie {

    private final EntityNameRepository nameRepository;

    /**
     * Atomic reference lets us swap the trie without locking query threads.
     * A null reference means the trie hasn't been built yet or failed to build.
     */
    private final AtomicReference<Trie> trieRef = new AtomicReference<>(null);

    @PostConstruct
    void initialize() {
        refresh();
    }

    /**
     * Rebuilds the trie from the current Neo4j data.
     * Safe to call from a background thread after new ingestion completes.
     */
    public void refresh() {
        try {
            Collection<String> individuals = nameRepository.loadIndividualNames();
            Collection<String> locations = nameRepository.loadLocationNames();

            int total = individuals.size() + locations.size();
            if (total == 0) {
                log.info("KnownEntityTrie: no entity names found in Neo4j — trie is empty");
                trieRef.set(Trie.builder().build());
                return;
            }

            Trie.TrieBuilder builder = Trie.builder()
                    .ignoreCase()
                    .onlyWholeWords();

            individuals.forEach(builder::addKeyword);
            locations.forEach(builder::addKeyword);

            trieRef.set(builder.build());
            log.info("KnownEntityTrie: built with {} keywords ({} individuals, {} locations)",
                    total, individuals.size(), locations.size());
        } catch (Exception e) {
            log.warn("KnownEntityTrie: failed to build trie — entity matching disabled: {}", e.getMessage());
            trieRef.set(Trie.builder().build());
        }
    }

    /**
     * Scans {@code query} and returns the matched entity names in encounter order.
     *
     * @param query the user's question string
     * @return matched known entity names; empty list if trie is empty or nothing matches
     */
    public List<String> match(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Trie trie = trieRef.get();
        if (trie == null) {
            return List.of();
        }

        Collection<Emit> emits = trie.parseText(query);
        if (emits.isEmpty()) {
            return List.of();
        }

        List<String> matched = new ArrayList<>(emits.size());
        for (Emit emit : emits) {
            matched.add(emit.getKeyword());
        }
        return matched;
    }

    /** Returns {@code true} if the trie contains at least one keyword. */
    boolean isPopulated() {
        Trie t = trieRef.get();
        return t != null;
    }
}
