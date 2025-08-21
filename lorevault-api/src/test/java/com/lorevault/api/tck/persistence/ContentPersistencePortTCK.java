package com.lorevault.api.tck.persistence;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.shared.PublicationCoordinates;
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
        Chapter ch = new Chapter();
        ch.setId(UUID.randomUUID());
        PublicationCoordinates coords = new PublicationCoordinates();
        coords.setUniverse("Middle Earth");
        coords.setSeries("The Lord of the Rings");
        coords.setBookNumber(1);
        coords.setChapterNumber(1);
        ch.setCoordinates(coords);
        ch.setChapterTitle("A Long-Expected Party");

        Chapter saved = port.createChapter(ch);
        Optional<Chapter> found = port.findChapterById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getChapterTitle()).isEqualTo("A Long-Expected Party");
    }

    @Test
    void add_and_find_chunks_with_embeddings() {
        ContentPersistencePort port = createPort();
        UUID chapterId = UUID.randomUUID();

        // Two chunks: one with embedding, one without
        Chunk withEmb = new Chunk();
        withEmb.setId(UUID.randomUUID());
        withEmb.setText("Some text");
        withEmb.setEmbedding(new double[]{1, 2, 3});
        withEmb.setEmbeddedAt(LocalDateTime.now());

        Chunk withoutEmb = new Chunk();
        withoutEmb.setId(UUID.randomUUID());
        withoutEmb.setText("Other text");

        port.addChunksToChapter(chapterId, List.of(withEmb, withoutEmb));

        List<Chunk> byChapter = port.findChunksByChapterId(chapterId);
        assertThat(byChapter).hasSize(2);

        List<Chunk> embedded = port.findAllChunksWithEmbeddings();
        assertThat(embedded).extracting(Chunk::getId).contains(withEmb.getId());
        assertThat(embedded).extracting(Chunk::getId).doesNotContain(withoutEmb.getId());
    }
}
