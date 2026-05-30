package com.lorevault.api.orchestration.consolidation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConsolidationEngineTest {

    private final ConsolidationEngine engine = new ConsolidationEngine();

    @Nested
    class SingleKeyClustering {

        @Test
        @DisplayName("empty input returns empty clusters")
        void emptyInput() {
            var result = engine.cluster(List.<String>of(), (String name) -> NameKeys.from(name));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("single source with one key produces single cluster")
        void singleSource() {
            var result = engine.cluster(
                    List.of("alice"),
                    (String name) -> NameKeys.from(name));
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("alice");
        }

        @Test
        @DisplayName("distinct keys produce separate clusters")
        void distinctKeys() {
            var result = engine.cluster(
                    List.of("alice", "bob", "carol"),
                    (String name) -> NameKeys.from(name));
            assertThat(result).hasSize(3);
            assertThat(result.get(0)).containsExactly("alice");
            assertThat(result.get(1)).containsExactly("bob");
            assertThat(result.get(2)).containsExactly("carol");
        }

        @Test
        @DisplayName("transitive merge: A-B and B-C merge into one cluster")
        void transitiveMerge() {
            // "alice" has keys {"alice", "ali"}
            // "bob" has keys {"bob", "ali"} — shares "ali" with alice
            // "carol" has keys {"carol", "bob"} — shares "bob" with bob
            // All three should merge into one cluster
            var result = engine.cluster(
                    List.of("alice", "bob", "carol"),
                    name -> switch (name) {
                        case "alice" -> Set.of("alice", "ali");
                        case "bob" -> Set.of("bob", "ali");
                        case "carol" -> Set.of("carol", "bob");
                        default -> Set.of(name);
                    });
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("alice", "bob", "carol");
        }

        @Test
        @DisplayName("bridging source merges two existing clusters")
        void bridgingSource() {
            // "alice" has keys {"alice"}
            // "bob" has keys {"bob"}
            // "carol" has keys {"alice", "bob"} — bridges both
            var result = engine.cluster(
                    List.of("alice", "bob", "carol"),
                    name -> switch (name) {
                        case "alice" -> Set.of("alice");
                        case "bob" -> Set.of("bob");
                        case "carol" -> Set.of("alice", "bob");
                        default -> Set.of(name);
                    });
assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactlyInAnyOrder("alice", "bob", "carol");
        }

        @Test
        @DisplayName("no overlap produces separate clusters")
        void noOverlap() {
            var result = engine.cluster(
                    List.of("alice", "bob"),
                    (String name) -> Set.of(name + "-key"));
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class EmptyKeyHandling {

        @Test
        @DisplayName("source with null key set is skipped")
        void nullKeySet() {
            var result = engine.cluster(
                    List.of("alice"),
                    (String name) -> null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("source with empty key set is skipped")
        void emptyKeySet() {
            var result = engine.cluster(
                    List.of("alice"),
                    (String name) -> Set.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("source with blank-only keys is skipped")
        void blankKeys() {
            var result = engine.cluster(
                    List.of("alice"),
                    (String name) -> NameKeys.from("   "));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("sources with blank keys are skipped, valid ones clustered")
        void mixedBlankAndValid() {
            var result = engine.cluster(
                    List.of("alice", "bob", "carol"),
                    name -> switch (name) {
                        case "alice" -> NameKeys.from("alice");
                        case "bob" -> NameKeys.from("   "); // blank → skipped
                        case "carol" -> NameKeys.from("carol");
                        default -> Set.of();
                    });
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly("alice");
            assertThat(result.get(1)).containsExactly("carol");
        }
    }

    @Nested
    class EncounterOrder {

        @Test
        @DisplayName("cluster order follows encounter order of first source")
        void encounterOrder() {
            var result = engine.cluster(
                    List.of("zebra", "alpha", "middle"),
                    (String name) -> NameKeys.from(name));
            assertThat(result).hasSize(3);
            assertThat(result.get(0)).containsExactly("zebra");
            assertThat(result.get(1)).containsExactly("alpha");
            assertThat(result.get(2)).containsExactly("middle");
        }
    }

    @Nested
    class NameBasedKeys {

        @Test
        @DisplayName("NameKeys.from with aliases produces multi-key set")
        void nameKeysWithAliases() {
            var keys = NameKeys.from("kevin jenkins", List.of("jenkins", "kevin"));
            assertThat(keys).containsExactlyInAnyOrder("kevin jenkins", "jenkins", "kevin");
        }

        @Test
        @DisplayName("NameKeys.from with null aliases produces single-key set")
        void nameKeysNullAliases() {
            var keys = NameKeys.from("kevin jenkins", null);
            assertThat(keys).containsExactly("kevin jenkins");
        }

        @Test
        @DisplayName("NameKeys.from with blank name and aliases produces empty set")
        void nameKeysBlankName() {
            var keys = NameKeys.from("   ", List.of("alias"));
            assertThat(keys).containsExactly("alias");
        }

        @Test
        @DisplayName("NameKeys.from single arg produces single key")
        void nameKeysSingleArg() {
            var keys = NameKeys.from("gandalf");
            assertThat(keys).containsExactly("gandalf");
        }

        @Test
        @DisplayName("NameKeys.normalizeName handles whitespace and case")
        void normalizeName() {
            assertThat(NameKeys.normalizeName("  Kevin   Jenkins  ")).isEqualTo("kevin jenkins");
            assertThat(NameKeys.normalizeName(null)).isNull();
            assertThat(NameKeys.normalizeName("   ")).isNull();
        }
    }

    @Nested
    class MultiKeyMerge {

        @Test
        @DisplayName("alias overlap merges two sources into one cluster")
        void aliasOverlapMerge() {
            // "kevin jenkins" has keys {"kevin jenkins", "jenkins"}
            // "jenkins" has keys {"jenkins"} — shares "jenkins"
            var result = engine.cluster(
                    List.of("kevin jenkins", "jenkins"),
                    (String name) -> NameKeys.from(name, name.equals("kevin jenkins") ? List.of("jenkins") : List.of()));
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("kevin jenkins", "jenkins");
        }

        @Test
        @DisplayName("transitive alias merge across three sources")
        void transitiveAliasMerge() {
            // "kevin jenkins" → keys {"kevin jenkins", "jenkins"}
            // "jenkins" → keys {"jenkins"}
            // "purveyor jenkins" → keys {"purveyor jenkins", "jenkins"}
            // All three share "jenkins" → one cluster
            var result = engine.cluster(
                    List.of("kevin jenkins", "jenkins", "purveyor jenkins"),
                    (String name) -> NameKeys.from(name, List.of("jenkins")));
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactlyInAnyOrder("kevin jenkins", "jenkins", "purveyor jenkins");
        }
    }
}