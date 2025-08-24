package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Minimal read repository for chapter ID sequences.
 */
public interface ChapterReadRepository extends Repository<ChapterNode, UUID> {

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
