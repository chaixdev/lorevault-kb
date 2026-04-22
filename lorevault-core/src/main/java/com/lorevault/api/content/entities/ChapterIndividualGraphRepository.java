package com.lorevault.api.content.entities;

import java.util.List;
import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ChapterIndividualGraphRepository extends Neo4jRepository<ChapterIndividual, UUID> {

    @Query("""
            MATCH (m:IndividualMention {chapterId: $chapterId})
            RETURN count(m)
            """)
    long countMentionsByChapterId(UUID chapterId);

    @Query("""
            MATCH (ci:ChapterIndividual {chapterId: $chapterId})
            RETURN count(ci)
            """)
    long countChapterIndividualsByChapterId(UUID chapterId);

    interface ChapterIndividualCandidateView {
        String getDisplayName();

        String getNormalizedName();

        Long getMentionCount();
    }

    @Query("""
            MATCH (m:IndividualMention {chapterId: $chapterId})
            OPTIONAL MATCH (m)-[r:REFERS_TO]->(:ChapterIndividual {chapterId: $chapterId})
            DELETE r
            SET m.resolutionStatus = 'unresolved'
            WITH DISTINCT $chapterId AS chapterId
            OPTIONAL MATCH (ci:ChapterIndividual {chapterId: chapterId})
            DETACH DELETE ci
            """)
    void deleteByChapterId(UUID chapterId);

    @Query("""
            MATCH (m:IndividualMention {chapterId: $chapterId})
            WHERE m.normalizedName IS NOT NULL AND trim(m.normalizedName) <> ''
            WITH m
            ORDER BY m.normalizedName, coalesce(m.displayName, ''), coalesce(m.extractionIndex, 0)
            WITH m.normalizedName AS normalizedName, collect(m) AS mentions
            WITH normalizedName, mentions, head(mentions) AS representative
            RETURN representative.displayName AS displayName,
                   normalizedName AS normalizedName,
                   size(mentions) AS mentionCount
            ORDER BY normalizedName
            """)
    List<ChapterIndividualCandidateView> findResolutionCandidates(UUID chapterId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            WITH c
            MATCH (ci:ChapterIndividual {id: $chapterIndividualId})
            MERGE (c)-[:HAS_INDIVIDUAL]->(ci)
            """)
    void linkChapterToIndividual(UUID chapterId, UUID chapterIndividualId);

    @Query("""
            MATCH (m:IndividualMention {chapterId: $chapterId, normalizedName: $normalizedName})
            WITH m
            MATCH (ci:ChapterIndividual {id: $chapterIndividualId})
            MERGE (m)-[:REFERS_TO]->(ci)
            SET m.resolutionStatus = $resolutionStatus
            """)
    void linkMentionsToChapterIndividual(
            UUID chapterId,
            String normalizedName,
            UUID chapterIndividualId,
            String resolutionStatus
    );
}
