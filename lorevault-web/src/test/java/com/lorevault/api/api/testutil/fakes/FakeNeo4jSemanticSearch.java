package com.lorevault.api.testutil.fakes;

import com.lorevault.api.search.Neo4jSemanticSearch;
import com.lorevault.api.search.SpoilerVisibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake Neo4j semantic search for unit/service tests.
 * Allows pre-configuring results per query vector hash and applies topK and simple filter checks.
 */
public class FakeNeo4jSemanticSearch extends Neo4jSemanticSearch {

	private final Map<Integer, List<SearchResult>> byKey = new ConcurrentHashMap<>();
	private volatile boolean available = true;

	public FakeNeo4jSemanticSearch() {
		super(null);
	}

	public List<SearchResult> search(double[] queryEmbedding, int topK, SearchFilters filters) {
		return search(queryEmbedding, topK, filters, null);
	}

	@Override
	public List<SearchResult> search(double[] queryEmbedding, int topK, SearchFilters filters,
			SpoilerVisibility visibility) {
		int key = keyOf(queryEmbedding, filters);
		List<SearchResult> list = new ArrayList<>(byKey.getOrDefault(key, List.of()));
		list.sort(Comparator.comparingDouble(SearchResult::score).reversed());
		if (topK > 0 && list.size() > topK) {
			return new ArrayList<>(list.subList(0, topK));
		}
		return list;
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public void configureResults(double[] queryEmbedding, SearchFilters filters, List<SearchResult> results) {
		byKey.put(keyOf(queryEmbedding, filters), new ArrayList<>(results));
	}

	public void clear() { byKey.clear(); }

	private int keyOf(double[] vec, SearchFilters filters) {
		int h = 1;
		if (vec != null) {
			for (int i = 0; i < Math.min(vec.length, 8); i++) {
				long bits = Double.doubleToLongBits(vec[i]);
				h = 31 * h + (int)(bits ^ (bits >>> 32));
			}
		}
		if (filters != null) {
			h = 31 * h + (filters.universe() == null ? 0 : filters.universe().hashCode());
			h = 31 * h + (filters.series() == null ? 0 : filters.series().hashCode());
			h = 31 * h + (filters.bookNumber() == null ? 0 : filters.bookNumber().hashCode());
			h = 31 * h + (filters.chapterNumber() == null ? 0 : filters.chapterNumber().hashCode());
		}
		return h;
	}

	// ── Convenience factory methods ───────────────────────────────────────────

	/** Creates a minimal result with no scene/entity data (null/empty defaults). */
	public static SearchResult result(UUID chunkId, double score, String snippet,
			UUID chapterId, Integer bookNumber, Integer chapterNumber) {
		return new SearchResult(chunkId, score, snippet, chapterId, bookNumber, chapterNumber,
				null, null, List.of(), List.of());
	}

	/** Creates a result with full scene/entity context. */
	public static SearchResult result(UUID chunkId, double score, String snippet,
			UUID chapterId, Integer bookNumber, Integer chapterNumber,
			UUID sceneId, String sceneSummary,
			List<String> individualsPresent, List<String> locationsPresent) {
		return new SearchResult(chunkId, score, snippet, chapterId, bookNumber, chapterNumber,
				sceneId, sceneSummary, individualsPresent, locationsPresent);
	}
}
