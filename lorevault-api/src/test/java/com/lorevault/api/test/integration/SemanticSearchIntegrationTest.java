package com.lorevault.api.test.integration;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.dto.search.SemanticSearchDtos;
import com.lorevault.api.domain.shared.PublicationCoordinates;
import com.lorevault.api.repository.ChapterRepository;
import com.lorevault.api.repository.ChunkRepository;
import com.lorevault.api.service.ingestion.IngestionService;
import com.lorevault.api.test.IntegrationTestBase;
import com.lorevault.api.web.search.SemanticSearchController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "vector-int"})
class SemanticSearchIntegrationTest extends IntegrationTestBase {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private SemanticSearchController searchController;

    @Test
    void ingestionToEmbeddingToSearchRoundTrip() {
        // Given: Submit a chapter for ingestion
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setCoordinates(createTestCoordinates());
        request.setChapterTitle("Test Chapter");
        request.setChapterText("This is a story about dragons and knights. The dragon lived in a castle.");

        // When: Process the chapter
        SubmitChapterResponse response = ingestionService.submitChapter(request);
        UUID chapterId = response.getChapterId();

        // Then: Verify chapter and chunks were created
        Chapter chapter = chapterRepository.findById(chapterId).orElseThrow();
        assertThat(chapter).isNotNull();
        assertThat(chapter.getRawText()).isEqualTo("This is a story about dragons and knights. The dragon lived in a castle.");

        List<Chunk> chunks = chunkRepository.findByChapterIdOrderByChunkNumber(chapterId);
        assertThat(chunks).isNotEmpty();

        // And: Semantic search should return results
        SemanticSearchDtos.Request searchRequest = new SemanticSearchDtos.Request();
        searchRequest.setQuery("dragon castle");
        searchRequest.setTopK(5);

        SemanticSearchDtos.Response searchResponse = searchController.semantic(searchRequest).getBody();
        assertThat(searchResponse).isNotNull();
        assertThat(searchResponse.getResults()).isNotEmpty();

        // Verify that returned chunk IDs match stored chunks
        List<String> returnedChunkIds = searchResponse.getResults().stream()
                .map(SemanticSearchDtos.ResultItem::getChunkId)
                .filter(id -> id != null)
                .toList();
        
        List<String> storedChunkIds = chunks.stream()
                .map(chunk -> chunk.getId().toString())
                .toList();

        assertThat(returnedChunkIds).isSubsetOf(storedChunkIds);
        
        // Verify chapter ID matches
        for (SemanticSearchDtos.ResultItem item : searchResponse.getResults()) {
            if (item.getChapterId() != null) {
                assertThat(UUID.fromString(item.getChapterId())).isEqualTo(chapterId);
            }
        }
    }

    private PublicationCoordinates createTestCoordinates() {
        PublicationCoordinates coords = new PublicationCoordinates();
        coords.setUniverse("TestUniverse");
        coords.setBookNumber(1);
        coords.setChapterNumber(1);
        return coords;
    }
}
