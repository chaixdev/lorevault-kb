package com.lorevault.api.content.entities;

import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Minimal read repository for chapter ID sequences.
 */
public interface ChapterReadRepository extends Repository<Chapter, UUID> {

    @Query(
        """
        MATCH (b:Book {id: $bookId})
        MATCH (c:Chapter)-[:IN_BOOK]->(b)
        WHERE c.chapterNumber <= $uptoChapterNumber
        RETURN c.id ORDER BY c.chapterNumber
        """
    )
    List<UUID> findChapterIdsUpTo(UUID bookId, int uptoChapterNumber);
}
