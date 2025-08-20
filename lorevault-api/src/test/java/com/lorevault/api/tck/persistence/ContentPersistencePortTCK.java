package com.lorevault.api.tck.persistence;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TCK for ContentPersistencePort implementations.
 *
 * Marked with @Tag("integration") so it's excluded by default in unit test runs.
 * Concrete adapter tests (e.g. Neo4j) should extend this class and provide a
 * fully-wired port instance backed by a real store (Testcontainers recommended).
 */
@Tag("integration")
public abstract class ContentPersistencePortTCK {

    protected abstract ContentPersistencePort createPort();

    @Test
    void create_and_find_chapter_by_id() {
        ContentPersistencePort port = createPort();
        ChapterNode ch = new ChapterNode();
        ch.setId(UUID.randomUUID());
        ch.setUniverse("Middle Earth");
        ch.setBookNumber(1);
        ch.setChapterNumber(1);
        ch.setChapterTitle("A Long-Expected Party");

        ChapterNode saved = port.createChapter(ch);
        Optional<ChapterNode> found = port.findChapterById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getChapterTitle()).isEqualTo("A Long-Expected Party");
    }

    @Test
    void add_and_find_chunks_with_embeddings() {
        ContentPersistencePort port = createPort();
        UUID chapterId = UUID.randomUUID();

        // Two chunks: one with embedding, one without
        ChunkNode withEmb = new ChunkNode();
        withEmb.setId(UUID.randomUUID());
        withEmb.setText("Some text");
        withEmb.setEmbedding(new double[]{1, 2, 3});
        withEmb.setEmbeddedAt(LocalDateTime.now());

        ChunkNode withoutEmb = new ChunkNode();
        withoutEmb.setId(UUID.randomUUID());
        withoutEmb.setText("Other text");

        port.addChunksToChapter(chapterId, List.of(withEmb, withoutEmb));

        List<ChunkNode> byChapter = port.findChunksByChapterId(chapterId);
        assertThat(byChapter).hasSize(2);

        List<ChunkNode> embedded = port.findAllChunksWithEmbeddings();
        assertThat(embedded).extracting(ChunkNode::getId).contains(withEmb.getId());
        assertThat(embedded).extracting(ChunkNode::getId).doesNotContain(withoutEmb.getId());
    }
}
