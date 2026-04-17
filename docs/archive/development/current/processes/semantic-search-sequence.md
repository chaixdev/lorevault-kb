# Semantic Search Flow - Sequence Diagram

This diagram shows the complete flow when a user submits a Natural Language Query (NLQ) to the semantic search endpoint.

```mermaid
sequenceDiagram
    participant Client
    participant Controller as SemanticSearchController
    participant Service as SemanticSearchService
    participant EmbedPort as EmbeddingPort
    participant EmbedAdapter as EmbeddingModelAdapter
    participant SearchPort as SemanticSearchPort
    participant SearchAdapter as InMemorySemanticSearchAdapter
    participant PersistPort as ContentPersistencePort
    participant Neo4j as Neo4j Database
    participant OpenAI as OpenAI API

    Note over Client, OpenAI: Natural Language Query Processing Flow

    Client->>Controller: POST /api/search/semantic
    Note right of Client: {"query": "character development", "topK": 5}

    Controller->>Controller: Log request details
    Controller->>Service: isAvailable()
    
    Service->>SearchPort: isAvailable()
    SearchAdapter->>PersistPort: findAllChunksWithEmbeddings()
    PersistPort->>Neo4j: MATCH (ch:Chunk) WHERE ch.embedding IS NOT NULL
    Neo4j-->>PersistPort: List<ChunkNode>
    PersistPort-->>SearchAdapter: chunks with embeddings
    SearchAdapter-->>Service: !chunks.isEmpty()
    Service-->>Controller: true/false

    alt Service unavailable
        Controller-->>Client: HTTP 503 Service Unavailable
        Note right of Controller: Retry-After: 300 seconds
    end

    Controller->>Service: search(request)
    Service->>Service: Log search start, record startTime

    Note over Service, OpenAI: Query Embedding Generation Phase
    
    Service->>EmbedPort: embed(request.getQuery())
    EmbedPort->>EmbedAdapter: embed("character development")
    
    EmbedAdapter->>EmbedAdapter: Prepare HTTP request body
    Note right of EmbedAdapter: {"input": ["character development"], "model": "text-embedding-3-small"}
    
    EmbedAdapter->>OpenAI: POST /embeddings
    OpenAI-->>EmbedAdapter: {"data": [{"embedding": [0.1, 0.2, ...]}]}
    
    EmbedAdapter->>EmbedAdapter: Extract vector from response
    EmbedAdapter-->>Service: double[] queryEmbedding

    Service->>Service: Log embedding dimension
    Service->>Service: convertFilters(request.getFilters())

    Note over Service, Neo4j: Vector Similarity Search Phase

    Service->>SearchPort: search(queryEmbedding, topK, filters)
    SearchAdapter->>SearchAdapter: Log search parameters
    SearchAdapter->>PersistPort: findAllChunksWithEmbeddings()
    
    PersistPort->>Neo4j: MATCH (ch:Chunk) WHERE ch.embedding IS NOT NULL
    Neo4j-->>PersistPort: List<ChunkNode> (all embedded chunks)
    PersistPort-->>SearchAdapter: chunks

    SearchAdapter->>SearchAdapter: applyFilters(chunks, filters)
    Note right of SearchAdapter: Currently no-op in v0.7.0

    loop For each chunk
        SearchAdapter->>SearchAdapter: calculateSimilarity(chunk, queryEmbedding)
        Note right of SearchAdapter: cosineSimilarity(chunkVector, queryVector)
    end

    SearchAdapter->>SearchAdapter: Sort by score descending
    SearchAdapter->>SearchAdapter: Filter score > 0.0
    SearchAdapter->>SearchAdapter: Limit to topK results
    SearchAdapter->>SearchAdapter: Log processing time

    SearchAdapter-->>Service: List<SearchResult>

    Note over Service, Controller: Response Assembly Phase

    Service->>Service: Convert SearchResult to SearchResultDto
    Service->>Service: Calculate processingTime
    Service->>Service: Create SearchMetadata
    Service->>Service: Log completion stats

    Service-->>Controller: SemanticSearchResponse

    Controller->>Controller: Log success metrics
    Controller-->>Client: HTTP 200 + JSON response

    Note right of Client: Response:<br/>{"results": [{"chunkId": "...", "score": 0.89, "snippet": "..."}], "metadata": {...}}

    Note over Client, OpenAI: Error Handling Flows

    alt Embedding API Failure
        EmbedAdapter->>OpenAI: POST /embeddings
        OpenAI-->>EmbedAdapter: HTTP 429/500/timeout
        EmbedAdapter->>EmbedAdapter: Retry with backoff (up to 3 attempts)
        
        alt All retries failed
            EmbedAdapter-->>Service: RuntimeException("Embedding service unavailable")
            Service-->>Controller: Exception propagated
            Controller->>Controller: Log error
            Controller-->>Client: HTTP 500 Internal Server Error
        end
    end

    alt No chunks with embeddings
        SearchAdapter-->>Service: Empty List<SearchResult>
        Service-->>Controller: Response with empty results
        Controller-->>Client: HTTP 200 + empty results
    end

    alt Invalid request parameters
        Controller->>Controller: @Valid validation
        Controller-->>Client: HTTP 400 Bad Request
    end
```

## Key Technical Details

### Embedding Generation
- Uses OpenAI-compatible API (configurable endpoint)
- Typical dimensions: 3072 for the current LoreVault embedding standard
- Includes retry logic with exponential backoff
- Falls back to empty vectors on failure (skipped in persistence)

### Vector Similarity Search
- **Current Implementation (v0.7.0)**: In-memory cosine similarity
- Loads all chunks with embeddings from Neo4j
- Calculates similarity: `dot(a,b) / (norm(a) * norm(b))`
- Returns scores from -1 to 1 (higher = more similar)

### Performance Characteristics
- **Query Embedding**: ~100-300ms (external API call)
- **Vector Search**: ~1-50ms depending on chunk count
- **Total Response Time**: Typically 200-500ms for small datasets

### Future Optimizations (Roadmap)
- Replace in-memory search with Neo4j vector indexes
- Add approximate nearest neighbor (ANN) algorithms
- Implement database-level filtering
- Add result caching for common queries

## Request/Response Examples

### Request
```json
{
  "query": "character development arc",
  "topK": 5,
  "threshold": 0.7,
  "filters": {
    "universe": "Cosmere",
    "bookNumber": 1
  }
}
```

### Response
```json
{
  "results": [
    {
      "chunkId": "uuid-1",
      "score": 0.89,
      "snippet": "The character's growth throughout the journey...",
      "chapterId": "chapter-uuid",
      "bookNumber": 1,
      "chapterNumber": 3
    }
  ],
  "metadata": {
    "query": "character development arc",
    "totalResults": 1,
    "returnedResults": 1,
    "processingTimeMs": 245
  }
}
```
