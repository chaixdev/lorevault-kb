package com.lorevault.api.service.content;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.repository.ChapterRepository;
import com.lorevault.api.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkEmbeddingService {

    private static final String MD_TYPE = "type";
    private static final String MD_TYPE_CHUNK = "CHUNK";
    private static final String MD_CHAPTER_ID = "chapterId";
    private static final String MD_CHUNK_ID = "chunkId";
    private static final String MD_MODEL_ID = "modelId";
    private static final String MD_GENERATED_AT = "generatedAt";

    private final ChapterRepository chapterRepository;
    private final ChunkRepository chunkRepository;
    private final VectorStore vectorStore;

    @Value("${spring.ai.openai.embedding.options.model:gemini-embedding-001}")
    private String embeddingModelId;

    @Transactional(readOnly = true)
    public int generateEmbeddingsForChapter(UUID chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        List<Chunk> chunks = chunkRepository.findByChapterIdOrderByChunkNumber(chapterId);
        if (chunks.isEmpty()) {
            log.info("No chunks found for chapter {}. Skipping embeddings.", chapterId);
            return 0;
        }

        String chapterText = chapter.getRawText();
        String now = OffsetDateTime.now().toString();

        List<Document> docs = new ArrayList<>(chunks.size());
        for (Chunk c : chunks) {
            int start = Math.max(0, Math.min(c.getStartCharInChapter(), chapterText.length()));
            int end = Math.max(start, Math.min(c.getEndCharInChapter(), chapterText.length()));
            String content = chapterText.substring(start, end);

            // Skip empty content defensively
            if (content.isBlank()) {
                log.debug("Skipping empty content for chunk {} in chapter {} (start={}, end={})", c.getId(), chapterId, start, end);
                continue;
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put(MD_TYPE, MD_TYPE_CHUNK);
            metadata.put(MD_CHAPTER_ID, chapterId.toString());
            metadata.put(MD_CHUNK_ID, c.getId().toString());
            metadata.put(MD_MODEL_ID, embeddingModelId);
            metadata.put(MD_GENERATED_AT, now);

            docs.add(new Document(content, metadata));
        }

        if (docs.isEmpty()) {
            log.info("No non-empty documents to embed for chapter {}.", chapterId);
            return 0;
        }

        vectorStore.add(docs);
        log.info("Added {} documents to vector store for chapter {}", docs.size(), chapterId);
        return docs.size();
    }

    @Transactional(readOnly = true)
    public List<Document> search(String query, int limit, double threshold) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(limit)
                        .similarityThreshold(threshold)
                        .build()
        );
    }
}
