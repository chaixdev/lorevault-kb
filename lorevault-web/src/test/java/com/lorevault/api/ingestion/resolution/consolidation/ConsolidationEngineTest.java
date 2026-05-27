package com.lorevault.api.ingestion.resolution.consolidation;

import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConsolidationEngine")
class ConsolidationEngineTest {

    @Test
    @DisplayName("Returns empty list for empty input")
    void emptyInput() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of(),
                $ -> Set.of($)
        );
        assertThat(clusters).isEmpty();
    }

    @Test
    @DisplayName("Returns single cluster when all sources share a common key")
    void singleKeySingleCluster() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("a", "b", "c"),
                $ -> Set.of("shared")
        );
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("Splits sources into clusters by distinct keys")
    void distinctKeysSeparateClusters() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("x", "y", "z"),
                $ -> Set.of($)
        );
        assertThat(clusters).hasSize(3);
        assertThat(clusters.get(0)).containsExactly("x");
        assertThat(clusters.get(1)).containsExactly("y");
        assertThat(clusters.get(2)).containsExactly("z");
    }

    @Test
    @DisplayName("Merges clusters transitively through bridging keys")
    void transitiveMergeThroughBridgingKeys() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("A", "B", "C"),
                $ -> switch ($) {
                    case "A" -> Set.of("key1", "key2");
                    case "B" -> Set.of("key2", "key3");
                    case "C" -> Set.of("key3", "key4");
                    default -> Set.of();
                }
        );
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("Merges clusters when a later source bridges two previously separate clusters")
    void bridgingSourceMergesTwoExistingClusters() {
        // A and C start separate, B bridges them via shared alias
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("A-sword", "B-moonblade", "C-dagger"),
                $ -> switch ($) {
                    case "A-sword" -> Set.of("silver_sword", "moonblade");
                    case "B-moonblade" -> Set.of("moonblade");
                    case "C-dagger" -> Set.of("ceremonial_dagger", "moonblade");
                    default -> Set.of();
                }
        );
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).containsExactly("A-sword", "B-moonblade", "C-dagger");
    }

    @Test
    @DisplayName("Does not merge clusters when no keys overlap")
    void noOverlapSeparateClusters() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("A", "B", "C"),
                $ -> switch ($) {
                    case "A" -> Set.of("key_a1", "key_a2");
                    case "B" -> Set.of("key_b");
                    case "C" -> Set.of("key_c");
                    default -> Set.of();
                }
        );
        assertThat(clusters).hasSize(3);
    }

    @Test
    @DisplayName("Skips sources whose key extractor returns an empty set")
    void skipsSourcesWithEmptyKeys() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("valid", "skip-me", "also-good"),
                $ -> {
                    if ("skip-me".equals($)) {
                        return Set.of();
                    }
                    return Set.of($);
                }
        );
        assertThat(clusters).hasSize(2);
        assertThat(clusters.stream().flatMap(List::stream))
                .doesNotContain("skip-me")
                .containsExactly("valid", "also-good");
    }

    @Test
    @DisplayName("Skips sources whose key extractor returns null")
    void skipsSourcesWithNullKeys() {
        List<String> sources = new java.util.ArrayList<>();
        sources.add("keep");
        sources.add(null);
        List<List<String>> clusters = ConsolidationEngine.cluster(
                sources,
                $ -> $ == null ? null : Set.of($)
        );
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).containsExactly("keep");
    }

    @Test
    @DisplayName("Skips sources whose keys contain only blank strings")
    void skipsSourcesWithBlankKeys() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("  ", "\t"),
                $ -> Set.of($)
        );
        assertThat(clusters).isEmpty();
    }

    @Test
    @DisplayName("Preserves encounter order within each cluster")
    void preservesEncounterOrderWithinCluster() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("first", "second", "third"),
                $ -> Set.of("same")
        );
        assertThat(clusters.get(0)).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("Returns clusters in first-encountered order")
    void returnsClustersInFirstEncounterOrder() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("A", "B", "C", "D"),
                $ -> switch ($) {
                    case "A", "C" -> Set.of("group1");
                    case "B", "D" -> Set.of("group2");
                    default -> Set.of();
                }
        );
        // A appears first → group1 is cluster 0. B appears next → group2 is cluster 1.
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0)).containsExactly("A", "C");
        assertThat(clusters.get(1)).containsExactly("B", "D");
    }

    @Test
    @DisplayName("Handles name-based keys like real entity resolution")
    void nameBasedKeys() {
        // Simulate: Rivendell (keys: rivendell, imladris), Last Homely House (keys: the last homely house, rivendell)
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("Rivendell", "Last Homely House", "Shire"),
                $ -> switch ($) {
                    case "Rivendell" -> Set.of("rivendell", "imladris");
                    case "Last Homely House" -> Set.of("the last homely house", "rivendell");
                    case "Shire" -> Set.of("the shire");
                    default -> Set.of();
                }
        );
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0)).containsExactly("Rivendell", "Last Homely House");
        assertThat(clusters.get(1)).containsExactly("Shire");
    }

    @Test
    @DisplayName("All sources skipped when all keys are blank")
    void allSkippedWhenAllKeysBlank() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("  ", "", "\t"),
                $ -> Set.of($)
        );
        assertThat(clusters).isEmpty();
    }

    @Test
    @DisplayName("Multikey source merges into existing cluster when any key matches")
    void multikeySourceWithSingleKeyMatch() {
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("A-alias", "B-main"),
                $ -> switch ($) {
                    case "A-alias" -> Set.of("moonblade");
                    case "B-main" -> Set.of("silver_sword", "moonblade");
                    default -> Set.of();
                }
        );
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).containsExactly("A-alias", "B-main");
    }

    @Test
    @DisplayName("Multikey merge preserves all keys from merged clusters in index")
    void multikeyMergePreservesIndexForNewKeyLookups() {
        // A enters cluster 0 with keys {k1, k2}
        // B enters cluster 1 with keys {k3}
        // C has keys {k2, k3} — should merge clusters 0 and 1
        // D has key {k4} — should go into separate cluster since all existing keys are tracked
        List<List<String>> clusters = ConsolidationEngine.cluster(
                List.of("A", "B", "C", "D"),
                $ -> switch ($) {
                    case "A" -> Set.of("k1", "k2");
                    case "B" -> Set.of("k3");
                    case "C" -> Set.of("k2", "k3");
                    case "D" -> Set.of("k4");
                    default -> Set.of();
                }
        );
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0)).containsExactly("A", "B", "C");
        assertThat(clusters.get(1)).containsExactly("D");
    }
}
