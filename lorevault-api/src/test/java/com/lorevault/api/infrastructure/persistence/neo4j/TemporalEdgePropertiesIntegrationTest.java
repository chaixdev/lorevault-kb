package com.lorevault.api.infrastructure.persistence.neo4j;

import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jMapper;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jTemporalEdgeAdapter;
import com.lorevault.api.service.timeline.DefaultTemporalEdgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@Import({Neo4jContentPersistenceAdapter.class, Neo4jMapper.class, DefaultTemporalEdgeService.class, Neo4jTemporalEdgeAdapter.class})
class TemporalEdgePropertiesIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.20")
            .withAdminPassword("testpass123");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.neo4j.uri", neo4j::getBoltUrl);
        r.add("spring.neo4j.authentication.username", () -> "neo4j");
        r.add("spring.neo4j.authentication.password", neo4j::getAdminPassword);
    }

    @Autowired
    private Neo4jContentPersistenceAdapter contentAdapter;

    @Autowired
    private DefaultTemporalEdgeService temporalService;

    @Autowired
    private org.springframework.data.neo4j.core.Neo4jClient client;

    @Test
    void default_edges_have_expected_temporal_properties() {
        Universe u = contentAdapter.createUniverse(Universe.ofName("U3"));
        Book b = contentAdapter.createBook(Book.createStandalone(u.getId(), u.getName(), "B3"));

        PublicationCoordinates pc = new PublicationCoordinates();
        pc.setUniverse(u.getName()); pc.setBookTitle(b.getTitle()); pc.setBookNumber(1);
        pc.setChapterTitle("C1"); pc.setChapterNumber(1);
        Chapter c1 = contentAdapter.createChapter(Chapter.createStandalone(b.getId(), u.getId(), pc, "C1", "abc", "h1"));

        Scene s1 = new Scene(UUID.randomUUID(), c1, 0, "s1", 0L, 3L, "abc", null, null, List.of());
        Scene s2 = new Scene(UUID.randomUUID(), c1, 1, "s2", 3L, 6L, "def", null, null, List.of());
        contentAdapter.addScenesToChapter(c1.getId(), List.of(s1, s2));

        int created = temporalService.createInChapterDefaults(b.getId());
        assertThat(created).isGreaterThanOrEqualTo(0);

        var res = client.query("""
            MATCH (:Scene {id: $s1})-[t:TEMPORAL]->(:Scene {id: $s2})
            RETURN t.relation as relation, t.status as status, t.confidence as confidence, t.certainty as certainty
        """)
                .bind(s1.getId()).to("s1")
                .bind(s2.getId()).to("s2")
                .fetch().one();

        assertThat(res).isPresent();
        var row = res.get();
        assertThat(row.get("relation")).isEqualTo("MEETS");
        assertThat(row.get("status")).isEqualTo("CONFIRMED");
        assertThat(((Number)row.get("confidence")).doubleValue()).isEqualTo(0.5d);
        assertThat(row.get("certainty")).isEqualTo("HEURISTIC");
    }
}
