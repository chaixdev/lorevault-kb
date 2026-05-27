package com.lorevault.api.ingestion.resolution.consolidation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Generic connected-components clustering engine for entity consolidation.
 *
 * <p>Groups source entities into clusters based on overlapping identity key sets.
 * Two sources belong to the same cluster if they share at least one key,
 * transitively. Sources whose key extractor returns an empty set are silently
 * skipped — no separate {@code isResolvable} pre-filtering is needed.
 *
 * <p>The engine is type-agnostic — it works for any source type {@code S} at
 * any lifecycle level (Mention → ChapterEntity, ChapterEntity → BookEntity).
 *
 * <h3>Algorithm</h3>
 *
 * The engine maintains a map of key → cluster index. For each source:
 * <ol>
 *   <li>Extract identity keys via the caller-supplied {@code keyExtractor}.</li>
 *   <li>If no keys are present, skip the source entirely.</li>
 *   <li>Find all existing clusters that share at least one key with the source.</li>
 *   <li>If none, create a new cluster.</li>
 *   <li>If one, add the source to that cluster and union the key set.</li>
 *   <li>If multiple, add the source to the lowest-index cluster and merge all
 *       matching clusters into it, then rebuild the key index.</li>
 * </ol>
 *
 * <p>Output is deterministic — clusters appear in first-encountered order, and
 * sources within each cluster appear in the order they were added.
 *
 * @param <S> the source entity type (e.g. {@code LocationMention},
 *            {@code ChapterLocation})
 */
public final class ConsolidationEngine {

    private ConsolidationEngine() {
        // static utility
    }

    /**
     * Cluster sources into groups based on overlapping identity key sets.
     *
     * @param sources       ordered list of source entities
     * @param keyExtractor  function that derives identity keys from a source;
     *                      return an empty set to skip the source
     * @return list of clusters, each a list of sources in encounter order
     */
    public static <S> List<List<S>> cluster(
            List<S> sources,
            Function<S, Set<String>> keyExtractor
    ) {
        List<List<S>> clusters = new ArrayList<>();
        Map<String, Integer> keyToClusterIndex = new LinkedHashMap<>();

        for (S source : sources) {
            Set<String> rawKeys = keyExtractor.apply(source);
            if (rawKeys == null || rawKeys.isEmpty()) {
                continue;
            }

            Set<String> keys = new LinkedHashSet<>();
            for (String key : rawKeys) {
                if (key != null && !key.isBlank()) {
                    keys.add(key);
                }
            }
            if (keys.isEmpty()) {
                continue;
            }

            Set<Integer> matchingIndices = new LinkedHashSet<>();
            for (String key : keys) {
                Integer index = keyToClusterIndex.get(key);
                if (index != null) {
                    matchingIndices.add(index);
                }
            }

            if (matchingIndices.isEmpty()) {
                List<S> newCluster = new ArrayList<>();
                newCluster.add(source);
                int newIndex = clusters.size();
                clusters.add(newCluster);
                for (String key : keys) {
                    keyToClusterIndex.put(key, newIndex);
                }
                continue;
            }

            // Pick the lowest-index cluster as the base so it remains valid
            // after higher-index clusters are removed from the list.
            int baseIndex = java.util.Collections.min(matchingIndices);

            if (matchingIndices.size() > 1) {
                // Merge other matching clusters into base cluster first,
                // preserving encounter order (earlier clusters' sources come first).
                List<Integer> otherIndices = matchingIndices.stream()
                        .filter(idx -> idx != baseIndex)
                        .sorted(java.util.Comparator.reverseOrder())
                        .toList();
                for (Integer otherIndex : otherIndices) {
                    clusters.get(baseIndex).addAll(clusters.get(otherIndex));
                    clusters.remove((int) otherIndex);
                }
                // Then add the bridging source last within the merged cluster.
                clusters.get(baseIndex).add(source);

                keyToClusterIndex.clear();
                for (int i = 0; i < clusters.size(); i++) {
                    for (S s : clusters.get(i)) {
                        for (String key : keyExtractor.apply(s)) {
                            if (key != null && !key.isBlank()) {
                                keyToClusterIndex.put(key, i);
                            }
                        }
                    }
                }
            } else {
                clusters.get(baseIndex).add(source);
                for (String key : keys) {
                    keyToClusterIndex.putIfAbsent(key, baseIndex);
                }
            }
        }

        return clusters;
    }
}
