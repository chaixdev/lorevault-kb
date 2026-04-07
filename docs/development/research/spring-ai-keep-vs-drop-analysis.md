# Spring AI: Keep vs. Drop Analysis
**Date**: April 2026  
**Project**: LoreVault v0.8.3-SNAPSHOT (Java 21 / Spring Boot 3.5.4)  
**Current BOM**: `spring-ai-bom:1.0.0`  
**Recommended target**: `spring-ai-bom:1.1.4`

---

## 1. Recommendation: **KEEP — and upgrade to 1.1.4**

**Verdict: Keep Spring AI. Do not drop it.**

The evidence from the codebase shows that Spring AI is not a cosmetic dependency. It is load-bearing:

| Usage site | What it does | Dropable? |
|---|---|---|
| `SceneDetectionClient` | `ChatClient`, `PromptTemplate`, `OpenAiChatOptions` | ❌ — core API |
| `RagService` | `ChatClient` for answer synthesis | ❌ — core API |
| `TriadOrchestrationService` | `PromptTemplate` rendering | ❌ — core API |
| `EmbeddingModelAdapter` | Currently bypasses Spring AI (raw `RestTemplate`) | ✅ — replaceable with Spring AI's `EmbeddingModel` |

Dropping Spring AI means reimplementing `ChatClient`, `PromptTemplate`, retry wiring, and streaming support via raw `HttpClient`. That is roughly 800–1,200 lines of infrastructure code for zero feature gain. It also means losing access to the matured features in 1.1.x that directly address LoreVault's open problems (token logging, vector store abstraction, chat memory, Micrometer integration).

The only scenario where dropping is correct is if the project were to permanently freeze at current scope with zero future AI capability expansion. That contradicts the stated v0.9.0+ roadmap.

### Cost/benefit summary

| Factor | Drop | Keep + Upgrade |
|---|---|---|
| Lines of replacement code to write | ~1,000+ | 0 |
| Token counting (actual, not estimated) | Build yourself | Free via Micrometer |
| Embedding model abstraction | Build yourself | Free via `EmbeddingModel` |
| Spring Boot 3.5.x compatibility | N/A | ✅ confirmed (1.1.4) |
| Spring Boot 4.x path | N/A | Spring AI 2.0 (ready when you upgrade) |
| CVE maintenance burden | Your code | Spring project's responsibility |
| Risk of API drift from OpenAI | High (raw HTTP) | Low (Spring AI abstracts it) |

---

## 2. Migration Scope: Spring AI 1.0.0 → 1.1.4

### What changed between 1.0.0 and 1.1.4 that affects LoreVault

Spring AI 1.1 GA (November 2025) was a major release with 850+ improvements. The Spring AI team published an [OpenRewrite migration recipe](https://github.com/arconia-io/arconia-migrations/blob/main/docs/spring-ai.md) that automates the majority of package-level renames.

#### Breaking changes relevant to LoreVault

**1. `ChatClient` builder API** — The `ChatClient.Builder` factory method changed slightly in 1.1. If LoreVault constructs `ChatClient` beans manually in a `@Configuration`, the builder call may need updating. Verify the configuration class that produces the `nlpSmall` and `nlpBig` `ChatClient` beans.

**2. `PromptTemplate` constructor** — In 1.1, `PromptTemplate` no longer accepts a raw `String` in all constructors the same way. The `template.render(Map.of())` pattern used in `TriadOrchestrationService` and `SceneDetectionClient` should still compile but must be tested.

**3. `OpenAiChatOptions.builder()` API** — The builder methods `.temperature()`, `.topP()`, `.maxTokens()` are unchanged in 1.1.4. No action needed.

**4. `spring-ai-client-chat` artifact** — This artifact exists in 1.0.0 and 1.1.x under the same coordinates. No change needed in `pom.xml`.

**5. Auto-configuration split** — In 1.1, Spring AI more aggressively separates `spring-ai-openai` (core client) from `spring-ai-openai-spring-boot-autoconfigure` (auto-config). Since LoreVault uses `spring-ai-openai` without the autoconfigure starter (confirmed in pom.xml), this should be transparent.

#### Non-breaking improvements immediately available after upgrade

- **`TokenCountBatchingStrategy`** — replace `EmbeddingModelAdapter`'s manual batch loop
- **Micrometer `gen_ai.client.token.usage`** — real token counts instead of `estimateTokens()` heuristic
- **`Neo4jVectorStore` auto-config** — can replace `Neo4jSemanticSearchAdapter` (see §4)
- **`MessageWindowChatMemory`** — structured two-pass context (see §3)

### Step-by-step migration checklist

```
[ ] 1. Bump BOM in root pom.xml:
        spring-ai-bom: 1.0.0 → 1.1.4

[ ] 2. Run: mvn dependency:tree -Dincludes=org.springframework.ai
        Verify no version conflicts with Boot 3.5.4 managed deps.

[ ] 3. Compile: mvn clean compile
        Fix any API-level compile errors (PromptTemplate, ChatClient builder).

[ ] 4. Run unit tests: mvn test
        All 1.0.0 Spring AI usage is already tested.

[ ] 5. Run integration tests: mvn verify -P integration-tests
        Verifies ChatClient + Neo4j wiring end-to-end.

[ ] 6. Smoke test scene detection locally with a sample chapter.

[ ] 7. Optionally: add spring-ai-starter-vector-store-neo4j to pom.xml
        (replaces Neo4jSemanticSearchAdapter — see §4)
```

**Estimated effort: 2–4 hours** for the BOM bump + compilation fixes. The OpenRewrite recipe can automate any package renames if needed:

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.arconia.migrations:arconia-migrations-spring-ai:LATEST \
  -Drewrite.activeRecipes=io.arconia.migrations.spring.ai.UpgradeSpringAi_1_1
```

---

## 3. XML → JSON Scene Detection Migration Decision

### Current state (what the code actually does)

`SceneDetectionClient` asks the LLM for XML. `TriadXmlParser` then parses the XML response into `TriadResult(timelineMarker, prevToCurr, currToNext)` records. The cleanup step exists specifically because the LLM sometimes wraps the XML in markdown code fences (`` ```xml ``).

The XML structure is:
```xml
<scene_analysis>
  <timeline_marker>...</timeline_marker>
  <relationships>
    <previous_to_current>
      <temporal_type>BEFORE</temporal_type>
      <certainty>HIGH</certainty>
      <evidence>...</evidence>
    </previous_to_current>
    <current_to_next>...</current_to_next>
  </relationships>
</scene_analysis>
```

### Spring AI 1.1's `entity()` structured output

Spring AI 1.1 offers `chatClient.prompt()...call().entity(MyRecord.class)`. The framework generates a JSON Schema from the Java record, appends it to the system prompt, and deserializes the response directly. With native structured output enabled (`AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`), the JSON Schema is sent directly to the model API's `response_format` field, which guarantees valid JSON without prompt-appending tricks.

### Migration path (if desired)

Replace `TriadXmlParser.TriadResult` with a Java record:

```java
// New: replaces TriadXmlParser.TriadResult
public record TriadResult(
    @JsonProperty("timeline_marker") String timelineMarker,
    @JsonProperty("prev_to_curr") Relation prevToCurr,
    @JsonProperty("curr_to_next") Relation currToNext
) {
    public record Relation(
        @JsonProperty("temporal_type") String temporalType,
        String certainty,
        String evidence
    ) {}
}
```

Then in `SceneDetectionClient.detectScenesPass2Triad()`:
```java
// Before (XML string):
return chatClient.prompt()
    .system(systemPrompt)
    .user(userInput)
    .options(options)
    .call()
    .content();

// After (typed record, XML parser eliminated):
return chatClient.prompt()
    .system(systemPrompt)
    .user(userInput)
    .options(options)
    .call()
    .entity(TriadResult.class);
```

This eliminates:
- `TriadXmlParser` (96 lines)
- The markdown fence cleanup logic
- The `DocumentBuilder` XML parsing infrastructure
- The current `null`-return fallback path on malformed XML

### Decision

**Migrate to JSON structured output.** The XML layer exists solely because it was the easiest way to get structured data from the LLM at the time of writing. With Spring AI 1.1's native structured output, the XML parser becomes dead weight. The migration is a straightforward record definition + `.entity()` call change.

**Prerequisite**: This requires the LLM in use (Groq's llama-3.3-70b-versatile) to support JSON mode or structured output. Groq does support OpenAI-compatible `response_format: {type: "json_object"}`. Verify this before migrating the system prompt away from XML instructions.

**Recommended sequence**: Do the BOM upgrade first (§2), validate it works, then migrate the XML parser as a separate PR. Do not bundle them.

---

## 4. Custom Ingestion Pipeline vs. Spring AI ETL + RetrievalAugmentationAdvisor

### What LoreVault currently builds manually

#### Embedding pipeline (`EmbeddingModelAdapter`)

`EmbeddingModelAdapter` is 148 lines of raw `RestTemplate` HTTP:
- Manual JSON body construction (`objectMapper.createObjectNode()`)
- Manual response parsing (`root.get("data")`)  
- Manual retry with exponential backoff
- Manual dimension configuration
- Manual batch alignment with empty-vector fallback

**Spring AI replacement**: `EmbeddingModel` from `spring-ai-starter-model-openai`. It handles batching, retry, dimension config, and alignment automatically. The adapter's entire body reduces to:

```java
// Replaces all of EmbeddingModelAdapter
@Component
public class EmbeddingModelAdapterV2 implements EmbeddingPort {
    private final EmbeddingModel embeddingModel;
    
    @Override
    public double[] embed(String text) {
        return embeddingModel.embed(text);
    }
    
    @Override
    public List<double[]> embedBatch(List<String> texts) {
        EmbeddingRequest req = new EmbeddingRequest(texts, EmbeddingOptions.EMPTY);
        return embeddingModel.call(req).getResults()
            .stream().map(r -> r.getOutput()).toList();
    }
}
```

**Verdict: Replace `EmbeddingModelAdapter` with Spring AI's `EmbeddingModel`.** 148 lines → ~20 lines.

#### Vector search (`Neo4jSemanticSearchAdapter`)

`Neo4jSemanticSearchAdapter` is 165 lines of hand-written Cypher:
- Raw `db.index.vector.queryNodes` call
- Manual oversample-then-filter strategy
- Manual `double[]` → `List<Double>` conversion
- Manual result mapping via `typeSystem, record` lambda
- Dual relationship pattern handling (`HAS_CHUNK` vs `HAS_SCENE→HAS_CHUNK`)

**Spring AI replacement**: `Neo4jVectorStore` from `spring-ai-starter-vector-store-neo4j`. It handles:
- Index management and HNSW k-ANN queries
- Portable metadata filtering (translated to Cypher `WHERE`)
- `TokenCountBatchingStrategy` for batch embedding
- Spring Boot auto-configuration

**However**, there is a complication: LoreVault's graph schema uses its own `Chunk` nodes with domain-specific relationships (`HAS_SCENE → HAS_CHUNK`). `Neo4jVectorStore` manages its own node label (`Document` by default, configurable). Migrating to it would require either:

1. **Dual-write**: Store chunks in both LoreVault's `Chunk` nodes and Spring AI's `Document` nodes (storage cost but zero migration risk)
2. **Schema migration**: Rename `Chunk` nodes to match Spring AI's expected structure (high migration risk)
3. **Custom index config**: Configure `Neo4jVectorStore` to use LoreVault's existing index name (`chunk_embedding_idx`) and label — this is supported via `Neo4jVectorStoreConfig`.

**Verdict: Defer `Neo4jSemanticSearchAdapter` replacement until v0.9.0 or later.** The dual relationship pattern (`HAS_SCENE → HAS_CHUNK`) is schema logic that Spring AI's generic store cannot express without custom Cypher anyway. The current adapter works correctly. Keep it for now, add a TODO to evaluate the custom `Neo4jVectorStoreConfig` approach when the schema stabilizes.

#### RAG pipeline (`RagService`)

`RagService` is 309 lines of hand-assembled RAG:
- Manual semantic search call
- Manual threshold filtering
- Manual context string assembly (`[1] text... [2] text...`)
- Manual system/user prompt construction
- Manual citation building with chapter lookups

**Spring AI replacement**: `RetrievalAugmentationAdvisor` with `VectorStoreDocumentRetriever`. In the simplest form:

```java
ChatClient.create(chatModel)
    .prompt()
    .advisors(RetrievalAugmentationAdvisor.builder()
        .documentRetriever(VectorStoreDocumentRetriever.builder()
            .vectorStore(neo4jVectorStore)
            .topK(request.getTopK())
            .build())
        .build())
    .user(request.getQuestion())
    .call()
    .content();
```

**However**, this requires migrating to `Neo4jVectorStore` first (see above). If the vector store migration is deferred, the RAG advisor cannot be used. The current `RagService` is tightly coupled to `SemanticSearchService → Neo4jSemanticSearchAdapter → raw Cypher`.

**Verdict: Defer `RagService` replacement until `Neo4jVectorStore` migration is done.** When the time comes, the `RetrievalAugmentationAdvisor` replaces roughly 200 of the 309 lines. The citation-building logic (which requires domain-specific chapter lookups) will still need to remain as application code.

#### LLM call logging (`LlmCallLoggingService` / `LlmCallRecord`)

`LlmCallLoggingService` is 136 lines of:
- Manual token estimation (4 chars/token heuristic)
- SHA-256 truncation + integrity hashing
- Status record linking
- Conditional body persistence

Spring AI 1.1 provides Micrometer counters for real token counts: `gen_ai.client.token_usage{gen_ai_token_type=input|output|total}`. These replace the estimation heuristic.

**However**, `LlmCallRecord` does more than Micrometer: it persists per-call records to Neo4j with full response body, truncation metadata, prompt template IDs, and job linkage. Micrometer gives you metrics, not audit records. These are different concerns.

**Verdict: Keep `LlmCallRecord` and `LlmCallLoggingService` as-is.** Replace only the token estimation (`estimateTokens()` heuristic in `SceneDetectionClient`) by reading actual token counts from the `ChatResponse.getMetadata().getUsage()` object, which is available in Spring AI 1.0+ and populated by the OpenAI client. This is a 5-line change, not a removal.

---

## 5. Consolidated Action Plan

### Phase 1: BOM upgrade (low risk, immediate value)
```
Priority: HIGH
Effort: 2–4 hours
Risk: Low

1. Bump spring-ai-bom to 1.1.4 in root pom.xml
2. Fix any compile errors
3. Replace token estimation heuristic in SceneDetectionClient with:
   ChatResponse response = chatClient.prompt()...call().chatResponse();
   Usage usage = response.getMetadata().getUsage();
   int inputTokens = usage.getPromptTokens();
   int outputTokens = usage.getGenerationTokens();
4. All tests pass → merge
```

### Phase 2: Eliminate EmbeddingModelAdapter (medium risk, big win)
```
Priority: HIGH
Effort: 1–2 hours
Risk: Medium (changes embedding call path)

1. Add spring-ai-openai auto-config or configure EmbeddingModel bean
2. Rewrite EmbeddingModelAdapter to delegate to EmbeddingModel
3. Remove 148-line RestTemplate implementation
4. Verify batch alignment and dimension config via integration test
```

### Phase 3: XML → JSON structured output migration (medium risk, cleanliness win)
```
Priority: MEDIUM
Effort: 3–5 hours
Risk: Medium (prompt changes + LLM behavior change)

1. Define TriadResult as a Java record with JSON annotations
2. Update scene-detection-pass2 system prompt to request JSON (not XML)
3. Update SceneDetectionClient.detectScenesPass2Triad() to use .entity()
4. Delete TriadXmlParser (96 lines)
5. Full integration test with real LLM call
6. Verify Groq llama-3.3-70b-versatile produces valid JSON with this approach
```

### Phase 4: Neo4jVectorStore + RAG advisor (high risk, deferred)
```
Priority: LOW (defer to v0.9.0+)
Effort: 1–2 days
Risk: High (schema migration, dual relationship patterns)

1. Evaluate Neo4jVectorStoreConfig to use existing chunk_embedding_idx
2. If feasible: dual-write Chunk nodes + Spring AI Document nodes
3. Migrate Neo4jSemanticSearchAdapter to Neo4jVectorStore
4. Migrate RagService to RetrievalAugmentationAdvisor
5. Keep citation-building logic as application code
```

---

## 6. Files affected by Phases 1–3

| File | Change type |
|---|---|
| `pom.xml` (root) | BOM version bump |
| `SceneDetectionClient.java` | Replace `estimateTokens()` heuristic with `ChatResponse.getMetadata().getUsage()` |
| `EmbeddingModelAdapter.java` | Rewrite body (~130 lines deleted, ~15 lines added) |
| `TriadXmlParser.java` | **DELETE** (Phase 3) |
| `SceneDetectionClient.java` | `.entity(TriadResult.class)` replaces `.content()` in triad method (Phase 3) |
| Triad system prompts (resources) | Update XML instructions → JSON instructions (Phase 3) |

No domain model changes. No repository changes. No controller changes.

---

*Document status: Final — all four downstream research deliverables complete.*
