package com.lorevault.api.test.integration;

import com.lorevault.api.dto.search.SemanticSearchDtos;
import com.lorevault.api.test.IntegrationTestBase;
import com.lorevault.api.web.search.SemanticSearchController;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "vector-int"})
class SimilarityOrderingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private SemanticSearchController searchController;

    @Test
    void similarityOrderingAndScorePresence() {
        // Given: Insert 3 documents with different but predictable content
        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("chunkId", "chunk-1");
        meta1.put("chapterId", "chapter-1");
        meta1.put("type", "CHUNK");
        Document doc1 = new Document("dragon fire castle medieval", meta1);

        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("chunkId", "chunk-2");
        meta2.put("chapterId", "chapter-1");
        meta2.put("type", "CHUNK");
        Document doc2 = new Document("knight sword battle armor", meta2);

        Map<String, Object> meta3 = new HashMap<>();
        meta3.put("chunkId", "chunk-3");
        meta3.put("chapterId", "chapter-2");
        meta3.put("type", "CHUNK");
        Document doc3 = new Document("dragon castle ancient treasure", meta3);

        vectorStore.add(List.of(doc1, doc2, doc3));

        // When: Search for "dragon castle" (should match doc1 and doc3 better than doc2)
        SemanticSearchDtos.Request request = new SemanticSearchDtos.Request();
        request.setQuery("dragon castle");
        request.setTopK(3);

        SemanticSearchDtos.Response response = searchController.semantic(request).getBody();

        // Then: Verify response structure
        assertThat(response).isNotNull();
        assertThat(response.getResults()).hasSize(3);

        // Verify ordering: documents with "dragon" and "castle" should rank higher
        List<SemanticSearchDtos.ResultItem> results = response.getResults();
        
        // First result should be doc1 or doc3 (both have "dragon castle")
        SemanticSearchDtos.ResultItem first = results.get(0);
        assertThat(first.getChunkId()).isIn("chunk-1", "chunk-3");
        assertThat(first.getScore()).isNotNull();
        assertThat(first.getScore()).isGreaterThan(0.0);

        // Scores should be in descending order
        for (int i = 0; i < results.size() - 1; i++) {
            Double currentScore = results.get(i).getScore();
            Double nextScore = results.get(i + 1).getScore();
            
            if (currentScore != null && nextScore != null) {
                assertThat(currentScore).isGreaterThanOrEqualTo(nextScore);
            }
        }

        // All results should have non-null chunk IDs and content
        for (SemanticSearchDtos.ResultItem item : results) {
            assertThat(item.getChunkId()).isNotNull();
            assertThat(item.getContent()).isNotBlank();
        }
    }
}
