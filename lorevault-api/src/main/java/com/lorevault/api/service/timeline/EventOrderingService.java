package com.lorevault.api.service.timeline;

import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventOrderingService {

    private final Neo4jContentPersistenceAdapter contentPort;

    /**
     * Order events within a chapter using precedence edges first, then sceneIndex and UUID as stable tie-breakers.
     */
    public List<Scene> orderChapterEvents(UUID chapterId) {
        List<Scene> scenes = new ArrayList<>(contentPort.findScenesByChapterId(chapterId));
        if (scenes.isEmpty()) return List.of();

        Map<UUID, Scene> byId = scenes.stream().collect(Collectors.toMap(Scene::getId, s -> s));
        Map<UUID, List<UUID>> adj = new HashMap<>();
        Map<UUID, Integer> indeg = new HashMap<>();
        byId.keySet().forEach(id -> { adj.put(id, new ArrayList<>()); indeg.put(id, 0); });

        for (var e : contentPort.findChapterTemporalEdges(chapterId)) {
            UUID from = e.getKey();
            UUID to = e.getValue();
            if (byId.containsKey(from) && byId.containsKey(to)) {
                adj.get(from).add(to);
                indeg.put(to, indeg.get(to) + 1);
            }
        }

        Comparator<UUID> tie = Comparator
                .comparing((UUID id) -> Optional.ofNullable(byId.get(id).getSceneIndex()).orElse(Integer.MAX_VALUE))
                .thenComparing(UUID::toString);

        PriorityQueue<UUID> q = new PriorityQueue<>(tie);
        indeg.forEach((id, d) -> { if (d == 0) q.add(id); });

        List<UUID> order = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        while (!q.isEmpty()) {
            UUID u = q.poll();
            if (!seen.add(u)) continue;
            order.add(u);
            for (UUID v : adj.get(u)) {
                int d = indeg.get(v) - 1;
                indeg.put(v, d);
                if (d == 0) q.add(v);
            }
        }

        if (order.size() < byId.size()) {
            byId.keySet().stream()
                .filter(id -> !seen.contains(id))
                .sorted(tie)
                .forEach(order::add);
        }

        return order.stream().map(byId::get).collect(Collectors.toList());
    }

    /**
     * Order events across chapters up to N by concatenating per-chapter orders by chapterNumber sequence.
     */
    public List<Scene> orderBookEventsUpToChapter(UUID bookId, int uptoChapterNumber) {
        List<UUID> chapterIds = contentPort.findChapterIdsUpTo(bookId, uptoChapterNumber);
        List<Scene> all = new ArrayList<>();
        for (UUID chapterId : chapterIds) {
            all.addAll(orderChapterEvents(chapterId));
        }
        return all;
    }
}
