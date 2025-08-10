package com.lorevault.api.service.search;

import com.lorevault.api.dto.search.SemanticSearchDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private SemanticSearchService semanticSearchService;

    @Test
    void search_WhenNoResults_ShouldReturnEmptyList() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        SemanticSearchDtos.Response resp = semanticSearchService.search(new SemanticSearchDtos.Request("q", 5, 0.0));
        assertThat(resp.getResults()).isEmpty();
    }

    @Test
    void search_WhenResultsExist_ShouldMapMetadataAndContent() {
        Document d = new Document("hello world", Map.of("chunkId", "c1", "chapterId", "ch1"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(d));

        SemanticSearchDtos.Response resp = semanticSearchService.search(new SemanticSearchDtos.Request("q", 5, 0.0));
        assertThat(resp.getResults()).hasSize(1);
        var r = resp.getResults().get(0);
        assertThat(r.getChunkId()).isEqualTo("c1");
        assertThat(r.getChapterId()).isEqualTo("ch1");
        assertThat(r.getContent()).isEqualTo("hello world");
        // score may be null depending on VectorStore implementation
    }
}
