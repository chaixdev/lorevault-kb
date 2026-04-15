# Robust Q&A Strategy Report — LoreVault

**Date:** April 2026  
**Status:** Brainstorm — strategic analysis, not an accepted decision  
**Purpose:** Preserve the long-horizon analysis of what LoreVault must become to support robust lore Q&A, how the current concept model fits that ambition, where the bottlenecks are, and how the next product-value moves should be chosen.

---

## 1. Framing

The goal is **not** to optimize a knowledge graph for its own sake.

The goal is **robust Q&A** for narrative/lore-heavy reading experiences:

- grounded in text
- bounded by spoiler-safe reader progress
- able to handle ambiguity and partial knowledge
- able to answer factual, relational, temporal, causal, comparative, and interpretive questions
- able to evolve beyond today's narrow retrieval path without losing architectural coherence

That means the graph is a **tool to deploy and shape**, not the product target.

The strongest framing that emerged from this analysis is:

- **evidence layer** → durable semantic foundation
- **interpretation layer** → retrieval-and-reasoning layer that metabolizes that foundation into usable evidence
- **Q&A runtime** → user-visible question-answering behavior and answer assembly

The central strategic question is therefore:

> Are LoreVault's Event DAG and Entity-Claim concepts the right evidence layer for maximalist robust Q&A, and what must be built above them to make that ambition real?

Short answer:

- **Yes** — they are the right evidence layer.
- **No** — they are not the whole answer system.
- The next bottlenecks are mostly in the **interpretation layer**, not in adding graph complexity indiscriminately.

---

## 2. Target-State Ambition

LoreVault should eventually support a broad spectrum of lore questions, not just one or two retrieval patterns.

### The target question space

#### A. Identity and continuity

- Who is X so far?
- Was X always called Y?
- Did X start as Mantear and later become Damodred?
- What is known about X at this point in the reading order?

#### B. Scene and event reconstruction

- What happened when X met Y?
- What was the argument between X and Y about while they were at Z?
- Who was present, where, and in what sequence?

#### C. Social and structural relations

- How is X related to Y?
- Which factions are aligned with Z?
- What institutional or kinship structure explains this connection?

#### D. Temporal development

- What changed before or after event Z?
- When did X first/last do Y?
- How did a relationship evolve over time?

#### E. Causal and explanatory

- Why did this betrayal happen?
- What led to this political crisis?
- What caused a state change in an individual, group, or institution?

#### F. Comparative

- How does X in chapter 3 compare to X in chapter 9?
- How do city A and city B differ as depicted so far?
- How does a concept or institution vary across books?

#### G. World-model / institutional / thematic

- How does succession work in Andor?
- What are the rules of this order, group, or system?
- What themes surround exile, prophecy, inheritance, or duty?

#### H. Uncertainty / interpretation / epistemics

- Do we know if…?
- Is this explicitly stated, or only implied?
- Who believed this at the time?
- Was this actually true yet, or merely suspected?

This target-state question space is the real design horizon. Retrieval architecture should serve it.

---

## 3. Strategic Reading Of The Existing Concept Model

### 3.1 Event DAG

The Event DAG concept is strong because it explicitly commits to:

- local evidence first
- triads over global ordering
- partial order rather than total order
- sparse temporal structure rather than dense closure
- confidence and evidence traveling with edges
- retrieval-time temporal composition rather than eager global inference

This is exactly the right stance for fiction.

Narrative chronology is rarely a clean line. It contains:

- flashbacks
- intercuts
- implied simultaneity
- delayed reveals
- multiple scales of time

Treating temporal structure as a sparse, evidence-backed DAG is therefore strategically correct.

### 3.2 Entity-Claim model

The Entity-Claim concept is strong because it explicitly commits to:

- claim-first modeling rather than fact-first flattening
- provenance-bearing assertions
- spoiler-safe publication coordinates on artifacts
- offline aggregation and confidence/status projection
- retrieval menus and constrained vocab rather than giant ontology prompts
- read-optimized projections that do not destroy the evidence floor

This is also exactly the right stance for fiction/lore QA.

Lore questions are full of:

- rumor
- disagreement
- contradiction
- interpretation
- changing titles/roles/names
- unreliable narrators
- public belief diverging from objective truth

Any architecture that jumps directly to one canonical fact graph loses too much.

### 3.3 Combined reading

Taken together, the Event DAG and Entity-Claim models are best understood as:

- the **temporal evidence layer**
- the **assertional evidence layer**

They are the right foundational materials for robust Q&A because they preserve:

- evidence
- ambiguity
- chronology
- provenance
- spoiler-safe scope

That is already a major strategic win.

---

## 4. Evidence Layer, Interpretation Layer, Q&A Runtime

The best systems metaphor that emerged from this analysis is:

### Evidence layer

The durable semantic material the system grows from.

In LoreVault, this is:

- publication hierarchy and spoiler lattice
- scene/chapter/book structure
- Event DAG
- Entity-Claim model
- scoped identity/location ladders
- chunk/scene evidence floor

### Interpretation layer

The living retrieval-and-reasoning layer that metabolizes the evidence layer into answerable forms.

In LoreVault, this includes:

- question understanding and classification
- scope and spoiler analysis
- entity/time/uncertainty cue extraction
- retrieval planning and route selection
- compact semantic read models and projections
- evidence expansion, reranking, and aggregation
- answer planning and uncertainty framing

### Q&A runtime

The user-visible question-answering behavior.

In LoreVault, this means:

- accurate answers
- meaningful citations
- graceful abstention
- explanation of uncertainty
- useful synthesis at the right narrative scope

### Strategic implication

The evidence layer does not answer questions by itself.

The graph only becomes Q&A-capable once the interpretation layer can:

1. understand the question
2. choose the right retrieval/evidence strategy
3. assemble the right answer-bearing evidence
4. present it in a grounded way

This is why “more graph” is not automatically the right next move.

The next bottleneck may instead be:

- better projections
- better routing
- better epistemic modeling
- better event-role modeling
- better evidence scoring

---

## 5. Why A Generic GraphRAG Taxonomy Is Not Enough

Industry GraphRAG commonly talks about:

- local search
- global search
- relational / multi-hop
- comparative retrieval

That vocabulary is useful as a translation layer, but it is not a good primary design model for LoreVault.

### Why it is insufficient

#### “Local” is too coarse

It blurs together:

- scene-local factual retrieval
- entity profile lookup
- scene/event reconstruction
- chapter-bounded relation questions

These fail differently and need different retrieval shapes.

#### “Global” assumes artifacts LoreVault does not yet maintain

Corpus/community-level summaries are legitimate later, but they are not currently the right architectural center.

#### Narrative QA is defined by answer shape and scope

For LoreVault, the better first question is:

> What kind of evidence shape does this answer require?

Not:

> Is this local or global in a generic chatbot sense?

### Better framing

LoreVault should route primarily by:

1. **answer shape**
2. **narrative scope**
3. **spoiler horizon**
4. **whether the question is factual, relational, temporal, comparative, causal, or interpretive**

Industry GraphRAG categories remain helpful as supporting vocabulary, not the governing abstraction.

---

## 6. Recommended Question Taxonomy For LoreVault

The strongest synthesis from Oracle, narrative-QA research, the existing concept model, and query brainstorming is:

### 6.1 Scoped factual lookup

Examples:

- Who is X so far?
- What do we know about location Y at this point?
- Did X always have that surname?

Evidence shape:

- bounded entity/state evidence
- spoiler-safe textual support

### 6.2 Scene / event reconstruction

Examples:

- What happened when X met Y?
- What was the argument about while they were at Z?
- How did chapter 6 end?

Evidence shape:

- candidate scenes/events
- supporting chunks
- local sequence and context

### 6.3 Relationship queries

Examples:

- How is X related to Y?
- Who traveled with X?
- Which faction is aligned with Z?

Evidence shape:

- graph structure plus supporting chunk evidence

### 6.4 Temporal development

Examples:

- What changed before/after event Z?
- When did X first do Y?
- How did the relationship evolve?

Evidence shape:

- event/timeline traversal
- state snapshots over time

### 6.5 Causal / explanatory

Examples:

- Why did this happen?
- What led to the split between A and B?

Evidence shape:

- event chain
- state-change evidence
- causal support from text

### 6.6 Bounded comparison

Examples:

- Compare X in book 1 and book 3
- How does city A differ from city B so far?

Evidence shape:

- two scoped retrievals
- explicit comparison synthesis

### 6.7 Thematic / world-model / institutional

Examples:

- How does succession work in Andor?
- What are the rules of this order/system?

Evidence shape:

- broader synthesis artifacts
- cross-passage / cross-chapter support

### 6.8 Uncertainty / interpretation / epistemic questions

Examples:

- Do we know if…?
- Was this true yet, or only suspected?
- Who believed this at that point?

Evidence shape:

- claims with provenance
- viewpoint-sensitive or uncertainty-sensitive synthesis

This is a better long-term benchmark space than overfitting to any single example question family.

---

## 7. What The Current Concepts Already Solve

### 7.1 They solve the evidence problem

The concepts preserve the raw material needed for explainable Q&A.

### 7.2 They solve the spoiler problem

Publication coordinates and scoped ladders give a strong answerability lattice.

### 7.3 They solve the ambiguity problem better than naive canonical graphs

Claim-first modeling avoids premature flattening.

### 7.4 They solve the temporal-shape problem

The Event DAG correctly assumes sparse partial order.

### 7.5 They solve the “graph blow-up” problem strategically

Lazy inference and sparse structure keep the system tractable.

These are not small wins. They are the right foundations.

---

## 8. What They Do Not Yet Solve

This is the decisive section.

The concepts are **foundational**, but they are not yet the full answer-serving architecture.

### 8.1 Missing answer-oriented semantic projections

Claims and sparse temporal edges are not themselves a user-facing answer model.

What is missing are compact, query-serving views such as:

- entity so far
- event so far
- state at publication boundary
- relationship at time T
- what is explicit vs implied vs denied vs believed

This is arguably the single biggest missing middle layer.

### 8.2 Missing epistemic/viewpoint model

The concepts preserve source and certainty, but robust lore QA eventually needs richer epistemic semantics:

- who knows
- who believes
- who says
- what is public knowledge
- what is rumor
- what is only inferred

This is critical for:

- suspicion questions
- deception questions
- prophecy questions
- “do we know if…?” questions

### 8.3 Missing event-role/state-change layer

The Event DAG is strong on ordering but weak on event semantics such as:

- who participated in what role
- what changed because of the event
- what causal relation exists between events
- what state transition the event induced

For robust Q&A, events need to become answer-serving structures, not just ordered anchors.

### 8.4 Scene-as-event eventually hits a ceiling

Scene-as-event is a good skeleton.

But robust lore QA eventually needs event identity that can:

- recur across scenes
- be referred to retrospectively
- span chapters/books
- accumulate later evidence or reinterpretation

That implies a future event layer richer than scene-local anchoring alone.

### 8.5 Missing query-serving retrieval architecture above the evidence layer

Even with a strong evidence layer, robust Q&A still needs:

- question classification
- route selection
- retrieval planning
- evidence fusion
- reranking
- uncertainty-aware synthesis

This is the interpretation layer.

---

## 9. Strategic Bottlenecks

These are the bottlenecks implied by the concept model relative to the robust-Q&A ambition.

### Bottleneck 1 — query-serving read models

Need compact semantic projections above raw claims and temporal edges.

### Bottleneck 2 — epistemic modeling

Need explicit support for belief, knowledge, rumor, suspicion, public knowledge, and contested interpretation.

### Bottleneck 3 — event-role and state-change semantics

Need events to carry answer-relevant semantics, not just order.

### Bottleneck 4 — cross-scene / cross-book event identity

Need durable event identities for recurring happenings and retrospective linkage.

### Bottleneck 5 — retrieval architecture and routing

Need question-aware planning above the graph.

### Bottleneck 6 — summary/synthesis artifacts for broader questions

Need chapter/book/arc/world-model summaries or equivalent derived structures for thematic and institutional questions.

### Bottleneck 7 — evaluation discipline

Need benchmark sets across question classes so LoreVault does not overfit to one flashy query shape.

---

## 10. What Not To Do

### Do not treat the current implementation as the product ceiling

Current code should shape sequencing, not architecture ambition.

### Do not turn the graph into a giant omniscient fact store

That would undermine ambiguity, provenance, and spoiler discipline.

### Do not rush to dense inferred closure

Retrieval-time reasoning remains the right default unless proven otherwise.

### Do not confuse conceptual richness with answer readiness

Claims + Event DAG are a strong evidence layer, but not the whole reasoning layer.

### Do not overbuild governance machinery too early

Heavy catalog microservices, elaborate numeric confidence formulas, and broad ontology infrastructure should be justified by demonstrated bottlenecks, not aesthetic completeness.

---

## 11. Strategic Sequencing

The right sequencing principle is:

> pursue maximalist ambition at the target-state level, but resolve the **next bottleneck** in a way that also delivers product value.

That means choosing the next move by asking:

1. Which question classes matter most to product value now?
2. Which bottleneck most blocks those classes?
3. Which intervention strengthens the long-term evidence layer or interpretation layer without overcommitting to premature infrastructure?

### Good next-move candidates

#### Option A — question-serving projections

Build compact “so far” views for entities, events, and relationships.

Why it helps:

- improves factual lookup
- improves relationship answers
- supports spoiler-aware state summaries

#### Option B — event-role/state-change modeling

Enrich events with participants, roles, outcomes, and changed state.

Why it helps:

- improves scene/event reconstruction
- improves causal questions
- improves temporal reasoning

#### Option C — epistemic/viewpoint layer

Model belief/knowledge/claim ownership more explicitly.

Why it helps:

- improves interpretation and uncertainty questions
- differentiates what is true from what is believed

#### Option D — routing/evidence assembly layer

Define question taxonomy, retrieval lanes, and evidence contracts.

Why it helps:

- turns the existing evidence layer into an operational Q&A system
- de-risks overfitting on one route

These are better strategic bottleneck moves than “add more graph” in the abstract.

---

## 12. Recommended Strategic Stance

### 12.1 What to preserve as durable principles

- evidence-first modeling
- spoiler-safe publication hierarchy
- sparse temporal structure
- scoped identity/location ladders
- retrieval-time reasoning over dense closure
- graph as retrieval/reasoning support, not direct answer surface

### 12.2 What to add above the evidence layer

- query-serving semantic projections
- richer event semantics
- epistemic/viewpoint modeling
- retrieval planning and routing
- evaluation benchmark suites across question classes

### 12.3 What to defer unless justified

- dense global graph algorithms
- huge ontology/governance machinery
- free-form graph generation/query generation as the primary path
- broad summary/community infrastructure before narrower, high-value bottlenecks are solved

---

## 13. The Cleanest Overall Conclusion

LoreVault's Event DAG and Entity-Claim concepts are **architecturally aligned** with a maximalist robust-Q&A ambition.

They are the right **evidence layer** because they preserve:

- evidence
- ambiguity
- chronology
- provenance
- spoiler-safe scope

But they are not, by themselves, the full **Q&A runtime**.

The next strategic bottlenecks are mostly in the **interpretation layer**:

- query-serving projections
- routing
- event-role/state-change semantics
- epistemic modeling
- answer-oriented evidence assembly

So the key strategic posture should be:

> keep the evidence-layer principles; shape the graph aggressively where it improves robust QA; and prioritize the next bottleneck that unlocks immediate product value while strengthening the long-term reasoning stack.

---

## 14. Open Strategy Questions To Revisit Later

- When should event identity rise above scene-as-event?
- When should claims gain a richer epistemic vocabulary?
- When should book-level and cross-book summary artifacts be introduced?
- What benchmark suite best captures the real LoreVault question space?
- Which question classes deserve first-class routing lanes versus composition from more general lanes?
- When does the system need a world-model layer above book-scoped entity/event structures?

---

## 15. Evolved View: The Interpretation Layer As A Separate System

Later discussion sharpened the architecture boundary further.

The evidence layer and interpretation layer should be treated as **separate logical systems**, even if they may share storage or infrastructure later.

### Hard ownership rule

If deleting and rebuilding something from the Event DAG and Entity-Claim evidence layer would lose canon meaning, it belongs in the **evidence layer**.

If deleting and rebuilding it would only lose answer acceleration, retrieval convenience, or serving structure, it belongs in the **interpretation layer**.

This creates a clean separation:

### Evidence layer owns
- canon-bearing meaning
- provenance
- spoiler boundaries
- causal and temporal truth claims
- durable entity/event identity

### Interpretation layer owns
- derived answerability structures
- query-serving projections
- helper networks and read models
- evidence neighborhoods
- routing and retrieval plans
- answer assembly ingredients

This is the strongest single boundary discovered in the analysis and should remain stable even if implementation choices change.

---

## 16. The Interpretation Layer Should Be Hybrid, Not “All Graph”

One of the most important refinements from the later discussion is that the interpretation layer should not be imagined as a second giant graph that mirrors the evidence layer.

It should be a **hybrid answerability layer**.

### Graph-shaped where traversal is the capability

Good fits:

- causal neighborhoods
- entity trajectories
- event consequence chains
- relationship-evolution neighborhoods
- reveal paths and local narrative neighborhoods

### View-shaped or index-shaped where lookup is the capability

Good fits:

- boundary snapshots
- entity or relationship “state so far” views
- citation indexes
- visibility-bounded retrieval views
- conflict/uncertainty sets

### On-demand synthesis where open composition is the capability

Good fits:

- final answer prose
- long-form explanations
- broad comparisons
- thematic synthesis

The interpretation layer therefore is best thought of as a **capability-serving network of projections, read models, and helper structures**, not as “more graph.”

---

## 17. Claimless First-Wave Interpretation Layer

Another important refinement is sequencing.

Even though claims remain a strong long-term evidence-layer primitive, the first wave of interpretation-layer work does **not** need to depend on full claim modeling.

This is a deliberate strategic choice.

### Why claimless first is attractive

- claim modeling is expensive and opinionated
- it introduces governance, confidence, and ontology surface area early
- it may not be necessary if graph-enhanced retrieval and better evidence assembly already improve answer quality enough
- it is better introduced when retrieval quality clearly plateaus on uncertainty/contradiction-heavy question classes

So the first-wave question becomes:

> Can LoreVault get substantially better answers from better retrieval, graph exploration, reranking, snapshots, and trajectories before it needs claims?

That is an excellent bottleneck-resolving hypothesis.

### What a claimless interpretation layer can still do

- assemble better evidence bundles
- rerank chunks with graph/context features
- materialize state-at-boundary views
- provide trajectory views over scenes/events/entities
- expose query plans and retrieval contracts

All of that can happen without introducing full claim-resolution or claim-serving infrastructure.

Claims remain highly relevant later for:

- contradiction-heavy questions
- epistemic questions
- explicit vs implied questions
- truth vs belief separation

But they do not need to be the first step in answer-serving architecture.

---

## 18. First Candidate Interpretation-Layer Capabilities (Claimless)

The first set of concrete interpretation-layer capabilities should be understood as reusable answer-serving structures or services above the evidence layer.

### 18.1 Evidence neighborhoods

These are bounded evidence bundles around a chunk, scene, entity, or event anchor.

Typical contents might include:

- a focal chunk or scene
- local scene summary
- entities present
- locations present
- nearby chunks
- previous/next scenes
- relevant temporal or chapter context

Purpose:

- improve scene/event reconstruction
- improve entity-and-location anchored questions
- improve answer planning without requiring a huge ontology leap

### 18.2 Graph-enhanced reranking features

This is the scoring layer that uses graph/context signals to improve chunk ranking.

Examples:

- target entities present in the same scene
- target location present
- repeated support across nearby chunks/scenes
- temporal closeness to already-relevant scenes
- chapter/book boundary fit
- diversity penalties

Purpose:

- improve precision while preserving vector recall
- let graph structure surface more useful chunks to score

### 18.3 Query plans / answer contracts

This is a structured intermediate representation between question understanding and retrieval execution.

It can include:

- question class
- scope and spoiler boundary
- extracted anchors (entity, location, temporal cues)
- chosen retrieval lanes
- scoring features to apply
- evidence budget

Purpose:

- make routing and retrieval explainable
- make failures diagnosable
- avoid ad hoc query logic

### 18.4 Boundary snapshots

These are “state so far” read models at chapter/book/arc boundaries.

Examples:

- entity state at end of chapter N
- relationship salience at end of chapter N
- visible aliases/titles at boundary N
- recent associated locations and scenes

Purpose:

- support spoiler-safe factual lookup
- reduce repeated recomputation
- serve “what do we know so far?” questions directly

### 18.5 Trajectory views

These are ordered views of how entities, relationships, or narrative threads evolve across time.

Examples:

- character trajectory
- relationship trajectory
- location trajectory
- event thread around a participant

Purpose:

- support temporal development questions
- support “how did X change?” and “when did Y first happen?”

### Recommended early order

The strongest early sequence is:

1. evidence neighborhoods
2. graph-enhanced reranking
3. query plans / answer contracts
4. boundary snapshots
5. trajectory views

That ordering emphasizes immediate product value before heavier materialization work.

---

## 19. Packets As A Serious Interpretation-Layer Design Option

The later discussion also surfaced a more concrete possibility for the interpretation layer: **packet families**.

### The key insight

Some ingestion byproducts may already be usable as the seed of answer-serving packets.

For example, an LLM scene analysis response may already contain enough structure to seed a retrieval-serving artifact that combines:

- scene summary
- entity context
- location context
- temporal hints

The question is not whether such packets are canonical truth.

The question is whether they are useful, rebuildable, bounded **serving artifacts**.

### Packet family architecture

The interpretation layer may therefore develop not just as helper edges and dynamic assembly, but as a family of derived serving packets.

Potential packet families include:

- **scene packets**
- **chunk packets**
- **entity packets**
- **relationship packets**
- **event packets**
- **chapter packets**
- **trajectory packets**

### Scene packets are the strongest first candidate

Scene packets can plausibly be derived from:

- scene analysis summary
- scene-linked entities and locations
- chunk snippets or representative chunk context
- previous/next scene references

They could then be embedded or retrieved as serving artifacts.

This is a meaningful architectural option because many questions are not best answered by a naked chunk vector alone. They are better answered by a bounded, scene-shaped retrieval object.

### Important caution

The packet should not simply be “store the entire raw scene analysis response.”

The valuable version is a **serving-shaped packet**:

- compact enough to embed or rank
- stable enough to use repeatedly
- backed by underlying chunks/scenes for final citation
- rebuildable from the evidence layer and local graph context

So a useful first research question for packet-based interpretation-layer design is:

> Is the existing scene analysis output already a good seed for a Q&A-serving scene packet, or does it need a narrower serving-specific transformation?

### Why packets matter strategically

Packets offer a possible first-wave interpretation layer that is more concrete than generic “helper graph” language.

They may let LoreVault test whether richer retrieval artifacts improve answer quality before investing deeply in claim-first answer-serving structures.

---

## 20. Packet Families And Likely Derivation Sources

To make the packet direction more concrete, it helps to state what each packet family would likely be derived from.

This is useful because it reinforces the rule that packets are **serving artifacts**, not evidence-layer truth.

### Scene packets

Likely derived from:

- LLM scene analysis summary / context
- scene-linked Individuals and Locations
- chunk snippets or representative chunk text
- previous/next scene references
- chapter/book boundary metadata
- local temporal cues

### Chunk packets

Likely derived from:

- raw chunk text
- parent scene summary
- scene-linked Individuals and Locations
- neighboring chunk references
- publication boundary metadata
- local graph-derived reranking hints

### Entity packets

Likely derived from:

- scoped entity ladders (`IndividualMention -> ChapterIndividual -> BookIndividual` and Location equivalents)
- aliases observed so far
- representative scenes and chunks
- recent/important co-occurring entities and locations
- boundary snapshots
- trajectory summaries if available

### Relationship packets

Likely derived from:

- co-occurrence patterns across scenes
- temporal neighborhood of shared scenes/events
- representative chunks where the relationship is visible
- relationship trajectory summaries
- nearby entity and location context

### Event packets

Likely derived from:

- scene-as-event summaries
- event participants and participant roles where available
- local temporal neighborhood
- linked locations
- causal or consequence hints where available
- representative evidence chunks/scenes

### Chapter packets

Likely derived from:

- chapter-level scene summaries
- salient Individuals and Locations across the chapter
- chapter-scoped trajectories and major event threads
- representative scenes/chunks
- chapter boundary metadata and spoiler-safe visibility scope

### Trajectory packets

Likely derived from:

- ordered scene/event references
- entity or relationship appearances over time
- boundary snapshots
- turning-point scenes or key neighboring events
- representative chunk evidence for each phase of the trajectory

### General rule

The packet should be derived from:

- evidence-layer truth and evidence
- local graph context
- existing summaries or retrieval-ready abstractions

But the packet should **not** become a second canonical semantic source.

It is a serving object whose job is to improve retrieval, ranking, and answer planning.

---

## 21. Revised View Of Immediate Bottlenecks

The later discussion sharpens the near-term bottleneck order.

### The first question is no longer only:

> What semantic modeling layer should come next?

### It is also:

> What answer-serving structures can improve retrieval and answer quality now, without prematurely committing to full claim modeling?

That reframes the next frontier as:

- better serving packets or evidence bundles
- better reranking
- better retrieval plans
- better state-at-boundary views

The more ambitious semantic layers — especially claims as a Q&A-serving evidence-layer primitive — should arrive when simpler serving structures stop delivering enough quality.

---

## Related Materials

- `../query/graph-aware-qa-design-april-2026.md`
- `../query/multi-entity-retrieval-external-research-april-2026.md`
- `../../concepts/event-dag.md`
- `../../concepts/Entity-Event-Claim-model.md`
- `../../concepts/entity-claim-model.md`
- `../entity-modeling/concept-model-critique-april-2026.md`
- `../../patterns/rag-retrieval-chain.md`
- `../../patterns/triad-analysis.md`
