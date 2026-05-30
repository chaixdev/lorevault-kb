package com.lorevault.api.orchestration.pipeline;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Static topology of the ingestion pipeline DAG.
 *
 * <p>Defines which stages trigger which downstream stages and exposes utilities
 * for the coordinator: root discovery, transitive downstream computation, and
 * fan-in barrier evaluation.
 *
 * <p>This is the single source of truth for DAG structure. Handlers no longer
 * reference other handler event types directly — the coordinator dispatches
 * based on this topology.
 *
 * <h3>DAG structure</h3>
 * <pre>
 * SCENE_SEGMENTATION (root)
 *   ├── TRIGGERS → CHUNKING
 *   ├── TRIGGERS → CHAPTER_INDIVIDUAL_CONSOLIDATION
 *   ├── TRIGGERS → CHAPTER_COLLECTIVE_CONSOLIDATION
 *   ├── TRIGGERS → CHAPTER_LOCATION_CONSOLIDATION
 *   ├── TRIGGERS → CHAPTER_OBJECT_CONSOLIDATION
 *   └── TRIGGERS → CHAPTER_EVENT_CONSOLIDATION
 *
 * CHUNKING → EMBEDDING
 *
 * CHAPTER_INDIVIDUAL_CONSOLIDATION → BOOK_INDIVIDUAL_CONSOLIDATION
 * CHAPTER_COLLECTIVE_CONSOLIDATION → BOOK_COLLECTIVE_CONSOLIDATION
 * CHAPTER_LOCATION_CONSOLIDATION   → BOOK_LOCATION_CONSOLIDATION
 * CHAPTER_OBJECT_CONSOLIDATION     → BOOK_OBJECT_CONSOLIDATION
 * CHAPTER_EVENT_CONSOLIDATION      → CHAPTER_EVENT_EMBEDDING
 *
 * CHAPTER_EVENT_EMBEDDING       → BOOK_EVENT_CANDIDATE_GENERATION
 *
 * EMBEDDING                    → INGESTION_COMPLETE
 * BOOK_INDIVIDUAL_CONSOLIDATION    → INGESTION_COMPLETE
 * BOOK_COLLECTIVE_CONSOLIDATION    → INGESTION_COMPLETE
 * BOOK_LOCATION_CONSOLIDATION      → INGESTION_COMPLETE
 * BOOK_OBJECT_CONSOLIDATION        → INGESTION_COMPLETE
 * BOOK_EVENT_CANDIDATE_GENERATION → INGESTION_COMPLETE
 * </pre>
 */
public final class StageDag {

    private final Map<StageKey, List<StageKey>> children;
    private final Map<StageKey, List<StageKey>> parents;
    private final Set<StageKey> roots;

    public StageDag() {
        Map<StageKey, List<StageKey>> c = new LinkedHashMap<>();

        // Root triggers
        c.put(StageKey.SCENE_SEGMENTATION, List.of(
                StageKey.CHUNKING,
                StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION,
                StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,
                StageKey.CHAPTER_LOCATION_CONSOLIDATION,
                StageKey.CHAPTER_OBJECT_CONSOLIDATION,
                StageKey.CHAPTER_EVENT_CONSOLIDATION
        ));

        // Content lane
        c.put(StageKey.CHUNKING, List.of(StageKey.EMBEDDING));

        // Entity resolution → reduction
        c.put(StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, List.of(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION));
        c.put(StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,  List.of(StageKey.BOOK_COLLECTIVE_CONSOLIDATION));
        c.put(StageKey.CHAPTER_LOCATION_CONSOLIDATION,    List.of(StageKey.BOOK_LOCATION_CONSOLIDATION));
        c.put(StageKey.CHAPTER_OBJECT_CONSOLIDATION,      List.of(StageKey.BOOK_OBJECT_CONSOLIDATION));

        // Event lane
        c.put(StageKey.CHAPTER_EVENT_CONSOLIDATION,    List.of(StageKey.CHAPTER_EVENT_EMBEDDING));
        c.put(StageKey.CHAPTER_EVENT_EMBEDDING,     List.of(StageKey.BOOK_EVENT_CANDIDATE_GENERATION));

        // Fan-in to terminal barrier
        c.put(StageKey.EMBEDDING,                      List.of(StageKey.INGESTION_COMPLETE));
        c.put(StageKey.BOOK_INDIVIDUAL_CONSOLIDATION,      List.of(StageKey.INGESTION_COMPLETE));
        c.put(StageKey.BOOK_COLLECTIVE_CONSOLIDATION,      List.of(StageKey.INGESTION_COMPLETE));
        c.put(StageKey.BOOK_LOCATION_CONSOLIDATION,        List.of(StageKey.INGESTION_COMPLETE));
        c.put(StageKey.BOOK_OBJECT_CONSOLIDATION,          List.of(StageKey.INGESTION_COMPLETE));
        c.put(StageKey.BOOK_EVENT_CANDIDATE_GENERATION, List.of(StageKey.INGESTION_COMPLETE));

        // Terminal barrier has no children
        c.put(StageKey.INGESTION_COMPLETE, List.of());

        this.children = Collections.unmodifiableMap(c);
        this.parents = computeParents(c);
        this.roots = computeRoots();
    }

    /** Stages with no parents — the spark for a fresh job. */
    public Set<StageKey> roots() {
        return roots;
    }

    /** Which stages are triggered when {@code stage} completes? */
    public List<StageKey> childrenOf(StageKey stage) {
        return children.getOrDefault(stage, List.of());
    }

    /** Which stages must complete before {@code stage} can be triggered? */
    public List<StageKey> parentsOf(StageKey stage) {
        return parents.getOrDefault(stage, List.of());
    }

    /**
     * All stages reachable from {@code stage} via {@code [:TRIGGERS]} edges,
     * including {@code stage} itself. Used for cascade invalidation during rerun.
     */
    public Set<StageKey> transitiveDownstream(StageKey stage) {
        Set<StageKey> result = new LinkedHashSet<>();
        Deque<StageKey> queue = new ArrayDeque<>();
        queue.add(stage);
        while (!queue.isEmpty()) {
            StageKey current = queue.poll();
            if (result.add(current)) {
                queue.addAll(childrenOf(current));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the invalidated stages in deepest-first topological order.
     * Used during cascade invalidation to delete children before parents.
     */
    public List<StageKey> topologicalDepthDescending(Set<StageKey> stages) {
        // BFS from roots gives topological order; reverse for deepest-first
        List<StageKey> result = new ArrayList<>();
        Set<StageKey> visited = new LinkedHashSet<>();
        Deque<StageKey> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            StageKey current = queue.poll();
            if (!visited.add(current)) continue;
            if (stages.contains(current)) {
                result.add(current);
            }
            queue.addAll(childrenOf(current));
        }
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    /** Validate all StageKey values are reachable from at least one root. */
    public Set<StageKey> validateConnectivity() {
        Set<StageKey> reachable = new LinkedHashSet<>();
        Deque<StageKey> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            StageKey current = queue.poll();
            if (reachable.add(current)) {
                queue.addAll(childrenOf(current));
            }
        }
        return EnumSet.complementOf(EnumSet.copyOf(reachable));
    }

    // ── private helpers ──────────────────────────────────────────────

    private static Map<StageKey, List<StageKey>> computeParents(
            Map<StageKey, List<StageKey>> children) {
        Map<StageKey, List<StageKey>> result = new LinkedHashMap<>();
        for (Map.Entry<StageKey, List<StageKey>> entry : children.entrySet()) {
            StageKey parent = entry.getKey();
            for (StageKey child : entry.getValue()) {
                result.computeIfAbsent(child, k -> new ArrayList<>()).add(parent);
            }
        }
        return Collections.unmodifiableMap(
                result.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> Collections.unmodifiableList(e.getValue()),
                                (a, b) -> a,
                                LinkedHashMap::new)));
    }

    private Set<StageKey> computeRoots() {
        Set<StageKey> allStages = EnumSet.allOf(StageKey.class);
        Set<StageKey> hasParents = parents.keySet();
        Set<StageKey> result = EnumSet.copyOf(allStages);
        result.removeAll(hasParents);
        return Collections.unmodifiableSet(result);
    }
}
