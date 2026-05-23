# Scene Detection Naming Analysis — April 2026

**Status:** Brainstorm — terminology analysis, not yet an accepted decision  
**Purpose:** Record why the current `pass1` / `pass2` naming should be changed before it hardens further across code, prompts, observability, tests, and docs.

---

## 1. The Problem

LoreVault still refers to the two scene-detection stages as `Pass 1` and `Pass 2` in multiple important places:

- code comments and method names
- prompt keys and prompt filenames
- config keys
- logs and statuses
- LLM call records
- tests and fixtures
- docs and architecture descriptions

That naming is already serviceable for a temporary implementation phase, but it is becoming architecturally misleading.

`pass1` and `pass2` are **sequence names**, not **responsibility names**.

They explain the order in which the steps run, but they do not explain what the steps actually do.

This is the point at which naming debt should be corrected early, before it becomes truly inhibitive to change.

---

## 2. Why The Current Names Are Weak

### 2.1 They hide the actual responsibilities

The first stage is not “pass 1” in any durable architectural sense. It is the stage that:

- takes chapter text
- segments it into scenes
- produces scene-local hints and anchors

The second stage is not “pass 2” in any durable architectural sense. It is the stage that:

- analyzes the scenes produced by the first stage
- uses triad context
- enriches scene structure with temporal and entity-aware information

`pass1` / `pass2` hide these real responsibilities.

### 2.2 They make architecture discussions harder

As LoreVault grows, people will increasingly need to talk about:

- what the scene segmentation stage is responsible for
- what the scene analysis stage is responsible for
- which stage should own which artifacts
- how scene packets, evidence bundles, or interpretation-layer artifacts are derived

Sequence names make those discussions less precise.

### 2.3 They age badly as pipelines evolve

If the system later adds:

- segmentation fallback logic
- richer scene analysis
- packet derivation
- event linking
- additional helper stages

then “pass 1” and “pass 2” become even less descriptive. They encourage the team to keep thinking in terms of “the second thing we do” rather than “the scene analysis stage.”

### 2.4 They leak temporary implementation language into durable surfaces

Right now the names already appear in:

- prompt keys
- status records
- observability data
- docs

That means temporary language is already leaking into durable architecture language.

If not corrected soon, the rename cost keeps increasing.

---

## 3. Stronger Naming Direction

The strongest candidate pair discussed was:

- **chapter segmentation**
- **scene analysis**

### Why this pair is strong

#### Chapter segmentation
This names the action clearly:

- input: chapter text
- output: segmented scenes

It is concrete, readable, and easy to understand in logs, docs, and code.

#### Scene analysis
This names the second stage by its actual responsibility:

- analyze scenes
- enrich them with temporal/entity/location/context structure

It is broad enough to survive future evolution without needing another rename immediately.

### Why this is better than pass-based naming

It replaces:

- implementation-order language

with:

- responsibility-oriented language

That is a healthier architectural vocabulary.

---

## 4. Architectural Benefit Of Renaming Early

Renaming now is not just cosmetic.

It helps LoreVault in three ways:

### 4.1 Cleaner conceptual boundaries

It becomes much easier to say:

- chapter segmentation produces scene boundaries and scene cards
- scene analysis enriches those scenes with interpretation-ready structure

That is much clearer than:

- pass 1 does X
- pass 2 does Y

### 4.2 Better alignment with future architecture

LoreVault is now moving toward a clearer layered model:

- evidence layer
- interpretation layer
- Q&A runtime

That makes responsibility-first names even more important.

The stage that creates scene boundaries should sound like an evidence-layer production step.
The stage that enriches scenes should sound like an interpretation-oriented analysis step.

### 4.3 Reduced future migration pain

The user explicitly does **not** care about preserving historical naming if it blocks better architecture.

That is the right time to rename.

If the system waits longer, the names will spread into:

- more persisted data
- more tests
- more metrics dashboards
- more docs
- more product language

At that point the rename becomes organizationally expensive rather than just technically inconvenient.

---

## 5. What Should Be Renamed

The rename should be thought of as a terminology sweep across several layers.

### Code-level names

- method names
- service comments and JavaDocs
- enum descriptions / status text
- helper method names referring to `pass1` / `pass2`

### Prompt/config names

- prompt logical names
- prompt filenames
- prompt property keys

### Observability names

- LLM call step names
- ingestion status messages
- logging labels

### Tests and fixtures

- test method names
- fixture names
- fixture file names where worth cleaning

### Documentation

- current process docs
- pattern docs
- brainstorm docs

This is why the rename should be treated as an architectural terminology cleanup, not as a narrow text substitution.

---

## 6. Scope Recommendation

The naming cleanup does **not** need to block ongoing feature work forever, but it should happen before the terminology becomes more deeply embedded in:

- interpretation-layer design
- packet design
- Q&A retrieval architecture
- expanded observability/UI surfaces

That makes this a good “fix it before it hardens” task.

---

## 7. Recommended Terminology

Recommended pair:

- **chapter segmentation**
- **scene analysis**

These should become the preferred terms for:

- docs
- architecture discussion
- code naming where practical
- prompt/config naming if the team decides to do a full sweep

---

## 8. Bottom Line

`pass1` / `pass2` are no longer good enough as the main names for LoreVault's scene-detection pipeline.

They are temporary implementation-order labels that are starting to harden into architecture language.

That is exactly when they should be replaced.

The best current replacement is:

- **chapter segmentation**
- **scene analysis**

The rename is worth doing now because the cost of waiting is the steady spread of weak terminology into every layer of the system.
