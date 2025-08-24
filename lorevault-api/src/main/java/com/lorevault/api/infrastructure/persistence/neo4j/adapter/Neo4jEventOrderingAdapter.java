package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.application.port.EventOrderingPort;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChapterReadRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.EventGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.TemporalReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class Neo4jEventOrderingAdapter implements EventOrderingPort {

    private final EventGraphRepository eventGraphRepository;
    private final TemporalReadRepository temporalReadRepository;
    private final ChapterReadRepository chapterReadRepository;
    private final Neo4jMapper mapper;

    @Override
    public List<Scene> findChapterScenes(UUID chapterId) {
        List<SceneNode> nodes = eventGraphRepository.findSceneEventsByChapter(chapterId);
        return nodes.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<AbstractMap.SimpleEntry<UUID, UUID>> findChapterTemporalEdges(UUID chapterId) {
        return temporalReadRepository.findChapterEventEdges(chapterId).stream()
                .map(p -> new AbstractMap.SimpleEntry<>(p.getFromId(), p.getToId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<UUID> findBookChapterIdsUpTo(UUID bookId, int uptoChapterNumber) {
        return chapterReadRepository.findChapterIdsUpTo(bookId, uptoChapterNumber);
    }
}
