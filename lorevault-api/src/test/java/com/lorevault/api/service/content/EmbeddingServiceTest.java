package com.lorevault.api.service.content;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.testutil.fakes.FakeContentPersistencePort;
import com.lorevault.api.testutil.fakes.FakeEmbeddingPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("service")
@DisplayName("EmbeddingService")
class EmbeddingServiceTest {

    @Test
    @DisplayName("should return 0 when chapter has no chunks")
    void shouldReturnZeroWhenNoChunks() {
        ContentPersistencePort repo = new FakeContentPersistencePort();
        var embed = new FakeEmbeddingPort("fake-model", 8);
        var svc = new EmbeddingService(repo, embed);
        svc.setEmbeddingDim(8);
        svc.setBatchSize(8);

        UUID chapterId = UUID.randomUUID();
        // Only chapter exists, no chunks
    Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setRawText("Some text");
        ((FakeContentPersistencePort) repo).createChapter(chapter);

        int updated = svc.generateEmbeddingsForChapter(chapterId);
        assertThat(updated).isEqualTo(0);
    }

    @Test
    @DisplayName("should embed only chunks needing embedding and skip up-to-date ones")
    void shouldEmbedOnlyWhenNeeded() throws Exception {
        FakeContentPersistencePort repo = new FakeContentPersistencePort();
        var embed = new FakeEmbeddingPort("fake-model", 8);
        var svc = new EmbeddingService(repo, embed);
        svc.setEmbeddingDim(8);
        svc.setBatchSize(8);

        UUID chapterId = UUID.randomUUID();
    Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        String rawText = "abcdefghijklmnopqrstuvwxyz. "+
                         "More content here to ensure substring operations work.";
        chapter.setRawText(rawText);
        repo.createChapter(chapter);

        // Prepare three chunks for the chapter
    List<Chunk> chunks = new ArrayList<>();

        // 1) Needs embedding (no embedding yet)
    Chunk c1 = new Chunk();
        c1.setId(UUID.randomUUID());
        c1.setStartCharInChapter(0);
        c1.setEndCharInChapter(10);
        c1.setContentHash("hash-1");
        chunks.add(c1);

        // 2) Up-to-date: already has embedding + correct hash
    Chunk c2 = new Chunk();
        c2.setId(UUID.randomUUID());
        c2.setStartCharInChapter(10);
        c2.setEndCharInChapter(20);
        c2.setContentHash("hash-2");
        c2.setEmbedding(new double[]{1,2,3});
        c2.setEmbeddingHash(sha256(embed.getModelId()+":"+c2.getContentHash()));
        chunks.add(c2);

        // 3) Needs update: has embedding but wrong hash
    Chunk c3 = new Chunk();
        c3.setId(UUID.randomUUID());
        c3.setStartCharInChapter(20);
        c3.setEndCharInChapter(30);
        c3.setContentHash("hash-3");
        c3.setEmbedding(new double[]{9,9});
        c3.setEmbeddingHash("mismatch");
        chunks.add(c3);

        // Register chunks under chapter in fake repo
        ((FakeContentPersistencePort) repo).chunksByChapter.put(chapterId, chunks);

        int updated = svc.generateEmbeddingsForChapter(chapterId);
        // c1 and c3 should be updated; c2 skipped
        assertThat(updated).isEqualTo(2);

        // Verify embedding hash updated to expected format for updated chunks
        String expectedHash1 = sha256(embed.getModelId()+":"+c1.getContentHash());
        String expectedHash3 = sha256(embed.getModelId()+":"+c3.getContentHash());
        assertThat(c1.getEmbedding()).isNotNull();
        assertThat(c1.getEmbeddingHash()).isEqualTo(expectedHash1);
        assertThat(c3.getEmbedding()).isNotNull();
        assertThat(c3.getEmbeddingHash()).isEqualTo(expectedHash3);
    }

    private static String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
