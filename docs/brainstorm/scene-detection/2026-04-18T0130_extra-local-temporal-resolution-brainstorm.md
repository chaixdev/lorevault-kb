# Extra-Local Temporal Resolution — Brainstorm (April 2026)

**Date:** April 2026  
**Status:** Exploratory brainstorm — not canonical truth

---

## 1. Purpose

This document captures a broader design discussion that emerged from scene temporal linking work.

The narrow V1 local-linking problem is already understood: triad analysis can infer temporal relations between nearby scenes, including chapter-boundary neighbors.

The broader problem is different:

- how LoreVault should resolve temporal relationships beyond local reading-order neighbors
- how scenes can participate in a larger temporal DAG without pretending that local scene-to-scene judgments alone establish a full global timeline
- whether meaningful extra-local temporal resolution requires canonical **Event** extraction rather than relying only on scenes as temporal anchors

This document does **not** propose an implementation-ready slice. It frames the option space and the likely architectural implications.

---

## 2. Why This Matters

The current local temporal-linking direction is useful, but it only gives a sparse backbone of nearby constraints.

That is enough to support:

- local chronology checks
- chapter-boundary continuity
- evidence-backed scene-to-scene temporal judgments

It is **not** enough to support broader questions such as:

- where a flashback scene belongs relative to scenes several chapters away
- whether a recalled event predates a later onstage scene in another chapter
- how scenes across books align around a named battle, coronation, voyage, or investigation
- how to answer longer-horizon temporal queries without falling back to reading order

If LoreVault wants an Event DAG that is more than a chain of local scene judgments, it needs a principled way to place scenes relative to **extra-local anchors**.

---

## 3. Core Diagnosis

### 3.1 Local scene analysis gives constraints, not positions

Sequential scene analysis does **not** assign each scene a global slot in a timeline.

It produces only local temporal constraints such as:

- scene A `MEETS` scene B
- scene B `OVERLAPS` scene C
- scene C is `BEFORE` scene D

Those are useful, but they do not directly tell us where scene A belongs relative to some distant scene Z.

In other words:

- local analysis yields a **partial-order fragment**
- it does **not** by itself yield a broadly navigable temporal placement model

### 3.2 A DAG is not the same thing as a sortable global order

Saying the graph is a DAG does not mean every scene can be assigned one stable total-order position.

What it means is only that confirmed temporal constraints do not form impossible cycles.

That still leaves many scenes only partially positioned.

This is especially true once interval relations like `OVERLAPS`, `DURING`, `STARTS`, and `FINISHES` are involved.

### 3.3 Reading-order adjacency cannot carry the whole temporal model

If we only model:

- scene-to-previous
- scene-to-next

then the graph mostly captures a corrected version of publication adjacency.

That is still valuable, but it remains fundamentally **local**.

Extra-local temporal resolution needs additional anchor structures that connect scenes across longer narrative distances.

---

## 4. What The Concept Docs Already Suggest

The concept docs already point toward the missing mechanism, even if current implementation does not provide it yet.

### 4.1 Scene links are intentionally local

The Event DAG concept explicitly frames scene-to-scene links as neighbor relations from triads, not a dense global scene-order graph.

That implies local scene links are a backbone, not the whole story.

### 4.2 Extra-local resolution is supposed to come from anchors

The research direction in [Narrative event DAG](../../concepts/Narrative%20event%20DAG.md) introduces additional structures:

- **Events**
- **Landmarks**
- **Arcs**

Those are the intended bridge from local scene evidence to broader temporal placement.

The key conceptual move is:

- scenes are local evidence-bearing anchors
- events/landmarks/arcs provide reusable temporal reference points
- broader ordering is inferred through those shared anchors rather than by materializing dense scene↔scene links

### 4.3 Retrieval-time reasoning, not dense persistence, is still the intended philosophy

The concept docs consistently favor:

- sparse persisted edges
- evidence-backed local structure
- no eager transitive materialization
- retrieval-time reasoning for longer paths

That means extra-local temporal resolution should probably be understood as:

- adding better anchor relationships
- not filling the graph with fully materialized long-range scene-to-scene edges

---

## 5. The Extra-Local Resolution Problem

The practical question is:

> If scene-local temporal inference only tells us about nearby scenes, what graph structures let us relate a scene to distant scenes without pretending we know more than we do?

This leads to a more precise technical framing:

### 5.1 Local scene relations answer

- what happened immediately before or after in narrative context
- whether adjacent scenes probably meet, overlap, or reverse expected chronology

### 5.2 Extra-local temporal resolution must answer

- which broader event a scene occurs during
- which named event a scene refers back to or anticipates
- which scenes share a common landmark or arc
- how to bridge across chapters or books without assuming direct scene↔scene comparability

### 5.3 Therefore the missing capability is not “better sorting”

It is:

- **better temporal anchoring**
- **better reusable reference structures**
- **better query-time composition across those structures**

---

## 6. Candidate Anchor Structures

### 6.1 Scenes only

The smallest possible model keeps scenes as the only temporal entities and relies on local scene↔scene temporal edges.

#### Strengths

- simpler to implement
- aligns with current ingestion direction
- enough for local continuity and chapter-boundary reasoning

#### Weaknesses

- broad temporal alignment remains weak
- flashbacks and recalled history remain hard to place outside local context
- longer-range temporal questions collapse back toward reading order
- there is no reusable object to represent “the siege,” “the coronation,” or “the voyage” across many scenes

#### Conclusion

This is probably sufficient for **local temporal linking V1**, but not for robust extra-local resolution.

### 6.2 Scene → Landmark attachments

Landmarks are explicit temporal anchors such as:

- dates
- clock times
- named battles
- festivals
- franchise-wide milestones

Scenes can attach to landmarks using relations such as `EQUALS` or `DURING`.

#### Strengths

- offers a strong extra-local bridge without requiring full scene↔scene densification
- useful for cross-book alignment
- aligns directly with the Event DAG concept docs

#### Weaknesses

- depends on detecting anchor-worthy landmarks reliably
- many scenes will not contain explicit landmark cues
- landmarks help with absolute/canonical anchors, but not necessarily with all narrative-event references

#### Conclusion

Landmarks are valuable, but they are likely only part of the answer.

### 6.3 Scene → Event attachments

This is the most important candidate.

Instead of treating scenes as the only meaningful temporal unit, scenes can point to canonical events such as:

- a coronation
- a siege
- a journey
- a murder
- an investigation

Scene↔Event relations then carry roles such as:

- `during`
- `before`
- `after`
- `starts`
- `finishes`
- `equals`
- `refers_to` when temporal commitment is weak

#### Strengths

- creates reusable temporal anchors across many scenes
- supports flashback, foreshadowing, aftermath, and retrospective narration cleanly
- allows multiple scenes to align through the same event without needing direct scene↔scene links
- matches the conceptual Event DAG most closely

#### Weaknesses

- requires extracting or curating **Events as canonical entities**
- increases entity-modeling scope substantially
- depends on event identity / deduplication / proposal workflows

#### Conclusion

If LoreVault wants meaningful extra-local temporal resolution, this looks like the most likely long-term mechanism.

### 6.4 Scene/Event → Arc attachments

Arcs are higher-level container events such as:

- a war
- a journey
- an investigation
- a season-long political conflict

#### Strengths

- useful for coarse-grained temporal grouping
- helps explain nested or extended narrative structure

#### Weaknesses

- too coarse to replace event-level anchoring
- likely needs curation or richer semantic extraction

#### Conclusion

Arcs are likely complementary, not primary.

---

## 7. Likely Technical Shape

If the conceptual direction holds, the graph would behave like this:

### 7.1 Persist sparse local scene structure

- neighbor scene relations from triads
- confidence/evidence on those local edges
- ambiguity/contested state when local judgments diverge

### 7.2 Attach scenes to extra-local anchors

- Scene ↔ Event
- Scene ↔ Landmark
- Scene ↔ Arc

### 7.3 Optionally attach events to broader anchors

- Event ↔ Landmark
- Arc contains Event
- Arc contains Scene
- explicit Event ↔ Event temporal relations when text actually names them

### 7.4 Answer broader temporal questions through path composition

Example:

- Scene A `during` Event X
- Event X `during` Landmark L
- Scene B `after` Event X

Now Scene A and Scene B can be related through Event X or Landmark L, without persisting a direct long-range scene↔scene edge.

This is consistent with the existing concept-doc philosophy of lazy retrieval-time temporal reasoning.

---

## 8. Why Event Extraction Probably Matters

This discussion suggests a real architectural fork.

### 8.1 If scenes remain the only temporal entities

Then extra-local temporal resolution stays weak.

At best, LoreVault can:

- maintain local scene chronology
- detect local discontinuities
- maybe bridge through occasional landmarks

But it will struggle to express:

- multiple scenes depicting the same broader happening
- retrospective references to prior canonical happenings
- broad temporal alignment across books or arcs

### 8.2 If canonical events become first-class extracted entities

Then scenes become what the concept docs suggest they should be:

- local evidence containers
- temporal anchors for reading-order analysis
- attachment points to reusable Event / Landmark / Arc structures

That would make extra-local temporal resolution much more plausible.

### 8.3 Therefore

The extra-local temporal-resolution problem may be inseparable from a broader event-extraction problem.

If LoreVault postpones canonical Event extraction indefinitely, it should do so knowingly and accept that the Event DAG will remain largely scene-local.

---

## 9. Recommended Framing

The cleanest way to talk about this is:

- **local temporal linking** = scene↔scene neighborhood judgments derived from triads
- **extra-local temporal resolution** = relating scenes through reusable anchors such as Events, Landmarks, and Arcs
- **global timeline** = not a persisted total order, but a query-time reconstruction from sparse constraints

That framing avoids a misleading goal like:

> “give every scene one canonical global timeline position”

Instead, the goal becomes:

> “make each scene legibly placeable relative to reusable temporal anchors, then reason across those anchors when needed”

---

## 10. Proposed Staging Direction

This is not a commitment, but it looks like the most coherent staged path.

### Stage 1 — keep local temporal linking bounded

Focus current implementation work on:

- robust local scene↔scene temporal linking
- chapter-boundary coverage
- evidence/confidence preservation
- clear distinction between reading-order adjacency and temporal inference

### Stage 2 — add landmark attachment where explicit cues exist

This is the least disruptive extra-local enhancement because it does not require a full event-identity model.

### Stage 3 — introduce Event extraction as canonical entities

This is the likely threshold where extra-local temporal resolution becomes genuinely useful.

At that point, the graph can support:

- many scenes linking to the same event
- retrospective / prospective / aftermath relationships
- broader temporal composition across chapters and books

### Stage 4 — add richer retrieval-time temporal composition

Only after the anchor graph exists does it make sense to invest heavily in:

- Allen-composition query logic
- evidence-path explanations
- confidence-aware long-range temporal answers

---

## 11. Open Questions

- Is there a useful intermediate step between scene-only temporal linking and full canonical Event extraction?
- Should landmark extraction be treated as its own smaller vertical before Event extraction?
- What minimum event-identity model is enough to support Scene ↔ Event anchoring without triggering a full ontology project?
- Should event proposals remain curation-first for a long time, or is lightweight automatic event materialization acceptable?
- Which extra-local query classes actually matter most for MVP and post-MVP product value?
- How should contested local scene judgments interact with Event / Landmark attachments when they disagree?

---

## 12. Practical Takeaway

The important takeaway is not that local scene temporal linking is misguided.

It is that local linking and extra-local temporal resolution are **different problems**.

Local linking can and should still be improved.

But if LoreVault wants a temporal graph that supports broader narrative chronology, the likely missing ingredient is not a better sort over scene-local edges.

It is a richer anchor model built from:

- canonical Events
- Landmarks
- Arcs
- retrieval-time composition over those structures

That makes this less a pure scene-linking problem and more a future **event modeling + anchor attachment + temporal reasoning** problem.

---

## 13. Related Docs

- `../../concepts/temporal-relation-semantics.md`
- `../../adr/010-practical-allen-relation-usage.md`
- `../../concepts/event-dag.md`
- `../../concepts/Narrative event DAG.md`
- `../../concepts/event-model.md`
- `../../concepts/Entity-Event-Claim-model.md`
- `./2026-04-17T1113_scene-temporal-linking-brainstorm.md`
- `./2026-04-18T1113_scene-temporal-linking-solution-design-proposal.md`

---

## 14. Implementation Notes Since This Proposal

- No code implementation has been done from this brainstorm yet.
- The discussion around practical Allen relation usage and the mismatch between theoretical `MEETS` and narrative temporal evidence has been promoted into an accepted ADR.
- That ADR records LoreVault's decision to keep the full Allen relation set documented, but to deprecate `MEETS` / `MET_BY` for inferred use and to prefer `NEXT_IN_READING_ORDER` for structural adjacency.
- Extra-local temporal resolution itself remains future-facing and stays in brainstorm rather than being promoted as current mechanism truth.
