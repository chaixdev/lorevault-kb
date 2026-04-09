package com.lorevault.api.search;

import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchFilters;
import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.search.Neo4jSemanticSearch;
import com.lorevault.api.testutil.fakes.FakeEmbeddingModel;
import com.lorevault.api.testutil.fakes.FakeNeo4jSemanticSearch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("service")
@DisplayName("SemanticSearchService")
class SemanticSearchServiceTest {

    @Test
    @DisplayName("should return results ordered and limited by topK with filters applied")
    void shouldReturnResultsOrderedAndLimited() {
        var embedding = new FakeEmbeddingModel("fake-model", 8);
        var search = new FakeNeo4jSemanticSearch();
        var service = new SemanticSearchService(embedding, search);

        var req = new SemanticSearchRequest();
        req.setQuery("shards are power");
        req.setTopK(2);
        var filters = new SemanticSearchFilters();
        filters.setUniverse("Cosmere");
        req.setFilters(filters);

        float[] qRaw = embedding.embed(req.getQuery());
        double[] q = new double[qRaw.length];
        for (int i = 0; i < qRaw.length; i++) {
            q[i] = qRaw[i];
        }
        Neo4jSemanticSearch.SearchFilters f = new Neo4jSemanticSearch.SearchFilters("Cosmere", null, null, null);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        UUID c3 = UUID.randomUUID();
        search.configureResults(q, f, List.of(
                FakeNeo4jSemanticSearch.result(c1, 0.9, "A", UUID.randomUUID(), 1, 1),
                FakeNeo4jSemanticSearch.result(c2, 0.8, "B", UUID.randomUUID(), 1, 2),
                FakeNeo4jSemanticSearch.result(c3, 0.7, "C", UUID.randomUUID(), 1, 3)
        ));

        var resp = service.search(req);
        assertThat(resp.getResults()).hasSize(2);
        assertThat(resp.getResults().get(0).getChunkId()).isEqualTo(c1);
        assertThat(resp.getResults().get(1).getChunkId()).isEqualTo(c2);
        assertThat(resp.getMetadata().getQuery()).isEqualTo(req.getQuery());
        assertThat(resp.getMetadata().getReturnedResults()).isEqualTo(2);
        assertThat(resp.getMetadata().getTotalResults()).isEqualTo(2);
    }

    @Test
    @DisplayName("should report availability via port")
    void shouldReportAvailability() {
        var embedding = new FakeEmbeddingModel();
        var search = new FakeNeo4jSemanticSearch();
        var service = new SemanticSearchService(embedding, search);

        search.setAvailable(false);
        assertThat(service.isAvailable()).isFalse();
        search.setAvailable(true);
        assertThat(service.isAvailable()).isTrue();
    }
}
