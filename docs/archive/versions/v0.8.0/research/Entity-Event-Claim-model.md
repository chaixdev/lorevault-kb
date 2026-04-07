> **Research only - not an implementation target**

# Narrative → Knowledge Graph: the Entity-Claim conceptual model

---

## 1) Core principles

- **Four bins only:** `Attribute`, `Relation`, `Comparison`, `Ability`.
    
- **One Property catalog:** a “property” can be used as an **attribute** (single subject) or a **dimension** (for comparisons).
    
- **Registry + retrieval, not giant prompts:** fetch top-K relevant vocab items (properties, relation types, actions) per call; the model must choose from them (or propose a provisional).
    
- **Confidence = weighted endorsements:** many noisy claims roll up offline; queries traverse the already-scored projection.
    
- **Spoiler safety:** every artifact carries publication coordinates; edges are gated by user progress.
    
- **Minimal surface:** shallow keys, boring IDs, few conventions—easy to teach to people and models.
    

---

## 2) Data model (MVP)

### Entities

Canonical nodes you’ll relate/describe (people, species, orgs, places, events, materials).

### Claims (four bins)

All claims share: `source`, `subjectId`, `certainty (0–1)`, `polarity (assert|deny)`, `qualifiers?`, `pubCoords`, `chunkId`, `offsets`.

- **Attribute** — “subject has/is …”
    
    - `propertyId` _(from catalog)_, `value` _(typed literal or entityId)_, `subjectivity? (0–1)`
        
    - Minimal facet examples (as properties): `P:body.composition`, `P:body.tensile_strength`, `P:description`, `P:group.demographics`
        
    - Specifics go in **qualifiers** (e.g., `part: "endoskeleton"`, `unit`, `measurement`, `analogy_to`, `sample_condition`).
        
- **Relation** — “subject relates_to object (type)”
    
    - `relTypeId` _(e.g., `R:kinship.parent_of`, `R:located_in`, `R:has_member_class`, `R:participated_in`)_, `objectId`
        
- **Comparison** — “subject vs target on property”
    
    - `propertyId` _(dimension, e.g., `P:strength`, `P:speed`)_, `operator (lt|gt|≈|=)`, `targetId`
        
- **Ability** — “subject can/cannot do action”
    
    - `actionIds` _(e.g., `A:pull`, `A:push`, `A:twist`)_, `can: boolean`
        

> **Composition split clarified:**  
> _Material_ composition → **Attribute** (`P:body.composition`, `value: silica`, `qualifiers.part: "endoskeleton"`).  
> _Membership_ composition → **Relation** (`R:has_member` / `R:has_member_class` with optional `proportion` qualifier) or summarized Attribute (`P:group.demographics`).

---

## 3) Vocabulary catalogs (the “caralog”)

One small table per type with embeddings + metadata:

- **properties(id, label, allowedKinds, valueType, comparable, direction?, synonyms[], examples[], constraints, status, replacedBy, usage_count, embedding)**
    
- **reltypes(id, label, inverseId?, canonicalDirection, synonyms[], constraints, …, embedding)**
    
- **actions(id, label, synonyms[], constraints, …, embedding)**
    

**Constraints** guard usage (e.g., `subjectTypes`, `objectTypes`).  
**allowedKinds** for properties: `["Attribute"]` or `["Attribute","Comparison"]`.

**Retrieval flow:** hybrid search (BM25 + vector) → filter by constraints → return top-K options (IDs + 1-line gloss) to the LLM. Unknowns come back as `provisional_*` and go to review.

---

## 4) Ingestion pipeline (end-to-end)

1. **Segment** chapter → scenes → chunks (with offsets + `pubCoords`).
    
2. **Triage** chunks (cheap **SubstanceScore** prefilter): NE density, relation verbs, objective markers, novelty bonus, hedge penalty. Keep top ~30%.
    
3. **Extract** mentions → candidate claims (with `source`, `certainty`, `hedging`, `sample_condition`, snippets & offsets).
    
4. **Map to catalogs** via retrieval menus (properties / relTypes / actions).
    
5. **Validate** IDs + constraints; unknowns → provisional queue.
    
6. **Store** claims (append-only).
    
7. **Aggregate** endorsements per stable key:
    
    - Attribute: `(subjectId, propertyId, valueNorm)`
        
    - Relation: `(subjectId, relTypeId, objectId)`
        
    - Comparison: `(subjectId, propertyId, operator, targetId)`
        
    - Ability: `(subjectId, actionId, can)`
        
8. **Compute confidence + status** (offline, cheap, interpretable).
    
9. **Project edges** onto the entity graph with `{confidence, support/deny counts, firstSeenCoords, aggregateId}`.
    
10. **Gate by spoilers** (indexable comparisons against `firstSeen*`).
    
11. **Compaction** (optional): keep high-confidence; archive low.
    

---

## 5) Confidence as endorsements (one formula that works)

For each aggregate:

```
support = Σ_i (sourceReliability_i × certainty_i × evidenceQuality_i)
deny    = Σ_j (sourceReliability_j × certainty_j × evidenceQuality_j)

conf_raw = α·ln(1+support) - β·ln(1+deny) + γ·avg(sourceReliability)
confidence = sigmoid(conf_raw)  # 0..1
status ∈ {consensus, contested, denied, speculative}
```

Defaults: narrator 0.9, domain expert 0.75, named witness 0.6, unreliable 0.3; hedging −0.05; damaged sample −0.05.  
Project when `confidence ≥ 0.6` (tune per domain).

---

## 6) Projection (read-optimized edges)

Keep 4 edge types (or one with `kind`), all carrying `aggregateId` for explainability:

- `:ATTR {propertyId, value, subjectivity?, confidence, firstSeen*}`
    
- `:REL {relTypeId, confidence, firstSeen*}`
    
- `:COMP {propertyId, operator, targetId, confidence, firstSeen*}`
    
- `:ABIL {actionIds, can, confidence, firstSeen*}`
    

---

## 7) Conventions that prevent drift

- **Minimal facet names** (as properties): prefer `P:body.composition` over deep paths; add specifics in `qualifiers.part`.
    
- **One comparison direction:** always from `subject`’s POV; rely on `operator`.
    
- **Canonical relation direction:** e.g., store `parent_of` and derive `child_of` when rendering.
    
- **Pronouns aren’t aliases:** resolve locally via coref; don’t add “we/they/us” to entity alias maps.
    
- **Booleans vs. denial:** use `value: false` for absence; reserve `polarity: deny` only for explicit negations.
    
- **Units & analogies** live in `qualifiers`, not baked into `value` strings.
    
- **Budget:** max K promoted edges per subject per scene; the rest remain as claims.
    

---

## 8) Tiny, reusable output schemas (abridged)

```yaml
# Attribute
kind: Attribute
propertyId: "P:body.composition"
subjectId: "E:species/alien_vancouver"
value: "E:material/silica"         # or {value: 30, unit: "MPa"}
certainty: 0.6
subjectivity: 0.0
qualifiers: { part: "endoskeleton", hedging_terms: ["seems"], sample_condition: "damaged" }
pubCoords: { seriesId, bookOrder, chapterOrder, sceneIndex }
chunkId: "c:…"
offsets: { start, end }

# Relation
kind: Relation
relTypeId: "R:has_member_class"
subjectId: "E:group/project_scientists"
objectId: "E:concept/young_scientist"
certainty: 0.7
qualifiers: { proportion: 0.7 }

# Comparison
kind: Comparison
propertyId: "P:strength"
subjectId: "E:species/alien_vancouver"
operator: "lt"
targetId: "E:species/human"
certainty: 0.8

# Ability
kind: Ability
subjectId: "E:species/alien_vancouver"
actionIds: ["A:pull","A:push","A:twist"]
can: true
certainty: 0.6
```

---

## 9) Quality gates (cheap, crucial)

- Offsets ⊂ chunk ⊂ scene ⊂ chapter.
    
- IDs exist in catalogs; `allowedKinds` respected.
    
- No duplicate keys within the same `(subject, …)` window.
    
- Normalize comparisons & relations to canonical direction.
    
- Promote only if `SubstanceScore ≥ τ` **and** the aggregate changes confidence/status/firstSeen.
    

---

## 10) MVP checklist

-  Stand up **catalog tables** (properties, reltypes, actions) with embeddings.
    
-  Build **retrieval menus** per claim kind (top-K options, IDs + glosses).
    
-  Implement **four claim schemas** + validator.
    
-  Add **SubstanceScore triage** + promote thresholds.
    
-  Implement **endorsement aggregation** + confidence.
    
-  Project **four edge types** with spoiler coords + indexes.
    
-  Add **curation UI**: review provisionals, merge duplicates, deprecate → replacedBy, auto-migrate.
    

---
heck yes—let’s turn that into a tiny, punchy **Claim DSL** you can read, write, and parse fast. It stays aligned with the four bins (Attribute / Relation / Comparison / Ability) and your Property catalog.

# The Claim DSL (CDSL)

**One claim per line. Key=Value pairs. Ends with a period.**  
Designed so humans can jot facts quickly and your parser can round-trip them into structured JSON.

## 1) Core syntax

```
<Kind>(field=value, field=value, ...).
```

Kinds (exactly these four): `Attribute`, `Relation`, `Comparison`, `Ability`.

### Common fields (available to all)

- `source=` who asserts it (entity id or label)
    
- `subject=` the thing being described (entity id)
    
- `certainty=` 0–100 (percent) or 0.0–1.0 (parser normalizes)
    
- `polarity=` `assert` | `deny` (optional; default `assert`)
    
- `qualifiers=` `{k:v, ...}` optional bag (units, part, hedges, etc.)
    
- `pub=` `{seriesId:"...", book:1, chapter:3, scene:1, chunk:"c:1.3.1.4"}` (optional)
    

Numbers accept `%` or raw (e.g., `60%` or `0.6`). Strings can be unquoted if slug-like; quote when they contain spaces.

### Catalog IDs (strongly recommended)

- **Entities:** `E:person/martin_tremblay`
    
- **Properties:** `P:body.composition`, `P:strength`
    
- **Rel types:** `R:kinship.parent_of`, `R:appeared_at`
    
- **Actions:** `A:pull`, `A:push`, `A:twist`
    

(If you don’t know an ID yet, you can write a label; your validator maps it or flags it.)

---

## 2) Bin-specific fields

### Attribute — “subject has/is …”

```
Attribute(source=..., subject=..., key=P:..., value=<literal|entityId>,
          subjectivity=0..100, certainty=..., qualifiers={...}).
```

- `key` = Property id (used as attribute).
    
- `value` = typed literal (`42`, `"blue"`, `true`) or entity id (`E:material/silica`).
    
- `subjectivity` = 0–100 (adjectives high, measurements low).
    

**Examples**

```
Attribute(source=narrator, subject=E:person/he, key=P:description, value=annoying, subjectivity=100, certainty=100).
Attribute(source=Speaker1, subject=E:person/she, key=P:eye_color, value=blue, subjectivity=0, certainty=50).
Attribute(source=E:person/dr_taylor, subject=E:species/alien_vancouver,
          key=P:body.composition, value=E:material/silica,
          certainty=60, qualifiers={part:"endoskeleton", hedges:["seems"], sample:"damaged"}).
```

### Relation — “subject relates_to object (type)”

```
Relation(source=..., subject=..., type=R:..., object=E:..., certainty=...).
```

**Examples**

```
Relation(source=narrator, subject=E:person/martin_tremblay, type=R:briefed_by, object=E:person/dr_taylor, certainty=90).
Relation(source=narrator, subject=E:group/project_scientists, type=R:has_member_class, object=E:concept/young_scientist, certainty=70, qualifiers={proportion:0.7}).
```

### Comparison — “subject vs target along property”

```
Comparison(source=..., subject=E:..., property=P:..., op=<|>|≈|=, target=E:..., certainty=...).
```

(Choose one direction; stick with it everywhere.)

```
Comparison(source=E:person/dr_taylor, subject=E:species/alien_vancouver, property=P:strength, op=<, target=E:species/human, certainty=80).
```

### Ability — “subject can/cannot do actions”

```
Ability(source=..., subject=E:..., actions=[A:..., A:...], can=true|false, certainty=...).
```

```
Ability(source=E:person/dr_taylor, subject=E:species/alien_vancouver, actions=[A:pull, A:push, A:twist], can=true, certainty=60, qualifiers={hedges:["we think"]}).
```

---

## 3) Nice quality-of-life additions

### a) Context directive (defaults for following lines)

Set shared scaffolding once; applies to subsequent claims until changed.

```
@context(source=narrator, pub={seriesId:"deathworlders", book:1, chapter:3, scene:1});
```

Now you can omit `source` and `pub` on every line.

### b) Aliases (pronoun disambiguation in this block)

```
@alias(he=E:person/martin_tremblay, she=E:person/dr_betty_cote);
```

### c) Comments

Use `#` or `//` to comment a whole line.

---

## 4) From the excerpt (compact CDSL)

```
@context(source=narrator, pub={seriesId:"deathworlders", book:1, chapter:3, scene:1});
@alias(he=E:person/martin_tremblay, aliens=E:species/alien_vancouver, humans=E:species/human);

Relation(subject=aliens, type=R:appeared_at, object=E:location/vancouver, certainty=90).

Attribute(source=E:person/dr_taylor, subject=aliens, key=P:body.composition, value=E:material/silica,
          certainty=60, qualifiers={part:"endoskeleton", hedges:["seems"], sample:"damaged"}).

Attribute(source=E:person/dr_taylor, subject=aliens, key=P:body.tensile_strength, value:"~ smoked salmon",
          certainty=50, qualifiers={analogy_to:E:material/smoked_salmon}).

Ability(source=E:person/dr_taylor, subject=aliens, actions=[A:pull, A:push, A:twist], can=true, certainty=60,
        qualifiers={hedges:["we think"]}).

Comparison(source=E:person/dr_taylor, subject=aliens, property=P:strength, op=<, target=humans, certainty=80).
```

---

## 5) How your parser can treat it (round-trip schema)

Example line:

```
Attribute(source=E:person/dr_taylor, subject=E:species/alien_vancouver, key=P:body.composition, value=E:material/silica, certainty=60, qualifiers={part:"endoskeleton"}).
```

Normalize to JSON:

```json
{
  "kind":"Attribute",
  "sourceId":"E:person/dr_taylor",
  "subjectId":"E:species/alien_vancouver",
  "propertyId":"P:body.composition",
  "value":{"objectId":"E:material/silica"},
  "certainty":0.60,
  "subjectivity":0.0,
  "qualifiers":{"part":"endoskeleton"},
  "pubCoords":{"seriesId":"deathworlders","book":1,"chapter":3,"scene":1}
}
```

---

## 6) Micro-grammar (EBNF-ish)

```
line        := stmt "." | directive ";"
stmt        := kind "(" fields ")"
kind        := "Attribute" | "Relation" | "Comparison" | "Ability"
fields      := field ("," field)*
field       := name "=" value
name        := /[a-zA-Z_][a-zA-Z0-9_]*/
value       := id | number | percent | string | boolean | list | map
id          := /(E|P|R|A):[a-z0-9_]+(?:\/[a-z0-9_]+)*/
number      := /-?\d+(?:\.\d+)?/
percent     := number "%"
string      := QUOTED | SLUG
boolean     := "true" | "false" | "yes" | "no"
list        := "[" (value ("," value)*)? "]"
map         := "{" (name ":" value ("," name ":" value)*)? "}"
directive   := "@context" "(" map ")" | "@alias" "(" map ")"
```

Parser tips:

- Normalize `certainty` / `subjectivity` to 0–1 floats.
    
- Accept `polarity=deny` OR `value=false` for absence; prefer boolean values in Attributes.
    
- Treat `qualifiers` keys loosely (don’t hard-code them): `unit`, `measurement`, `part`, `hedges`, `sample`, `analogy_to`, `time`, `place`, `proportion`, etc.
    

---

## 7) Lint rules (keep things tidy)

- **Must-have fields:**
    
    - Attribute: `subject`, `key`, `value`
        
    - Relation: `subject`, `type`, `object`
        
    - Comparison: `subject`, `property`, `op`, `target`
        
    - Ability: `subject`, `actions`, `can`
        
- **Use IDs where possible** (`E:/P:/R:/A:`).
    
- **One direction for Comparison** (choose a house POV).
    
- **Keep facets shallow** (`P:body.composition`, not deep trees); add specifics in `qualifiers.part`.
    
- **No global pronoun aliases**; always set `@alias` per block if you use pronouns.
    

---

## 8) Why this works

- **Tiny surface area** → you’ll actually use it.
    
- **Readable** → writers/annotators can jot facts in plain text.
    
- **Parseable** → machine-friendly to turn into claims → aggregates → projected edges.
    
- **Catalog-ready** → `P:/R:/A:` IDs make reuse automatic; provisionals get flagged.
    
