# Retrieval-Oriented Chunking and Context Packing — April 2026

**Date:** April 2026  
**Status:** Brainstorm — not yet accepted  
**Purpose:** Reframe chunking as a retrieval and prompt-construction problem rather than a storage subdivision problem, and explore what refactor direction would best support LoreVault's long-form narrative Q&A goals.

---

## 1. Why This Needs Reconsideration

LoreVault's current chunking specification treats `Chunk` primarily as a technical subdivision of `Scene`.

That framing appears too narrow for the retrieval direction now emerging.

For narrative QA, chunking affects at least four distinct concerns:

1. **Embedding quality** — what text one vector represents
2. **Retrieval granularity** — what unit can be matched precisely against a query
3. **Fusion and reranking** — what unit is deduped and ranked across retrieval branches
4. **Prompt construction** — what text the answer-generation model should actually see

Those are related, but they are not the same problem.

The current specification likely over-optimizes for ingestion simplicity and under-specifies retrieval behavior.

---

## 2. Current State vs Emerging Direction

### Current State

Current chunking truth (see [Text Chunking Specification](../../patterns/ingestion/text-chunking-specification.md)):

- `Scene` is the primary narrative unit
- `Chunk` is a technical subdivision for downstream processing
- scenes under a size threshold become a single chunk
- larger scenes are subdivided with a sliding window

Current RAG truth (see [RAG Retrieval Chain](../../patterns/search/rag-retrieval-chain.md)):

- chunks are the current retrieval unit
- chunks are the current citation unit
- chunks are the current context-assembly unit fetched for answer generation

### Emerging Direction

Recent retrieval design discussion suggests a different framing:

- `Scene` should remain the primary narrative unit
- `Chunk` should likely be treated as the **retrieval and ranking unit**
- answer generation should probably consume **merged evidence blocks**, not raw retrieval chunks one-for-one

That implies LoreVault may need to distinguish:

- **retrieval unit**
- **ranking/fusion unit**
- **generation context unit**

rather than making one persisted `Chunk` abstraction serve all of them equally.

---

## 3. The Core Questions

### Question A — What should a persisted `Chunk` actually mean?

There are at least two viable interpretations:

#### Option 1 — Persist `Chunk` as a first-class node with text + vector

Example shape:

- `Chunk { id, text, startOffset, endOffset, chunkIndex, embedding... }`

Pros:

- simple and explicit
- easy to retrieve, cite, inspect, and debug
- current model already fits this shape
- chunk IDs remain a stable evidence unit for retrieval and citations

Cons:

- encourages overloading one abstraction for storage, retrieval, ranking, and generation
- may tempt the system toward coarse "one scene = one chunk" defaults that are convenient structurally but weak for retrieval
- duplicates text storage that may not be logically necessary if chapter/scene text is already authoritative

#### Option 2 — Persist chunk boundaries/embeddings as retrieval spans, not text blobs

Example shape:

- scene owns full text
- retrieval spans are effectively `(startOffset, endOffset, embedding, metadata)` records over that text

Pros:

- makes it explicit that chunking is a retrieval/indexing concern, not a separate content object in its own right
- avoids overcommitting to chunk-as-document semantics
- may simplify future re-chunking if the authoritative text remains elsewhere

Cons:

- potentially more awkward for citations, inspection, and debugging
- may complicate persistence and query ergonomics without producing much practical benefit
- may be overthinking a problem that a first-class chunk node already solves well enough

### Working intuition

It is plausible that LoreVault should **keep persisted chunks as first-class nodes**, but redefine them conceptually:

- not as a storage optimization
- but as the persisted unit of retrieval and evidence anchoring

That would preserve operational simplicity while correcting the product framing.

This remains an open design question, not accepted truth.

---

### Question B — Should chunk size be driven by scene length thresholds at all?

The current spec uses a threshold gate:

- short scene → single chunk
- long scene → chunk subdivision

This is likely too ingestion-centric.

For retrieval, the more relevant question is:

> What unit size best represents one semantically coherent retrieval candidate?

That suggests chunking should be driven more by:

- semantic coherence
- token budget
- dialogue continuity
- event/beat boundaries

and less by:

- whether the scene happened to be short enough to avoid subdivision.

---

### Question C — Should retrieval unit and generation unit diverge?

This appears increasingly likely.

Potential future design:

- retrieve/rank on smaller chunk units
- dedupe/fuse on chunk IDs
- merge adjacent or same-scene hits into larger prompt blocks for generation

That would improve:

- vector matching granularity
- ranking precision
- prompt coherence

without forcing one persisted chunk size to satisfy all objectives at once.

---

## 4. Retrieval-Oriented Chunking Principles

If LoreVault revisits chunking, the redesign should likely be guided by these principles:

1. **Scene remains the primary narrative unit**
   - chunking should not erase or compete with scene structure

2. **Chunk should optimize retrieval, not storage convenience**
   - chunk size should be chosen for embedding fidelity and match precision

3. **Generation context should be assembled, not assumed**
   - final prompt blocks may need to merge multiple adjacent hits

4. **Evidence unit should stay inspectable**
   - retrieval results should remain easy to cite and debug

5. **Context window is a budget, not a target to saturate**
   - the goal is enough context for answer quality, not maximum token stuffing

6. **Chunking should support graph-aware retrieval**
   - entity-scoped and vector-global retrieval branches should rank/fuse on the same evidence unit

---

## 5. Practical Heuristics Worth Testing

These are not yet accepted defaults, only testable heuristics.

### Retrieval unit

Likely better default regime for fiction retrieval:

- roughly **300–700 tokens**
- semantically coherent paragraph cluster / dialogue exchange / event beat
- not an arbitrary giant span just because the scene is under a character threshold

### Generation context unit

Likely better generation behavior:

- merge adjacent hits from the same scene or same local passage
- avoid prompt duplication from overlapping retrieval chunks
- pack evidence by total evidence-token budget, not raw chunk count

### Prompt evidence budget

Likely better answer-generation target:

- pack enough evidence to support reasoning
- do not treat full context window as a target to fill
- preserve room for instructions, question, and answer generation

---

## 6. Relationship to Search Strategy

This topic is tightly connected to the emerging retrieval direction.

If LoreVault moves toward a two-branch search model:

- **global vector branch** over all chunks
- **entity-scoped branch** over graph-derived chunk neighborhoods
- fusion over a shared evidence unit

then chunk shape becomes even more important.

That architecture works best if:

- both branches retrieve the same kind of unit
- that unit is precise enough for vector ranking
- that unit can still be merged upward for answer generation

This is another argument for treating chunking as retrieval design, not just ingestion segmentation.

---

## 7. Open Design Questions

1. Should `Chunk` remain a first-class persisted node, or become a lighter retrieval-span abstraction over scene text?
2. Should every scene always be subdivided into retrieval-oriented chunks, even if it is "short enough" by the current spec?
3. Should chunk boundaries be token-aware rather than character-threshold-based?
4. Should LoreVault define separate concepts for:
   - persisted retrieval chunk
   - fused ranking candidate
   - prompt evidence block?
5. Should citations continue to point to chunk IDs only, or should future answers cite a larger merged evidence block while preserving chunk provenance underneath?
6. How much overlap is actually useful before it becomes prompt duplication noise?
7. What chunk-size regime best supports dialogue-heavy scenes without losing speaker attribution or causal setup?

---

## 8. Evaluation Criteria

Any future refactor should be judged on retrieval and answer quality, not just ingestion elegance.

Important evaluation dimensions:

- retrieval recall for answer-bearing passages
- ranking precision for entity-anchored questions
- answer quality under narrative/causal questions
- prompt coherence (less fragmentation, less duplicate context)
- citation clarity and inspectability
- latency/storage cost impact
- operational complexity of re-chunking and re-embedding

---

## 9. Likely Shape of Future Work

This appears to be **moderate refactor territory**, not a small doc correction.

Potential future work may involve:

- redefining chunk semantics
- revising ingestion-time chunk generation
- revising retrieval/fusion assumptions
- revising context-packing logic for answer generation
- possibly revisiting how embeddings are stored relative to chunk text and scene text

Because of that, this proposal belongs in **brainstorm/planning**, not as a change to current pattern docs.

---

## 10. Current Recommendation

Do **not** overwrite the current chunking pattern doc with this direction yet.

For now:

- keep [Text Chunking Specification](../../patterns/ingestion/text-chunking-specification.md) as present-state truth
- treat retrieval-oriented chunking as future work
- continue refining the design here before promoting any part of it into planning or canonical docs

---

## Links

- `../../patterns/ingestion/text-chunking-specification.md`
- `../../patterns/search/rag-retrieval-chain.md`
- `2026-04-12T1816_graph-aware-qa-design.md`
