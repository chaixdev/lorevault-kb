# Python Question-Understanding Tooling Research For LoreVault

**Date:** 2026-04-16  
**Status:** Brainstorm — feasibility research, not an accepted implementation plan  
**Purpose:** Map the Python NLP / question-understanding tooling space for LoreVault, filter aggressively by integration practicality, and recommend the smallest credible experiments worth running next.

---

## 1. Executive Summary

LoreVault already has the graph-side ingredients needed for entity-aware question understanding, but its current query-time extraction is still shallow. The main integration seam is already present: `QueryEntityExtractor` runs before embedding in `SemanticSearchService`, and its output currently affects search only through exact-string reranking.

The best practicality-first path is:

1. **Prototype in Python outside the JVM first**
2. **Prefer a thin HTTP sidecar over embedded Python**
3. **Start with small, boring libraries** rather than heavyweight research stacks
4. **Use Python to improve candidate extraction and normalization first**, not to redesign the full Q&A runtime

The strongest shortlist for LoreVault is:

- **spaCy** — base noun phrase extraction, dependency parsing, basic NER
- **RapidFuzz** — cheap normalization / alias matching against LoreVault's existing graph names
- **sentence-transformers** — only if semantic query annotation or lightweight reranking experiments are needed
- **scikit-learn** — only if a small intent/template classifier becomes useful
- **FastAPI** — default sidecar framework if the experiment graduates beyond notebooks/scripts

The most realistic first experiment is **not** “find the best NLP stack.” It is:

> compare current LoreVault query extraction against `spaCy + RapidFuzz` on a small question set, then check whether the new candidates improve entity-aware reranking or routing enough to justify a sidecar.

---

## 2. LoreVault Constraints That Matter

### Current query-time integration seam already exists

The current query flow is:

- `AskController` → `RagService` / `SemanticSearchService`
- `SemanticSearchService.search(...)` calls `QueryEntityExtractor.extract(query)` **before** vector search
- `Neo4jSemanticSearch` returns `individualsPresent` and `locationsPresent`
- `SemanticSearchService.rerankByEntityOverlap(...)` applies a small additive rerank boost based on candidate overlap

This matters because a Python experiment does **not** need to redesign retrieval first. It can replace or augment the existing extraction step.

### Existing query-time extraction is real, current, and limited

LoreVault already has:

- `QueryEntityExtractor` — two-stage extraction
  - Strategy A: `KnownEntityTrie` over known names from Neo4j
  - Strategy B: `OpenNlpNounPhraseExtractor`
- `QueryEntityExtractorTest` — confirms the extractor is active in current code, not dead scaffolding

Current limitations:

- OpenNLP NP extraction is **optional** and disables itself if models are missing
- output is used only for **exact-string overlap reranking**
- no fuzzy normalization or alias expansion at query time
- no intent/template routing yet
- no structured extraction payload beyond known names + discovered noun phrases

### LoreVault already has graph-side entity structure worth exploiting

Relevant existing ladders:

- `Scene -> IndividualMention -> ChapterIndividual -> BookIndividual`
- `Scene -> LocationMention -> ChapterLocation -> BookLocation`

That means a Python tool does **not** need to invent entity memory from scratch. The practical job is to help turn a short user question into:

- likely entity spans/candidates
- likely normalized names
- maybe a coarse question shape/intention

### Runtime and dev-workflow constraints push toward sidecars or offline research

Current repo facts:

- Java 21 + Spring Boot + Maven
- only `neo4j` is in `docker-compose.yml` today
- local app startup is script-based: `./scripts/dev-api.sh`
- runtime config comes from `.env` + `application.yml`
- tests are Maven/Testcontainers-first on the Java side

This makes the most natural integration shapes:

1. local Python venv / notebook for research
2. local Python script for offline evaluation
3. optional Python HTTP sidecar in Docker

This repo is **not** currently shaped for embedded JVM/Python coupling.

---

## 3. Candidate Categories

The space worth considering for LoreVault is narrower than “all Python NLP.” The useful categories are:

1. **Noun phrase extraction**
2. **Dependency parsing / grammar analysis**
3. **NER / entity candidate extraction from short questions**
4. **Normalization / fuzzy matching**
5. **Lightweight intent or template detection**
6. **Evaluation tooling and local experiment harnesses**
7. **Sidecar/service frameworks**
8. **Interop / deployment tooling for Java integration**

---

## 4. Library / Tool Inventory

### Preferred evaluation criteria

Score candidates primarily on:

- setup friction
- quality on short question text
- determinism / repeatability
- speed
- operational complexity
- Docker friendliness
- ease of Java interop
- maintainability
- fit for local developer workflow
- fit for eventual productionization

### Inventory table

| Tool / library | Category | Primary capability | Maturity / ecosystem signal | Fast to prototype? | Sidecar-friendly? | Direct integration realistic? | Suggested test method | Suggested integration mode | Recommended next step | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| spaCy | NP / dep / NER | noun chunks, dependencies, POS, basic NER | very mature, widely used | Y | Y | N | local script over sample questions | sandbox → sidecar | compare against current extractor | **Shortlist** |
| Stanza | parsing / NER | dependency + constituency + NER | mature, Stanford-backed | Y | Y | N | targeted parsing comparison | sandbox only first | keep as backup if spaCy misses key syntax | Keep |
| Flair | NER | stronger NER-focused extraction | mature but heavier | Y | Y | N | NER-only spot check | sandbox or sidecar | use only if spaCy NER is insufficient | Keep |
| transformers pipeline | NER / zero-shot | flexible HF model wrappers | huge ecosystem, variable ops cost | Y | Y | N | single-task notebook/script trials | sandbox first | use only when task-specific model value is clear | Keep |
| sentence-transformers | semantic annotation / rerank support | short-text embeddings, semantic similarity | very mature | Y | Y | Possible via ONNX later | compare semantic question clustering / candidate similarity | sandbox → sidecar / possible port later | use after extraction baseline | **Shortlist** |
| RapidFuzz | normalization | fuzzy alias/name matching | mature, tiny, low-friction | Y | Y | N | alias matching against `BookIndividual` / `BookLocation` names | embedded in script or sidecar | pair with spaCy immediately | **Shortlist** |
| textdistance | normalization | alternate similarity metrics | mature but less compelling here | Y | Y | N | only if RapidFuzz underperforms | sandbox only | low priority backup | Keep |
| scikit-learn | intent detection | small TF-IDF + linear classifiers | extremely mature | Y | Y | Possible via ONNX later | 5-10 label intent trial on small question set | sandbox → sidecar / later Java port | use only if routing signal is useful | **Shortlist** |
| seqeval | evaluation | NER span evaluation | standard evaluation tool | Y | N/A | N/A | offline gold-set scoring | research only | add if span-label eval is needed | Keep |
| ragas | evaluation | RAG answer/context evaluation | active ecosystem | Y | N/A | N/A | compare end-to-end answer changes later | offline only | defer until retrieval experiment exists | Keep |
| Jupyter / notebooks | experimentation | fast interactive research | standard workflow | Y | N/A | N/A | notebook-based prompt/question analysis | sandbox only | use for early iteration only | Keep |
| FastAPI | sidecar framework | typed HTTP service, health/docs | excellent ecosystem fit | Y | Y | via HTTP only | minimal `/analyze` prototype | sidecar | default service option | **Shortlist** |
| Flask | sidecar framework | minimal HTTP service | mature, simple | Y | Y | via HTTP only | only if ultra-simple service needed | sidecar | lower priority than FastAPI | Keep |
| gRPC + protobuf | transport | stronger typed RPC | mature but higher friction | N | Y | Y | only if HTTP becomes bottleneck | sidecar | reject for first pass | Reject early |
| ONNX + DJL | runtime bridge | port trained/winning models to Java | credible but more work | N | N/A | Y | only after a Python winner is clear | direct runtime coupling later | long-term fallback, not first step | Keep |
| GraalPy / Jython / Py4J / JPype | JVM/Python embedding | direct embedding attempts | possible but awkward for ML stacks | N | N | Y-ish but fragile | none initially | direct coupling | skip unless forced | Reject early |

---

## 5. Practicality Filter

### Best fit for rapid experimentation

These are the fastest things LoreVault can test without committing to runtime complexity:

- **spaCy** for noun phrases, dependency clues, and basic entity spans
- **RapidFuzz** for mapping extracted strings to existing LoreVault graph names
- **small local scripts or notebooks** for comparison against current behavior

Why this group wins:

- low setup friction
- CPU-friendly
- deterministic enough for repeated testing
- easy to package later if promoted into a service

### Best fit for eventual integration

If experiments show value, these have the cleanest path into LoreVault:

- **FastAPI sidecar**
- **spaCy + RapidFuzz** inside that sidecar
- optionally **sentence-transformers** if semantic annotation helps enough to justify the extra model footprint

### Good for research, not first integration

- **Stanza** — useful if syntax quality becomes the blocker
- **Flair** — useful if NER quality becomes the blocker
- **transformers zero-shot / task pipelines** — useful when a narrow task clearly benefits from a specific model

These should not be first because LoreVault is still at the “is better query extraction even worth it?” stage.

### Tools to reject early

Reject early unless evidence changes:

- **embedded Python in the JVM** (`GraalPy`, `Jython`, `Py4J`, `JPype`)
- **gRPC-first service design**
- **heavy model-serving complexity before baseline experiments exist**

Reason: the likely first wins are in extraction and normalization quality, not transport sophistication.

---

## 6. Recommended Shortlist

### 6.1 spaCy + RapidFuzz

**Why it belongs on the shortlist**

- directly addresses noun phrase extraction and candidate discovery
- works well on short questions
- easy to compare against current `OpenNlpNounPhraseExtractor`
- can normalize candidates against existing `BookIndividual` / `BookLocation` names with low additional complexity

**Most realistic integration mode:**

- **research sandbox first**
- then **sidecar service** if the quality jump is real

**Most likely LoreVault use**

- replace or augment Strategy B in `QueryEntityExtractor`
- add normalized candidate expansion before `rerankByEntityOverlap(...)`

### 6.2 sentence-transformers

**Why it belongs on the shortlist**

- useful if LoreVault wants semantic query annotation or lightweight query/entity similarity beyond exact text matching
- could help with fuzzy matching of extracted phrases to canonical graph names or intent examples

**Why it is not the first experiment**

- it does not give spans by itself
- it introduces more runtime and packaging cost than spaCy/RapidFuzz

**Most realistic integration mode:**

- **sandbox only at first**
- later **sidecar** if semantic matching clearly beats simpler fuzzy methods
- possible long-term **Java port via ONNX/DJL** only after behavior stabilizes

### 6.3 scikit-learn intent classifier

**Why it belongs on the shortlist**

- if LoreVault wants cheap routing such as:
  - entity lookup
  - location lookup
  - multi-entity scene question
  - causal/explanatory question
  - summary/open-ended question
- small linear models are cheap, deterministic, and easy to maintain

**Most realistic integration mode:**

- **research sandbox**
- maybe **sidecar** later
- possible direct Java runtime only after the label set and behavior are stable

### 6.4 FastAPI

**Why it belongs on the shortlist**

- best low-friction sidecar shape for this repo
- clean startup model loading, health endpoints, and typed contracts
- easy HTTP integration from Spring Boot

**Most realistic integration mode:**

- **sidecar only**

---

## 7. Testing Strategy Per Shortlisted Option

### 7.1 spaCy + RapidFuzz

**Fastest local test**

- Python venv in a temporary research folder
- read a small CSV/JSON file of sample LoreVault questions
- output extracted noun phrases, dependencies, entity spans, and top canonical name matches

**Minimum sample dataset**

- 20-40 questions
- include at least these buckets:
  - single-entity lookup (`Who is Sazed?`)
  - entity + location (`What happened in Luthadel before the attack?`)
  - multi-entity (`Where does Kelsier meet Vin?`)
  - relation/event (`What was the argument between X and Y about?`)
  - misspelling / alias cases

**Expected output shape**

```json
{
  "question": "Where does Kelsier meet Vin?",
  "noun_phrases": ["Kelsier", "Vin"],
  "entities": [{"text": "Kelsier", "label": "PERSON"}, {"text": "Vin", "label": "PERSON"}],
  "normalized_candidates": [
    {"input": "Kelsier", "match": "kelsier", "score": 100},
    {"input": "Vin", "match": "vin", "score": 100}
  ]
}
```

**How to compare against current LoreVault behavior**

- run the same questions through current `QueryEntityExtractor`
- compare:
  - missed entity candidates
  - false positives
  - quality of normalized matches
  - whether the result would improve `rerankByEntityOverlap(...)`

**Good enough to continue**

- clear improvement on multi-token names / locations / aliases
- fewer misses than current OpenNLP path
- no major operational pain

### 7.2 sentence-transformers

**Fastest local test**

- encode extracted phrases and canonical graph names
- compare nearest-neighbor matching against RapidFuzz-only matching

**Minimum sample dataset**

- 20-40 questions
- plus a canonical-name list exported from LoreVault for one or two books

**Expected output shape**

```json
{
  "candidate": "the pits",
  "matches": [
    {"name": "pits of hathsin", "score": 0.84},
    {"name": "hathsin", "score": 0.72}
  ]
}
```

**How to compare against current LoreVault behavior**

- compare semantic-match quality against exact match and RapidFuzz
- measure whether it rescues cases that fuzzy string matching misses

**Good enough to continue**

- catches alias/paraphrase cases that simpler methods miss often enough to matter

### 7.3 scikit-learn intent detection

**Fastest local test**

- manually label a tiny set of questions with 5-8 intent/template classes
- train a TF-IDF + logistic regression baseline

**Minimum sample dataset**

- ideally 100+ questions eventually
- but 30-50 may be enough to test whether the signal is even separable

**Expected output shape**

```json
{
  "question": "Who is Sazed?",
  "intent": "entity_lookup",
  "confidence": 0.89
}
```

**How to compare against current LoreVault behavior**

- current system has no real intent layer
- compare whether intent would help choose different retrieval/rerank strategies

**Good enough to continue**

- routing becomes visibly easier to reason about for a handful of high-value question classes

### 7.4 FastAPI sidecar

**Fastest local test**

- expose one endpoint: `POST /v1/analyze-question`
- back it with whatever extraction pipeline won offline testing

**Minimum sample dataset**

- the same question set used offline
- plus a couple of end-to-end calls from a tiny Java client or curl

**Expected output shape**

```json
{
  "known_entities": ["kelsier", "vin"],
  "discovered_phrases": ["meet"],
  "normalized_locations": [],
  "intent": "multi_entity_scene_question"
}
```

**How to compare against current LoreVault behavior**

- substitute or mimic `QueryEntityExtractor` output
- confirm the payload can cleanly fit the current Java seam

**Good enough to continue**

- contract is simple
- startup is tolerable in local dev
- no awkward credential/config problems emerge

---

## 8. Integration Options Per Shortlisted Option

| Option | Research sandbox only | Offline / batch bridge | Sidecar service | Direct runtime coupling | Most realistic |
|---|---|---|---|---|---|
| spaCy + RapidFuzz | Y | Y | Y | N | **Sandbox first, then sidecar if useful** |
| sentence-transformers | Y | Y | Y | Possible later via ONNX/DJL | **Sandbox first** |
| scikit-learn | Y | Y | Y | Possible later via ONNX | **Sandbox first** |
| FastAPI | N | N | Y | N | **Sidecar only** |

### Recommended sidecar contract shape

If LoreVault reaches the sidecar stage, keep the contract narrow:

- `POST /v1/analyze-question`
- request: raw question + optional scope metadata
- response:
  - extracted candidates
  - normalized matches
  - optional intent label
  - optional confidence scores

### Best Java integration seam

The cleanest first Java integration is one of these:

1. **Replace Strategy B only**
   - keep `KnownEntityTrie`
   - replace `OpenNlpNounPhraseExtractor` behavior with a Python-backed adapter

2. **Replace `QueryEntityExtractor` with a delegating adapter**
   - Python returns a richer `ExtractionResult`-like payload
   - Java still controls search and reranking

Option 2 is more future-proof if normalization or intent routing becomes important.

---

## 9. Suggested First Experiments

### Experiment 1 — Extraction baseline replacement

**Goal:** determine whether `spaCy + RapidFuzz` produces better query candidates than the current Java extractor.

**Steps:**

1. export a small canonical name list for one book/universe from LoreVault
2. assemble 20-40 representative questions
3. run current LoreVault extractor and Python prototype side-by-side
4. compare misses, false positives, and normalized matches

**If it wins:** move to Experiment 2.

### Experiment 2 — Rerank usefulness simulation

**Goal:** determine whether better extracted/normalized candidates would materially improve current reranking.

**Steps:**

1. take existing search results with `individualsPresent` / `locationsPresent`
2. simulate current exact-match reranking
3. simulate reranking with Python-normalized aliases/candidates
4. inspect whether top results improve for entity-heavy questions

### Experiment 3 — Tiny intent/template probe

**Goal:** determine whether a cheap classifier helps route questions into better retrieval shapes.

Suggested labels:

- `entity_lookup`
- `location_lookup`
- `multi_entity_scene`
- `causal_explanatory`
- `open_summary`

If the label signal is weak or noisy, drop this path early.

### Experiment 4 — Thin sidecar spike

Only do this if Experiments 1-2 show value.

**Goal:** prove that LoreVault can call a local Python analyzer cleanly.

**Scope:**

- one FastAPI endpoint
- one Java client
- one local env variable such as `NLP_SIDECAR_URL`
- simple `/health` endpoint

---

## 10. Tools We Should Probably Skip Early

### Skip: direct JVM/Python embedding

Examples:

- GraalPy
- Jython
- Py4J
- JPype

Why:

- poor fit for the current repo shape
- awkward packaging and operational story
- unnecessary complexity while the behavior is still experimental

### Skip: gRPC-first architecture

Why:

- HTTP overhead is not the likely bottleneck here
- complexity arrives before value
- FastAPI + JSON is enough for an initial sidecar

### Skip: heavyweight model serving before a baseline exists

Examples:

- transformer-heavy NER serving
- multi-model orchestration
- production-grade inference stacks

Why:

- LoreVault has not yet proven that better query extraction changes answer quality enough to justify it

---

## Recommendation

If LoreVault wants a practical next move, it should **not** begin by building a Python service.

It should begin by answering this smaller question:

> Does `spaCy + RapidFuzz` materially outperform the current `QueryEntityExtractor` + optional OpenNLP path on LoreVault-style short questions?

If the answer is **no**, stop.

If the answer is **yes**, the next move is a **thin FastAPI sidecar** or even a temporary offline bridge — not embedded Python, not gRPC, and not a large model-serving platform.

That path matches LoreVault's current Java/Spring/Neo4j architecture, existing entity ladders, local developer workflow, and current maturity level.
