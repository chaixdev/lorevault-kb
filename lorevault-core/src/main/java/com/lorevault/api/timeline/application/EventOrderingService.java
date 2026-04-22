package com.lorevault.api.timeline.application;

import com.lorevault.api.content.domain.Scene;
import com.lorevault.api.content.infrastructure.ChapterReadRepository;
import com.lorevault.api.content.infrastructure.SceneGraphRepository;
import com.lorevault.api.timeline.infrastructure.TemporalReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventOrderingService {

    private final SceneGraphRepository sceneRepo;
    private final ChapterReadRepository chapterReadRepo;
    private final TemporalReadRepository temporalReadRepo;

    /**
     * Order events within a chapter using TEMPORAL precedence edges first,
     * then sceneIndex and UUID as stable tie-breakers.
     */
    public List<Scene> orderChapterEvents(UUID chapterId) {
        List<Scene> scenes = new ArrayList<>(sceneRepo.findByChapterId(chapterId));
        return orderScenes(scenes, temporalReadRepo.findChapterEventEdges(chapterId), Map.of());
    }

    /**
     * Order events across chapters up to N using a single temporal graph over all in-scope scenes.
     */
    public List<Scene> orderBookEventsUpToChapter(UUID bookId, int uptoChapterNumber) {
        List<UUID> chapterIds = chapterReadRepo.findChapterIdsUpTo(bookId, uptoChapterNumber);
        if (chapterIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> chapterOrder = new HashMap<>();
        List<Scene> allScenes = new ArrayList<>();
        for (int i = 0; i < chapterIds.size(); i++) {
            UUID chapterId = chapterIds.get(i);
            chapterOrder.put(chapterId, i);
            allScenes.addAll(sceneRepo.findByChapterId(chapterId));
        }

        return orderScenes(
                allScenes,
                temporalReadRepo.findBookEventEdgesUpToChapter(bookId, uptoChapterNumber),
                chapterOrder
        );
    }

    private List<Scene> orderScenes(List<Scene> scenes,
                                    List<TemporalReadRepository.TemporalEdgePair> edgePairs,
                                    Map<UUID, Integer> chapterOrder) {
        if (scenes.isEmpty()) {
            return List.of();
        }

        Map<UUID, Scene> byId = scenes.stream().collect(Collectors.toMap(Scene::getEventId, s -> s));
        Map<UUID, List<UUID>> adj = new HashMap<>();
        Map<UUID, Integer> indeg = new HashMap<>();
        byId.keySet().forEach(id -> {
            adj.put(id, new ArrayList<>());
            indeg.put(id, 0);
        });

        for (var edge : edgePairs) {
            UUID from = edge.getFromId();
            UUID to = edge.getToId();
            if (byId.containsKey(from) && byId.containsKey(to) && !adj.get(from).contains(to)) {
                adj.get(from).add(to);
                indeg.put(to, indeg.get(to) + 1);
            }
        }

        Comparator<UUID> tie = Comparator
                .comparing((UUID id) -> chapterRank(byId.get(id), chapterOrder))
                .thenComparing(id -> Optional.ofNullable(byId.get(id).getSceneIndex()).orElse(Integer.MAX_VALUE))
                .thenComparing(UUID::toString);

        PriorityQueue<UUID> q = new PriorityQueue<>(tie);
        indeg.forEach((id, d) -> {
            if (d == 0) {
                q.add(id);
            }
        });

        List<UUID> order = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        while (!q.isEmpty()) {
            UUID u = q.poll();
            if (!seen.add(u)) {
                continue;
            }
            order.add(u);
            for (UUID v : adj.get(u)) {
                int d = indeg.get(v) - 1;
                indeg.put(v, d);
                if (d == 0) {
                    q.add(v);
                }
            }
        }

        if (order.size() < byId.size()) {
            byId.keySet().stream()
                    .filter(id -> !seen.contains(id))
                    .sorted(tie)
                    .forEach(order::add);
        }

        return order.stream().map(byId::get).toList();
    }

    private int chapterRank(Scene scene, Map<UUID, Integer> chapterOrder) {
        if (scene == null || scene.getChapterId() == null) {
            return 0;
        }
        return chapterOrder.getOrDefault(scene.getChapterId(), 0);
    }
}
