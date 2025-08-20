package com.lorevault.api.tck.search;

import com.lorevault.api.application.port.SemanticSearchPort;
import com.lorevault.api.application.port.SemanticSearchPort.SearchFilters;
import com.lorevault.api.application.port.SemanticSearchPort.SearchResult;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.testutil.fakes.FakeContentPersistencePort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Technology Compatibility Kit (TCK) for SemanticSearchPort implementations.
 *
 * Contract focus:
 * - search orders results by relevance (descending)
 * - search respects topK limit
 * - search returns empty list when no embedded chunks exist
 * - isAvailable reflects presence of embedded chunks
 *
 * Implementors must provide a fixture wiring the port under test to a persistence
 * fake so this TCK can seed embedded chunks.
 */
public abstract class SemanticSearchPortTCK {

    /** Provides the port under test with a writable fake persistence for seeding data. */
    protected abstract Fixture createFixture();

    /** Container for the port under test and its backing fake persistence. */
    public record Fixture(SemanticSearchPort port, FakeContentPersistencePort persistence) {}

    @Test
    void search_ordersByRelevance_descending() {
        Fixture fx = createFixture();
        UUID chapterId = UUID.randomUUID();
        // Seed two chunks with orthogonal embeddings in 3D: e1 vs e2
        ChunkNode c1 = chunk(chapterId, UUID.randomUUID(), "alpha", new double[]{1, 0, 0});
        ChunkNode c2 = chunk(chapterId, UUID.randomUUID(), "beta", new double[]{0, 1, 0});
        fx.persistence().addChunksToChapter(chapterId, List.of(c1, c2));

        // Query closer to e1
        double[] q = new double[]{0.9, 0.1, 0.0};

        List<SearchResult> results = fx.port().search(q, 10, SearchFilters.empty());

        assertThat(results).hasSize(2);
        // Expect first result to be c1 (higher cosine to e1)
        assertThat(results.get(0).chunkId()).isEqualTo(c1.getId());
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(results.get(1).score());
    }

    @Test
    void search_respects_topK_limit() {
        Fixture fx = createFixture();
        UUID chapterId = UUID.randomUUID();
        List<ChunkNode> many = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            many.add(chunk(chapterId, UUID.randomUUID(), "text-" + i, new double[]{i + 1, 0, 0}));
        }
        fx.persistence().addChunksToChapter(chapterId, many);

        List<SearchResult> results = fx.port().search(new double[]{1, 0, 0}, 2, SearchFilters.empty());
        assertThat(results).hasSize(2);
        // Ensure sorted by score DESC
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(results.get(1).score());
    }

    @Test
    void search_returns_empty_when_no_embedded_chunks() {
        Fixture fx = createFixture();
        UUID chapterId = UUID.randomUUID();
        // Add a chunk without embeddings to ensure it's ignored
        ChunkNode noEmb = chunk(chapterId, UUID.randomUUID(), "no-emb", null);
        fx.persistence().addChunksToChapter(chapterId, List.of(noEmb));

        List<SearchResult> results = fx.port().search(new double[]{1, 0, 0}, 5, SearchFilters.empty());
        assertThat(results).isEmpty();
    }

    @Test
    void availability_reflects_presence_of_embedded_chunks() {
        Fixture fx = createFixture();
        assertThat(fx.port().isAvailable()).isFalse();

        UUID chapterId = UUID.randomUUID();
        fx.persistence().addChunksToChapter(chapterId,
                List.of(chunk(chapterId, UUID.randomUUID(), "emb", new double[]{1, 0})));

        assertThat(fx.port().isAvailable()).isTrue();
    }

    // Helpers
    private static ChunkNode chunk(UUID chapterId, UUID id, String text, double[] emb) {
        ChunkNode c = new ChunkNode();
        c.setId(id);
        c.setChunkNumberInChapter(1);
        c.setStartCharInChapter(0);
        c.setEndCharInChapter(text == null ? 0 : text.length());
        c.setContentHash("hash-" + id);
        c.setText(text);
        c.setEmbedding(emb);
        c.setEmbeddedAt(emb != null ? LocalDateTime.now() : null);
        return c;
    }
}
