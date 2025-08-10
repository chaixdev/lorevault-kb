package com.lorevault.api.service.search;

import com.lorevault.api.dto.search.SemanticSearchDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final VectorStore vectorStore;

    public SemanticSearchDtos.Response search(SemanticSearchDtos.Request request) {
        int topK = request.getTopK() != null ? Math.max(1, request.getTopK()) : 5;
        double threshold = request.getThreshold() != null ? request.getThreshold() : 0.0;

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.getQuery())
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build()
        );
        if (docs == null || docs.isEmpty()) {
            return new SemanticSearchDtos.Response(Collections.emptyList());
        }

        List<SemanticSearchDtos.ResultItem> items = docs.stream().map(d -> {
            Map<String, Object> md = d.getMetadata();
            String chunkId = md != null && md.get("chunkId") != null ? String.valueOf(md.get("chunkId")) : null;
            String chapterId = md != null && md.get("chapterId") != null ? String.valueOf(md.get("chapterId")) : null;
            Double score = d.getScore();
            String content = d.getText();
            return new SemanticSearchDtos.ResultItem(chunkId, chapterId, content, score);
        }).collect(Collectors.toList());

        return new SemanticSearchDtos.Response(items);
    }
}
