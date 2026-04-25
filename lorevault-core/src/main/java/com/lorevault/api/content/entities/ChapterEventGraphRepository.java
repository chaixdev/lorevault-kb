package com.lorevault.api.content.entities;

import java.util.List;
import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ChapterEventGraphRepository extends Neo4jRepository<ChapterEvent, UUID> {

    @Query("""
            MATCH (m:EventMention {chapterId: $chapterId})
            RETURN count(m)
            """)
    long countMentionsByChapterId(UUID chapterId);

    @Query("""
            MATCH (ce:ChapterEvent {chapterId: $chapterId})
            RETURN count(ce)
            """)
    long countChapterEventsByChapterId(UUID chapterId);

    interface ChapterEventCandidateView {
        String getDisplayName();

        String getNormalizedName();

        String getRepresentativeEventType();

        Long getMentionCount();

        List<String> getEvidenceSnippets();

        List<String> getEventTypes();

        List<String> getSceneRelativeRelations();
    }

    @Query("""
            MATCH (m:EventMention {chapterId: $chapterId})
            WHERE m.normalizedName IS NOT NULL AND trim(m.normalizedName) <> ''
            WITH m
            ORDER BY m.normalizedName, coalesce(m.displayName, ''), coalesce(m.extractionIndex, 0)
            WITH m.normalizedName AS normalizedName, collect(m) AS mentions
            WITH normalizedName, mentions, head(mentions) AS representative
            RETURN representative.displayName                                           AS displayName,
                   normalizedName                                                       AS normalizedName,
                   representative.eventType                                             AS representativeEventType,
                   size(mentions)                                                       AS mentionCount,
                   [x IN mentions[0..4] WHERE x.evidence IS NOT NULL | x.evidence]     AS evidenceSnippets,
                   [x IN mentions | x.eventType]                                        AS eventTypes,
                   [x IN mentions | x.sceneRelativeRelation]                            AS sceneRelativeRelations
            ORDER BY normalizedName
            """)
    List<ChapterEventCandidateView> findResolutionCandidates(UUID chapterId);

    @Query("""
            MATCH (m:EventMention {chapterId: $chapterId})
            OPTIONAL MATCH (m)-[r:REFERS_TO]->(:ChapterEvent {chapterId: $chapterId})
            DELETE r
            SET m.resolutionStatus = 'unresolved'
            WITH DISTINCT $chapterId AS chapterId
            OPTIONAL MATCH (ce:ChapterEvent {chapterId: chapterId})
            DETACH DELETE ce
            """)
    void deleteByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            WITH c
            MATCH (ce:ChapterEvent {id: $chapterEventId})
            MERGE (c)-[:HAS_EVENT]->(ce)
            """)
    void linkChapterToEvent(UUID chapterId, UUID chapterEventId);

    @Query("""
            MATCH (m:EventMention {chapterId: $chapterId, normalizedName: $normalizedName})
            WITH m
            MATCH (ce:ChapterEvent {id: $chapterEventId})
            MERGE (m)-[:REFERS_TO]->(ce)
            SET m.resolutionStatus = $resolutionStatus
            """)
    void linkMentionsToChapterEvent(
            UUID chapterId,
            String normalizedName,
            UUID chapterEventId,
            String resolutionStatus
    );
}
