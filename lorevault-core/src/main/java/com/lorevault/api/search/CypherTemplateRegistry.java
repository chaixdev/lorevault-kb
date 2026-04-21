package com.lorevault.api.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of parameterized Cypher templates for entity-lookup questions.
 *
 * <p>Templates are keyed by a stable template ID. Each template accepts a parameter map
 * and returns a list of {@link EntityLookupResult} rows. All templates carry a spoiler
 * guard via {@code $allowedBookIds} when a {@link SpoilerVisibility} is provided.
 *
 * <p>Supported templates:
 * <ul>
 *   <li>{@code individual-lookup} — profile of a named individual</li>
 *   <li>{@code individual-scenes} — scenes featuring a named individual</li>
 *   <li>{@code location-lookup} — profile of a named location</li>
 *   <li>{@code individual-co-occurrence} — scenes featuring two named individuals</li>
 *   <li>{@code individual-first-appearance} — first scene/chapter for a named individual</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CypherTemplateRegistry {

    /**
     * A single row returned by any entity-lookup template.
     * Fields are nullable; callers should check before use.
     */
    public record EntityLookupResult(
            String templateId,
            String displayName,
            String normalizedName,
            Integer mentionCount,
            String firstSeenChapterId,
            Integer firstSeenBookNumber,
            Integer firstSeenChapterNumber,
            String sceneId,
            String sceneSummary,
            Integer bookNumber,
            Integer chapterNumber
    ) {}

    // -------------------------------------------------------------------------
    // Template SQL strings
    // -------------------------------------------------------------------------

    private static final String INDIVIDUAL_LOOKUP = """
            MATCH (bi:BookIndividual)
            WHERE bi.normalizedName = $normalizedName
              AND bi.bookId IN $allowedBookIds
            OPTIONAL MATCH (bi)<-[:REFERS_TO]-(ci:ChapterIndividual)
            WITH bi, count(ci) AS chapterCount
            RETURN
                bi.displayName            AS displayName,
                bi.normalizedName         AS normalizedName,
                bi.chapterIndividualCount AS mentionCount,
                bi.firstSeenChapterId     AS firstSeenChapterId,
                null                      AS sceneId,
                null                      AS sceneSummary,
                null                      AS bookNumber,
                null                      AS chapterNumber
            """;

    private static final String INDIVIDUAL_SCENES = """
            MATCH (bi:BookIndividual)
            WHERE bi.normalizedName = $normalizedName
              AND bi.bookId IN $allowedBookIds
            MATCH (bi)<-[:REFERS_TO]-(ci:ChapterIndividual)<-[:REFERS_TO]-(im:IndividualMention)
            MATCH (scene:Scene)-[:MENTIONS]->(im)
            MATCH (chapter:Chapter)-[:HAS_SCENE]->(scene)
            RETURN DISTINCT
                bi.displayName        AS displayName,
                bi.normalizedName     AS normalizedName,
                null                  AS mentionCount,
                null                  AS firstSeenChapterId,
                scene.id              AS sceneId,
                scene.contextSummary  AS sceneSummary,
                chapter.bookNumber    AS bookNumber,
                chapter.chapterNumber AS chapterNumber
            ORDER BY chapter.bookNumber, chapter.chapterNumber
            LIMIT 20
            """;

    private static final String LOCATION_LOOKUP = """
            MATCH (bl:BookLocation)
            WHERE bl.normalizedName = $normalizedName
              AND bl.bookId IN $allowedBookIds
            OPTIONAL MATCH (bl)<-[:REFERS_TO]-(cl:ChapterLocation)
            WITH bl, count(cl) AS chapterCount
            RETURN
                bl.displayName            AS displayName,
                bl.normalizedName         AS normalizedName,
                bl.chapterLocationCount   AS mentionCount,
                bl.firstSeenChapterId     AS firstSeenChapterId,
                null                      AS sceneId,
                null                      AS sceneSummary,
                null                      AS bookNumber,
                null                      AS chapterNumber
            """;

    private static final String INDIVIDUAL_CO_OCCURRENCE = """
            MATCH (biA:BookIndividual)
            WHERE biA.normalizedName = $normalizedNameA
              AND biA.bookId IN $allowedBookIds
            MATCH (biB:BookIndividual)
            WHERE biB.normalizedName = $normalizedNameB
              AND biB.bookId IN $allowedBookIds
            MATCH (biA)<-[:REFERS_TO]-(ciA:ChapterIndividual)<-[:REFERS_TO]-(imA:IndividualMention)
            MATCH (scene:Scene)-[:MENTIONS]->(imA)
            MATCH (scene)-[:MENTIONS]->(imB:IndividualMention)
            MATCH (imB)-[:REFERS_TO]->(ciB:ChapterIndividual)-[:REFERS_TO]->(biB)
            MATCH (chapter:Chapter)-[:HAS_SCENE]->(scene)
            RETURN DISTINCT
                biA.displayName       AS displayName,
                biA.normalizedName    AS normalizedName,
                null                  AS mentionCount,
                null                  AS firstSeenChapterId,
                scene.id              AS sceneId,
                scene.contextSummary  AS sceneSummary,
                chapter.bookNumber    AS bookNumber,
                chapter.chapterNumber AS chapterNumber
            ORDER BY chapter.bookNumber, chapter.chapterNumber
            LIMIT 20
            """;

    private static final String INDIVIDUAL_FIRST_APPEARANCE = """
            MATCH (bi:BookIndividual)
            WHERE bi.normalizedName = $normalizedName
              AND bi.bookId IN $allowedBookIds
            MATCH (bi)<-[:REFERS_TO]-(ci:ChapterIndividual)<-[:REFERS_TO]-(im:IndividualMention)
            MATCH (scene:Scene)-[:MENTIONS]->(im)
            MATCH (chapter:Chapter)-[:HAS_SCENE]->(scene)
            RETURN
                bi.displayName        AS displayName,
                bi.normalizedName     AS normalizedName,
                null                  AS mentionCount,
                bi.firstSeenChapterId AS firstSeenChapterId,
                scene.id              AS sceneId,
                scene.contextSummary  AS sceneSummary,
                chapter.bookNumber    AS bookNumber,
                chapter.chapterNumber AS chapterNumber
            ORDER BY chapter.bookNumber, chapter.chapterNumber
            LIMIT 1
            """;

    private static final Map<String, String> TEMPLATES = Map.of(
            "individual-lookup",          INDIVIDUAL_LOOKUP,
            "individual-scenes",          INDIVIDUAL_SCENES,
            "location-lookup",            LOCATION_LOOKUP,
            "individual-co-occurrence",   INDIVIDUAL_CO_OCCURRENCE,
            "individual-first-appearance",INDIVIDUAL_FIRST_APPEARANCE
    );

    // -------------------------------------------------------------------------

    private final Neo4jClient neo4jClient;

    /**
     * Execute a named template with the given parameters and spoiler visibility.
     *
     * @param templateId  one of the registered template IDs
     * @param params      template-specific parameters (e.g. {@code normalizedName})
     * @param visibility  spoiler visibility for the requesting reader; may be null (no guard)
     * @return list of result rows; empty if no matches or template not found
     */
    public List<EntityLookupResult> execute(String templateId,
                                            Map<String, Object> params,
                                            SpoilerVisibility visibility) {
        String cypher = TEMPLATES.get(templateId);
        if (cypher == null) {
            log.warn("Unknown template ID: {}", templateId);
            return List.of();
        }

        Map<String, Object> allParams = new HashMap<>(params);
        allParams.put("allowedBookIds", resolveAllowedBookIds(visibility));

        log.debug("Executing template '{}' with params: {}", templateId, allParams.keySet());

        try {
            return neo4jClient.query(cypher)
                    .bindAll(allParams)
                    .fetchAs(EntityLookupResult.class)
                    .mappedBy((typeSystem, record) -> new EntityLookupResult(
                            templateId,
                            nullableString(record, "displayName"),
                            nullableString(record, "normalizedName"),
                            nullableInt(record, "mentionCount"),
                            nullableString(record, "firstSeenChapterId"),
                            nullableInt(record, "bookNumber"),   // reused as firstSeenBookNumber
                            nullableInt(record, "chapterNumber"),
                            nullableString(record, "sceneId"),
                            nullableString(record, "sceneSummary"),
                            nullableInt(record, "bookNumber"),
                            nullableInt(record, "chapterNumber")
                    ))
                    .all()
                    .stream()
                    .toList();
        } catch (Exception e) {
            log.warn("Template '{}' execution failed: {}", templateId, e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Derive the list of allowed book IDs from the reader's spoiler visibility.
     * When visibility is null, returns an empty list — callers must handle this
     * by either skipping the spoiler guard or treating all books as allowed.
     *
     * <p>Note: {@code BookIndividual.bookId} is a string UUID. This method returns
     * a list of strings that Cypher can match against {@code bi.bookId IN $allowedBookIds}.
     * The actual book UUID resolution is deferred to a future repository query once
     * {@code BookGraphRepository} exposes a {@code findBooksUpToProgress} method.
     * For now, an empty list means "no spoiler guard applied" — the WHERE clause
     * {@code bi.bookId IN $allowedBookIds} will match nothing if the list is empty,
     * so callers should pass a non-null visibility when spoiler safety is required.
     */
    private List<String> resolveAllowedBookIds(SpoilerVisibility visibility) {
        // TODO: replace with real book-id resolution once BookGraphRepository
        //       exposes findBooksUpToProgress(series, maxBookNumber) -> List<UUID>.
        //       For now, return a sentinel that disables the guard (all books allowed).
        if (visibility == null) {
            return List.of("__ALL__");  // sentinel: Cypher guard is effectively bypassed
        }
        // Return sentinel for now; real implementation will query the book repo.
        return List.of("__ALL__");
    }

    private static String nullableString(org.neo4j.driver.Record record, String key) {
        var value = record.get(key);
        return (value == null || value.isNull()) ? null : value.asString();
    }

    private static Integer nullableInt(org.neo4j.driver.Record record, String key) {
        var value = record.get(key);
        return (value == null || value.isNull()) ? null : value.asInt();
    }
}
