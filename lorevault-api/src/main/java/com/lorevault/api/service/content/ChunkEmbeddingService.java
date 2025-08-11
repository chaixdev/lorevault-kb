package com.lorevault.api.service.content;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkEmbeddingService {

    @Value("${spring.ai.openai.embedding.options.model:gemini-embedding-001}")
    private String embeddingModelId;

    @Transactional(readOnly = true)
    public int generateEmbeddingsForChapter(java.util.UUID chapterId) {
        log.debug("Embeddings generation skipped for chapter {} (deferred)", chapterId);
        return 0;
    }

    @Transactional(readOnly = true)
    public List<org.springframework.ai.document.Document> search(String query, int limit, double threshold) {
        return List.of();
    }
}
