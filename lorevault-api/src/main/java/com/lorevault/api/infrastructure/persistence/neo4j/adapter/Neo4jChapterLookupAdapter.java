package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.application.port.ChapterLookupPort;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChapterReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class Neo4jChapterLookupAdapter implements ChapterLookupPort {

    private final ChapterReadRepository chapterReadRepository;

    @Override
    public List<UUID> findChapterIdsUpTo(UUID bookId, int uptoChapterNumber) {
        return chapterReadRepository.findChapterIdsUpTo(bookId, uptoChapterNumber);
    }
}
