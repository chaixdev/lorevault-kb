package com.lorevault.api.service.content;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.repository.ChapterRepository;
import com.lorevault.api.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkEmbeddingServiceTest {

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    @InjectMocks
    private ChunkEmbeddingService service;

    private UUID chapterId;
    private Chapter chapter;

    @BeforeEach
    void setUp() {
        chapterId = UUID.randomUUID();
        chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setRawText("Hello world. This is sample text.");

        // Set default model id in service for predictable metadata
        ReflectionTestUtils.setField(service, "embeddingModelId", "test-embed-model");
    }

    @Test
    void generateEmbeddingsForChapter_WhenChunksExist_ShouldAddDocuments() {
        // Given
        Chunk c1 = new Chunk();
        c1.setId(UUID.randomUUID());
        c1.setStartCharInChapter(0);
        c1.setEndCharInChapter(5); // "Hello"
        c1.setChunkNumberInChapter(1);

        Chunk c2 = new Chunk();
        c2.setId(UUID.randomUUID());
        c2.setStartCharInChapter(6);
        c2.setEndCharInChapter(1000); // intentionally beyond text length to test clamping
        c2.setChunkNumberInChapter(2);

        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(chunkRepository.findByChapterIdOrderByChunkNumber(chapterId)).thenReturn(List.of(c1, c2));

        List<Document> captured = new ArrayList<>();
        doAnswer(invocation -> {
            List<Document> docs = invocation.getArgument(0);
            captured.addAll(docs);
            return null;
        }).when(vectorStore).add(anyList());

        // When
        int count = service.generateEmbeddingsForChapter(chapterId);

        // Then
        assertThat(count).isEqualTo(2);
        verify(vectorStore, times(1)).add(anyList());
        assertThat(captured).hasSize(2);

        Document d1 = captured.get(0);
        assertThat(d1.getText()).isEqualTo("Hello");
        assertThat(d1.getMetadata()).containsEntry("type", "CHUNK");
        assertThat(d1.getMetadata()).containsEntry("chapterId", chapterId.toString());
        assertThat(d1.getMetadata()).containsKey("chunkId");
        assertThat(d1.getMetadata()).containsEntry("modelId", "test-embed-model");
        assertThat(d1.getMetadata()).containsKey("generatedAt");

        Document d2 = captured.get(1);
        assertThat(d2.getText()).isNotBlank();
    }

    @Test
    void generateEmbeddingsForChapter_WhenNoChunks_ShouldDoNothing() {
        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(chunkRepository.findByChapterIdOrderByChunkNumber(chapterId)).thenReturn(List.of());

        int count = service.generateEmbeddingsForChapter(chapterId);

        assertThat(count).isZero();
        verify(vectorStore, never()).add(anyList());
    }
}
