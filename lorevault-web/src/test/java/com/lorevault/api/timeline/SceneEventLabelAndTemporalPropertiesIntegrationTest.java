package com.lorevault.api.timeline;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.testing.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import com.lorevault.api.timeline.application.DefaultTemporalEdgeService;
import com.lorevault.api.timeline.infrastructure.EventGraphRepository;
import com.lorevault.api.timeline.infrastructure.TemporalEdgeWriteRepository;

@DataNeo4jTest
@Testcontainers
@Import({DefaultTemporalEdgeService.class})
@Tag("integration")
class SceneEventLabelAndTemporalPropertiesIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("testpassword")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "testpassword");
    }

    @Autowired
    private EventGraphRepository eventGraphRepository;

    @Autowired
    private TemporalEdgeWriteRepository temporalEdgeWriteRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void setUp() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void savedScenesShouldCarryEventLabelAndBeQueryableAsEvents() {
        UUID chapterId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        createChapterAndScene(chapterId, sceneId, true);

        List<String> labels = neo4jClient.query("""
                MATCH (s:Scene {id: $sceneId})
                RETURN labels(s) AS labels
                """)
                .bind(sceneId.toString()).to("sceneId")
                .fetch()
                .one()
                .map(row -> row.get("labels"))
                .filter(List.class::isInstance)
                .map(value -> ((List<?>) value).stream().map(String::valueOf).toList())
                .orElse(List.of());

        assertThat(labels).contains("Scene", "Event");
        assertThat(eventGraphRepository.findAllSceneEvents()).hasSize(1);
    }

    @Test
    void temporalUpsertShouldStoreNormalizedTemporalProperties() {
        UUID chapterId = UUID.randomUUID();
        UUID earlierId = UUID.randomUUID();
        UUID laterId = UUID.randomUUID();
        createChapterAndScene(chapterId, earlierId, true);
        createChapterAndScene(chapterId, laterId, true);
        neo4jClient.query("""
                MATCH (s:Scene {id: $sceneId})
                SET s.sceneIndex = $sceneIndex, s.startOffset = $startOffset, s.endOffset = $endOffset
                """)
                .bind(0).to("sceneIndex")
                .bind(0L).to("startOffset")
                .bind(20L).to("endOffset")
                .bind(earlierId.toString()).to("sceneId")
                .run();
        neo4jClient.query("""
                MATCH (s:Scene {id: $sceneId})
                SET s.sceneIndex = $sceneIndex, s.startOffset = $startOffset, s.endOffset = $endOffset
                """)
                .bind(1).to("sceneIndex")
                .bind(21L).to("startOffset")
                .bind(50L).to("endOffset")
                .bind(laterId.toString()).to("sceneId")
                .run();

        temporalEdgeWriteRepository.upsertTemporalEdge(
                earlierId,
                laterId,
                "R:temporal.before",
                "Explicit",
                0.9,
                "test-suite",
                "evidence text",
                0L,
                10L,
                null
        );

        Map<String, Object> edge = neo4jClient.query("""
                MATCH (a:Scene {id: $fromId})-[t:TEMPORAL]->(b:Scene {id: $toId})
                RETURN t.temporalRelation AS temporalRelation,
                       t.certainty AS certainty,
                       t.weight AS weight,
                       t.source AS source,
                       t.type AS legacyType,
                       t.confidence AS legacyConfidence
                """)
                .bind(earlierId.toString()).to("fromId")
                .bind(laterId.toString()).to("toId")
                .fetch()
                .one()
                .orElseThrow();

        assertThat(edge.get("temporalRelation")).isEqualTo("R:temporal.before");
        assertThat(edge.get("certainty")).isEqualTo("Explicit");
        assertThat(edge.get("weight")).isEqualTo(0.9);
        assertThat(edge.get("source")).isEqualTo("test-suite");
        assertThat(edge.get("legacyType")).isNull();
        assertThat(edge.get("legacyConfidence")).isNull();
    }

    @Test
    void temporalUpsertShouldCollapseOppositeDirectionEdgeForSamePair() {
        UUID chapterId = UUID.randomUUID();
        UUID sceneA = UUID.randomUUID();
        UUID sceneB = UUID.randomUUID();
        createChapterAndScene(chapterId, sceneA, true);
        createChapterAndScene(chapterId, sceneB, true);

        neo4jClient.query("""
                MATCH (s:Scene {id: $sceneId})
                SET s.sceneIndex = $sceneIndex
                """)
                .bind(sceneA.toString()).to("sceneId")
                .bind(0).to("sceneIndex")
                .run();
        neo4jClient.query("""
                MATCH (s:Scene {id: $sceneId})
                SET s.sceneIndex = $sceneIndex
                """)
                .bind(sceneB.toString()).to("sceneId")
                .bind(1).to("sceneIndex")
                .run();

        // Simulate historical wrong-polarity duplicate persisted in reverse direction.
        neo4jClient.query("""
                MATCH (a:Scene {id: $aId})
                MATCH (b:Scene {id: $bId})
                CREATE (b)-[:TEMPORAL {
                    temporalRelation: 'R:temporal.before',
                    certainty: 'Explicit',
                    weight: 0.7,
                    source: 'legacy-test',
                    rationale: 'legacy'
                }]->(a)
                """)
                .bind(sceneA.toString()).to("aId")
                .bind(sceneB.toString()).to("bId")
                .run();

        temporalEdgeWriteRepository.upsertTemporalEdge(
                sceneA,
                sceneB,
                "R:temporal.before",
                "Explicit",
                0.95,
                "test-suite",
                "canonical evidence",
                null,
                null,
                null
        );

        Long edgeCount = neo4jClient.query("""
                MATCH (a:Scene {id: $aId})-[t:TEMPORAL]-(b:Scene {id: $bId})
                RETURN count(t) AS edgeCount
                """)
                .bind(sceneA.toString()).to("aId")
                .bind(sceneB.toString()).to("bId")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);

        assertThat(edgeCount).isEqualTo(1L);

        Map<String, Object> canonical = neo4jClient.query("""
                MATCH (a:Scene {id: $aId})-[t:TEMPORAL]->(b:Scene {id: $bId})
                RETURN t.temporalRelation AS temporalRelation,
                       t.source AS source,
                       t.rationale AS rationale
                """)
                .bind(sceneA.toString()).to("aId")
                .bind(sceneB.toString()).to("bId")
                .fetch()
                .one()
                .orElseThrow();

        assertThat(canonical.get("temporalRelation")).isEqualTo("R:temporal.before");
        assertThat(canonical.get("source")).isEqualTo("test-suite");
        assertThat(canonical.get("rationale")).isEqualTo("canonical evidence");
    }

    private void createChapterAndScene(UUID chapterId, UUID sceneId, boolean withEventLabel) {
        String createScene = withEventLabel
                ? "CREATE (s:Scene:Event {id: $sceneId, chapterId: $chapterId, sceneIndex: 0, startOffset: 0, endOffset: 1, contextSummary: 'summary', text: 'text'})"
                : "CREATE (s:Scene {id: $sceneId, chapterId: $chapterId, sceneIndex: 0, startOffset: 0, endOffset: 1, contextSummary: 'summary', text: 'text'})";

        neo4jClient.query("""
                MERGE (c:Chapter {id: $chapterId})
                ON CREATE SET c.chapterNumber = 1
                """)
                .bind(chapterId.toString()).to("chapterId")
                .run();

        neo4jClient.query(createScene)
                .bind(sceneId.toString()).to("sceneId")
                .bind(chapterId.toString()).to("chapterId")
                .run();

        neo4jClient.query("""
                MATCH (c:Chapter {id: $chapterId})
                MATCH (s:Scene {id: $sceneId})
                MERGE (c)-[:HAS_SCENE]->(s)
                """)
                .bind(chapterId.toString()).to("chapterId")
                .bind(sceneId.toString()).to("sceneId")
                .run();
    }
}
