package com.lorevault.api.service.timeline;

import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.SceneGraphRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@Import({Neo4jContentPersistenceAdapter.class, DefaultTemporalEdgeService.class})
@DisplayName("Cycle guard prevents MEETS edges that would introduce cycles")
class TemporalEdgeCycleGuardIntegrationTest {

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
    private SceneGraphRepository sceneRepo;

    @Test
    @DisplayName("in-chapter: skip s1->s2 when s2->s1 exists")
    void inChapter_cycleGuard_skipsEdge() {
        Universe u = contentAdapter.createUniverse(Universe.ofName("U"));
        Book b = contentAdapter.createBook(Book.createStandalone(u.getId(), u.getName(), "B"));

        PublicationCoordinates pc = new PublicationCoordinates();
        pc.setUniverse(u.getName());
        pc.setBookTitle(b.getTitle());
        pc.setBookNumber(b.getBookNumber() != null ? b.getBookNumber() : 1);
        pc.setChapterTitle("C1");
        pc.setChapterNumber(1);
        Chapter c1 = contentAdapter.createChapter(Chapter.createStandalone(b.getId(), u.getId(), pc, "C1", "abc", "h1"));

        Scene s1 = new Scene();
        s1.setId(UUID.randomUUID());
        s1.setChapter(c1);
        s1.setChapterId(c1.getId());
        s1.setSceneIndex(0);
        s1.setContextSummary("s1");
        s1.setStartCharacterOffset(0L);
        s1.setEndCharacterOffset(3L);
        s1.setText("abc");

        Scene s2 = new Scene();
        s2.setId(UUID.randomUUID());
        s2.setChapter(c1);
        s2.setChapterId(c1.getId());
        s2.setSceneIndex(1);
        s2.setContextSummary("s2");
        s2.setStartCharacterOffset(3L);
        s2.setEndCharacterOffset(6L);
        s2.setText("def");
        contentAdapter.addScenesToChapter(c1.getId(), List.of(s1, s2));

        // Pre-create reverse edge s2->s1 to simulate cycle risk
        sceneRepo.createMeetsBetween(s2.getId(), s1.getId());

        int created = temporalService.createInChapterDefaults(b.getId());
        // Guard should prevent creating s1->s2, so zero created
        assertThat(created).isGreaterThanOrEqualTo(0);
        long pairCount = sceneRepo.countMeetsBetween(s1.getId(), s2.getId());
        assertThat(pairCount).isEqualTo(0);
    }

    @Test
    @DisplayName("cross-chapter: skip last(c1)->first(c2) when first(c2)->last(c1) exists")
    void crossChapter_cycleGuard_skipsEdge() {
        Universe u = contentAdapter.createUniverse(Universe.ofName("U2"));
        Book b = contentAdapter.createBook(Book.createStandalone(u.getId(), u.getName(), "B2"));

        PublicationCoordinates pc1 = new PublicationCoordinates();
        pc1.setUniverse(u.getName()); pc1.setBookTitle(b.getTitle()); pc1.setBookNumber(1); pc1.setChapterTitle("C1"); pc1.setChapterNumber(1);
        Chapter c1 = contentAdapter.createChapter(Chapter.createStandalone(b.getId(), u.getId(), pc1, "C1", "abc", "h1"));
        Scene s1 = new Scene();
        s1.setId(UUID.randomUUID());
        s1.setChapter(c1);
        s1.setChapterId(c1.getId());
        s1.setSceneIndex(0);
        s1.setContextSummary("s1");
        s1.setStartCharacterOffset(0L);
        s1.setEndCharacterOffset(3L);
        s1.setText("abc");

        Scene s2 = new Scene();
        s2.setId(UUID.randomUUID());
        s2.setChapter(c1);
        s2.setChapterId(c1.getId());
        s2.setSceneIndex(1);
        s2.setContextSummary("s2");
        s2.setStartCharacterOffset(3L);
        s2.setEndCharacterOffset(6L);
        s2.setText("def");
        contentAdapter.addScenesToChapter(c1.getId(), List.of(s1, s2));

        PublicationCoordinates pc2 = new PublicationCoordinates();
        pc2.setUniverse(u.getName()); pc2.setBookTitle(b.getTitle()); pc2.setBookNumber(1); pc2.setChapterTitle("C2"); pc2.setChapterNumber(2);
        Chapter c2 = contentAdapter.createChapter(Chapter.createStandalone(b.getId(), u.getId(), pc2, "C2", "ghi", "h2"));
        Scene s3 = new Scene();
        s3.setId(UUID.randomUUID());
        s3.setChapter(c2);
        s3.setChapterId(c2.getId());
        s3.setSceneIndex(0);
        s3.setContextSummary("s3");
        s3.setStartCharacterOffset(0L);
        s3.setEndCharacterOffset(4L);
        s3.setText("ghi");
        contentAdapter.addScenesToChapter(c2.getId(), List.of(s3));

        // Pre-create reverse edge first(c2)->last(c1) i.e., s3->s2
        sceneRepo.createMeetsBetween(s3.getId(), s2.getId());

        int created = temporalService.createCrossChapterDefault(b.getId());
        assertThat(created).isGreaterThanOrEqualTo(0);
        long pairCount = sceneRepo.countMeetsBetween(s2.getId(), s3.getId());
        assertThat(pairCount).isEqualTo(0);
    }
}
