package com.lorevault.api.service.timeline;

import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jMapper;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jTemporalEdgeAdapter;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
// import com.lorevault.api.infrastructure.persistence.neo4j.repository.BookGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.SceneGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.TemporalEdgeWriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@Import({Neo4jContentPersistenceAdapter.class, Neo4jMapper.class, DefaultTemporalEdgeService.class, Neo4jTemporalEdgeAdapter.class})
@DisplayName("Cross-chapter default MEETS edges")
class CrossChapterTemporalEdgesIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("testpass123");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.neo4j.uri", neo4j::getBoltUrl);
        r.add("spring.neo4j.authentication.username", () -> "neo4j");
        r.add("spring.neo4j.authentication.password", () -> "testpass123");
    }

    @Autowired
    private Neo4jContentPersistenceAdapter contentAdapter;

    @Autowired
    private DefaultTemporalEdgeService temporalService;

    @Autowired
    private TemporalEdgeWriteRepository temporalRepo;

    @Autowired
    private SceneGraphRepository sceneRepo;

    @Test
    @DisplayName("creates in-chapter and cross-chapter edges and is idempotent")
    void createsEdgesAndIsIdempotent() {
        // Universe and Book
        Universe universe = Universe.ofName("U-Test");
        Universe savedUniverse = contentAdapter.createUniverse(universe);
        Book book = Book.createStandalone(savedUniverse.getId(), savedUniverse.getName(), "B1");
        Book savedBook = contentAdapter.createBook(book);

        // Chapter 1 with two scenes
        PublicationCoordinates pc1 = new PublicationCoordinates();
        pc1.setUniverse(savedUniverse.getName());
        pc1.setBookTitle(savedBook.getTitle());
        pc1.setBookNumber(savedBook.getBookNumber() != null ? savedBook.getBookNumber() : 1);
        pc1.setChapterTitle("C1");
        pc1.setChapterNumber(1);
        Chapter ch1 = Chapter.createStandalone(savedBook.getId(), savedUniverse.getId(), pc1, "C1", "abcdef", "hash-c1");
        ch1 = contentAdapter.createChapter(ch1);

        Scene s1 = new Scene(UUID.randomUUID(), ch1, 0, "s1", 0L, 3L, "abc", null, null, List.of());
        Scene s2 = new Scene(UUID.randomUUID(), ch1, 1, "s2", 3L, 6L, "def", null, null, List.of());
        contentAdapter.addScenesToChapter(ch1.getId(), List.of(s1, s2));

        // Chapter 2 with one scene
        PublicationCoordinates pc2 = new PublicationCoordinates();
        pc2.setUniverse(savedUniverse.getName());
        pc2.setBookTitle(savedBook.getTitle());
        pc2.setBookNumber(pc1.getBookNumber());
        pc2.setChapterTitle("C2");
        pc2.setChapterNumber(2);
        Chapter ch2 = Chapter.createStandalone(savedBook.getId(), savedUniverse.getId(), pc2, "C2", "ghij", "hash-c2");
        ch2 = contentAdapter.createChapter(ch2);

        Scene s3 = new Scene(UUID.randomUUID(), ch2, 0, "s3", 0L, 4L, "ghij", null, null, List.of());
        contentAdapter.addScenesToChapter(ch2.getId(), List.of(s3));

        // Sanity: scenes persisted
        assertThat(sceneRepo.findByChapterId(ch1.getId())).hasSize(2);
        assertThat(sceneRepo.findByChapterId(ch2.getId())).hasSize(1);

        // Create defaults (in-chapter + cross-chapter)
        int firstEdges = temporalService.createInChapterDefaults(savedBook.getId())
                + temporalService.createCrossChapterDefault(savedBook.getId());
        assertThat(firstEdges).isGreaterThanOrEqualTo(2); // s1->s2 and s2->s3

        // Verify counts via repository helper
        int ch1Edges = temporalRepo.countTemporalEdgesFromChapter(ch1.getId());
        int ch2Edges = temporalRepo.countTemporalEdgesFromChapter(ch2.getId());

        // ch1 should have two outgoing edges: s1->s2 (in-chapter) and s2->s3 (cross-chapter)
        assertThat(ch1Edges).isEqualTo(2);
        // ch2 has only one scene, so zero outgoing edges
        assertThat(ch2Edges).isEqualTo(0);

        // Idempotency: running again should not increase counts
        int secondRun = temporalService.createInChapterDefaults(savedBook.getId())
                + temporalService.createCrossChapterDefault(savedBook.getId());
        assertThat(secondRun).isGreaterThanOrEqualTo(0);

        int ch1EdgesAfter = temporalRepo.countTemporalEdgesFromChapter(ch1.getId());
        int ch2EdgesAfter = temporalRepo.countTemporalEdgesFromChapter(ch2.getId());
        assertThat(ch1EdgesAfter).isEqualTo(ch1Edges);
        assertThat(ch2EdgesAfter).isEqualTo(ch2Edges);

        // Verify actual edge endpoints: s2 -> s3 exists
        List<SceneNode> ch1Scenes = sceneRepo.findByChapterId(ch1.getId());
        List<SceneNode> ch2Scenes = sceneRepo.findByChapterId(ch2.getId());
        Optional<SceneNode> lastOfCh1 = ch1Scenes.stream().max(java.util.Comparator.comparing(SceneNode::getSceneIndex));
        Optional<SceneNode> firstOfCh2 = ch2Scenes.stream().min(java.util.Comparator.comparing(SceneNode::getSceneIndex));
        assertThat(lastOfCh1).isPresent();
        assertThat(firstOfCh2).isPresent();

        // Check with a simple Cypher count for the specific pair
        long pairCount = sceneRepo.countMeetsBetween(lastOfCh1.get().getId(), firstOfCh2.get().getId());
        assertThat(pairCount).isEqualTo(1);
    }
}
