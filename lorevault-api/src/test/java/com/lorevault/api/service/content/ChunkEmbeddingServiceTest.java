package com.lorevault.api.service.content;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkEmbeddingServiceTest {

    @Mock private ContentPersistencePort contentPersistencePort;
    @Mock private EmbeddingPort embeddingPort;
    @InjectMocks private ChunkEmbeddingService service;

    private UUID chapterId;

    @BeforeEach
    void setup() {
        chapterId = UUID.randomUUID();
        service.setBatchSize(32);
        service.setEmbeddingDim(1536);
    }

    @Test
    void generateEmbeddingsForChapter_WhenAllUpToDate_ShouldReturnZeroAndNotCallBatch() {
        when(embeddingPort.getModelId()).thenReturn("gemini-embedding-001");
        ChunkNode node = new ChunkNode();
        node.setId(UUID.randomUUID());
        node.setContentHash("abc");
        node.setEmbedding(new double[]{0.1});
        node.setEmbeddingHash(sha256("gemini-embedding-001:abc"));
        node.setEmbeddedAt(LocalDateTime.now());
        when(contentPersistencePort.findChunksByChapterId(chapterId)).thenReturn(List.of(node));

        int updated = service.generateEmbeddingsForChapter(chapterId);

        assertThat(updated).isZero();
        verify(embeddingPort, never()).embedBatch(anyList());
    }

    @Test
    void generateEmbeddingsForChapter_WhenMissingOrStale_ShouldUpdateAndCallBatch() {
        when(embeddingPort.getModelId()).thenReturn("gemini-embedding-001");
        ChunkNode node1 = new ChunkNode();
        node1.setId(UUID.randomUUID());
        node1.setContentHash("hash1"); // missing embedding
        ChunkNode node2 = new ChunkNode();
        node2.setId(UUID.randomUUID());
        node2.setContentHash("hash2");
        node2.setEmbedding(new double[]{0.2});
        node2.setEmbeddingHash("stale"); // mismatch
        when(contentPersistencePort.findChunksByChapterId(chapterId)).thenReturn(List.of(node1, node2));

        when(embeddingPort.embedBatch(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> new double[]{0.5, 0.6}).toList();
        });

        int updated = service.generateEmbeddingsForChapter(chapterId);

        assertThat(updated).isEqualTo(2);
        verify(embeddingPort, times(1)).embedBatch(anyList());
    }

    @Test
    void generateEmbeddingsForChapter_NoChunks_ShouldReturnZero() {
        when(contentPersistencePort.findChunksByChapterId(chapterId)).thenReturn(List.of());
        int updated = service.generateEmbeddingsForChapter(chapterId);
        assertThat(updated).isZero();
        verifyNoInteractions(embeddingPort);
    }

    private String sha256(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
