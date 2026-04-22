package com.lorevault.api.ai.application;

import com.lorevault.api.content.domain.Chapter;
import com.lorevault.api.content.domain.Scene;
import com.lorevault.api.content.infrastructure.ChapterReadRepository;
import com.lorevault.api.content.infrastructure.SceneGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds triads of scenes (previous, current, next) for a chapter.
 * Supports cross-chapter previous scene resolution (last scene of previous chapter).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TriadBuilderService {

    public record SceneTriad(Scene previous, Scene current, Scene next) {}

    private final SceneGraphRepository sceneRepo;
    private final ChapterReadRepository chapterReadRepo;

    /**
     * Build scene triads for the provided chapter.
     * @param chapter The Chapter aggregate (must have id, bookId, chapterNumber)
     * @return ordered list of triads, one per current scene in the chapter
     */
    public List<SceneTriad> buildTriadsForChapter(Chapter chapter) {
        UUID chapterId = chapter.getId();
        if (chapterId == null) {
            log.warn("TriadBuilder: chapter has no id; returning empty triads");
            return List.of();
        }

        // Prefer in-memory scenes if provided (e.g., retry-aware pipeline builds temp scenes)
        List<Scene> scenes = new ArrayList<>();
        if (chapter.getScenes() != null && !chapter.getScenes().isEmpty()) {
            scenes.addAll(chapter.getScenes());
            log.debug("TriadBuilder: using {} in-memory scenes for chapter {}", scenes.size(), chapterId);
        } else {
            scenes.addAll(sceneRepo.findByChapterId(chapterId));
            log.debug("TriadBuilder: loaded {} scenes from graph for chapter {}", scenes.size(), chapterId);
        }
        scenes.sort(Comparator.comparingInt(Scene::getSceneIndex));
        if (scenes.isEmpty()) {
            log.info("TriadBuilder: no scenes found for chapter {}", chapterId);
            return List.of();
        }

        // Resolve cross-chapter previous scene (last scene of previous chapter, if any)
        Scene crossChapterPrev = resolveCrossChapterPreviousScene(chapter);

        List<SceneTriad> triads = new ArrayList<>();
        for (int i = 0; i < scenes.size(); i++) {
            Scene prev = (i == 0) ? crossChapterPrev : scenes.get(i - 1);
            Scene curr = scenes.get(i);
            Scene next = (i < scenes.size() - 1) ? scenes.get(i + 1) : null;
            triads.add(new SceneTriad(prev, curr, next));
        }
        return triads;
    }

    private Scene resolveCrossChapterPreviousScene(Chapter chapter) {
        try {
            if (chapter.getBookId() == null || chapter.getChapterNumber() == null) return null;
            int currentNumber = chapter.getChapterNumber();
            if (currentNumber <= 1) return null;

            List<UUID> chapterIds = chapterReadRepo.findChapterIdsUpTo(chapter.getBookId(), currentNumber);
            if (chapterIds == null || chapterIds.isEmpty()) return null;

            int idx = chapterIds.indexOf(chapter.getId());
            if (idx <= 0) {
                // Fallback: if current id not in list, assume previous is just before currentNumber
                Optional<UUID> prevIdOpt = chapterIds.stream()
                        .limit(Math.max(0, chapterIds.size() - 1))
                        .reduce((a, b) -> b);
                if (prevIdOpt.isEmpty()) return null;
                return findLastScene(prevIdOpt.get()).orElse(null);
            }

            UUID prevChapterId = chapterIds.get(idx - 1);
            return findLastScene(prevChapterId).orElse(null);
        } catch (Exception e) {
            log.debug("TriadBuilder: failed to resolve cross-chapter previous scene: {}", e.getMessage());
            return null;
        }
    }

    private Optional<Scene> findLastScene(UUID chapterId) {
        List<Scene> scenes = new ArrayList<>(sceneRepo.findByChapterId(chapterId));
        if (scenes.isEmpty()) return Optional.empty();
        return scenes.stream().max(Comparator.comparingInt(Scene::getSceneIndex));
    }
}
