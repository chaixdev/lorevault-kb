package com.lorevault.api.orchestration.consolidation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Component;

/**
 * Generic connected-components clustering engine for entity consolidation.
 *
 * <p>Groups sources by shared identity keys. If two sources share any key,
 * they are placed in the same cluster. Transitive closure is applied: if
 * A shares a key with B, and B shares a key with C, then A, B, and C
 * are all in the same cluster.
 *
 * <p>Sources with empty or null key sets are skipped (not included in any cluster).
 *
 * <p>Cluster order is deterministic: sources are processed in encounter order,
 * and clusters are ordered by the first source that started each cluster.
 */
@Component
public class ConsolidationEngine {

    /**
     * Cluster sources by shared identity keys using connected-components.
     *
     * @param sources       the items to cluster
     * @param keyExtractor  function that extracts identity keys from each source
     * @param <S>           source type
     * @return list of clusters, each cluster is a list of sources in encounter order
     */
    public <S> List<List<S>> cluster(List<S> sources, Function<S, Set<String>> keyExtractor) {
        List<List<S>> clusters = new ArrayList<>();
        Map<String, Integer> clusterIndexByKey = new LinkedHashMap<>();

        for (S source : sources) {
            Set<String> keys = keyExtractor.apply(source);
            if (keys == null || keys.isEmpty()) {
                continue;
            }

            Set<Integer> matchingIndexes = new LinkedHashSet<>();
            for (String key : keys) {
                Integer index = clusterIndexByKey.get(key);
                if (index != null) {
                    matchingIndexes.add(index);
                }
            }

            if (matchingIndexes.isEmpty()) {
                List<S> newCluster = new ArrayList<>();
                newCluster.add(source);
                clusters.add(newCluster);
                int newIndex = clusters.size() - 1;
                for (String key : keys) {
                    clusterIndexByKey.put(key, newIndex);
                }
                continue;
            }

            // Merge into the lowest-index cluster
            int baseIndex = matchingIndexes.iterator().next();
            clusters.get(baseIndex).add(source);

            // Merge other matching clusters into the base cluster
            List<Integer> otherIndexes = matchingIndexes.stream()
                    .skip(1)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Integer otherIndex : otherIndexes) {
                clusters.get(baseIndex).addAll(clusters.get(otherIndex));
                clusters.remove((int) otherIndex);
            }

            // Rebuild the key-to-index map after merge
            clusterIndexByKey.clear();
            for (int i = 0; i < clusters.size(); i++) {
                for (S s : clusters.get(i)) {
                    Set<String> sKeys = keyExtractor.apply(s);
                    if (sKeys != null) {
                        for (String key : sKeys) {
                            clusterIndexByKey.put(key, i);
                        }
                    }
                }
            }
        }

        return clusters;
    }
}