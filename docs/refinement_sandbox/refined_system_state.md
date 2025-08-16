# Refined System State: Post-Discussion Updates

## 1. Cardinal Entity Types (FINALIZED)

### Core Types
- **Agent**: Individual entities with personal agency (persons, AIs, sentient beings)
- **Collective**: Groups with collective agency (factions, organizations, governments, teams)
- **Object**: Inanimate materials, tools, structures, artifacts
- **Location**: Spatial references, places, coordinates
- **Concept**: Abstract entities, principles, ideas, laws, **Species** (biological classifications)
- **Event**: Temporal occurrences, actions, happenings
- **Claim**: Meta-entity representing assertions about other entities

### Key Clarifications
- **Species as Concept**: Biological classifications (Homo sapiens, Vulcans) are conceptual categories, not collective agents
- **Collective vs Species**: "The Klingon Empire" (collective) vs "Klingons" as biological species (concept)
- **Claim as Entity**: Claims themselves can be subjects of other claims (disputed, retracted, supported by evidence)

---

## 2. Cross-Work Entity Resolution (SOLVED)

### PublicationCoordinates Integration
```java
public class PublicationCoordinates {
    @NotBlank private String universe;    // "star_trek", "deathworlders"  
    private String series;                // "original_series", "next_generation"
    private String bookTitle;
    private String chapterTitle;
    private Integer bookNumber;
    private Integer partNumber; 
    private Integer chapterNumber;
}
```

**Cross-work queries via Cypher:**
```cypher
// Find all mentions of Spock across all Star Trek works
MATCH (e:Entity {id: "E:person/spock"})-[r]->(claim:Claim)
WHERE claim.pubCoords.universe = "star_trek"
RETURN claim.pubCoords.series, claim.pubCoords.bookTitle, r, claim
```

**No gaps identified** - universe scope enables cross-series connections while maintaining proper attribution.

---

## 3. Entity Versioning (ARCHITECTURAL SOLUTION)

### Identity vs Version Pattern
```
[Identity Node] ← HAS_VERSION ← [Version Node] ← CLAIMS ← [Claim Nodes]
```

**Structure:**
- **Identity Node**: Lightweight, permanent ID (`E:person/luke_skywalker`)  
- **Version Node**: Time-anchored variants with PublicationCoordinates + Event anchors
- **Claims connect to Version Nodes**, not Identity Nodes

**Example:**
```
E:person/luke_skywalker (Identity)
├── luke_v1 (farmboy, Episode IV, pub: {series:"original", book:1})
├── luke_v2 (jedi_knight, Episode VI, pub: {series:"original", book:3})
└── luke_v3 (jedi_master, sequel_era, pub: {series:"sequel", book:1})
```

**Advantages:**
- Clean temporal separation
- Claims remain precise to narrative context  
- Identity persists across versions
- Enables "character development" queries

---

## 4. Nested Entity Handling (GRADUATED APPROACH)

### Initial: Qualifiers Pattern
```
Attribute(subject=E:person/alien, key=P:physical.strength.tensile, value=weak,
          qualifiers={body_part:"left_arm", condition:"damaged"}).
```

### Advanced: Background Monitoring for Entity Promotion
```python
def monitor_entity_complexity(entity_id):
    part_references = count_qualifier_usage(entity_id, "body_part")
    if part_references > threshold:
        queue_for_manual_review(entity_id, "consider_part_splitting")
```

**Manual review decides**: Keep as qualifiers vs promote to separate entities
- `E:person/alien` + `qualifiers.body_part:"left_arm"`
- → `E:person/alien` + `E:body_part/alien_left_arm`

---

## 5. Temporal Relations (STANDARDIZED)

### Allen Interval Algebra Integration
**Standard Relations:**
- `R:temporal.before` / `R:temporal.after`
- `R:temporal.meets` / `R:temporal.met_by` (adjacent)
- `R:temporal.overlaps` / `R:temporal.overlapped_by` 
- `R:temporal.starts` / `R:temporal.started_by`
- `R:temporal.during` / `R:temporal.contains`
- `R:temporal.finishes` / `R:temporal.finished_by`
- `R:temporal.equals` (same interval)

**Causal Extensions:**
- `R:temporal.caused_by` / `R:temporal.enables`
- `R:temporal.prevents` / `R:temporal.interrupted_by`

**Benefits:**
- Mathematically rigorous
- Standard inference rules
- Conflict detection algorithms exist
- DAG construction becomes deterministic

---

## 6. Scene-to-Event & Granularity (LLM-DRIVEN)

### Approach: Prompt Engineering + Model Selection
**Not deterministic by design** - narrative understanding requires interpretive flexibility

**Control mechanisms:**
- **Temperature=0**: Deterministic for same input
- **Prompt templates**: Consistent extraction patterns  
- **Model selection**: GPT-4 vs Claude vs specialized models
- **Quality scoring**: Post-process for coherence

**Granularity prompts:**
```
"Extract events from this scene. Consider:
- Major plot developments (always extract)
- Character interactions (extract if meaningful)  
- Environmental changes (extract if relevant)
- Micro-actions (extract only if pivotal)"
```

---

## 7. Property Catalog (POC REQUIREMENTS)

### Seed Catalog Structure
```
P:physical.*       # Strength, size, composition, appearance
P:cognitive.*      # Intelligence, memory, reasoning, knowledge  
P:social.*         # Status, relationships, influence, reputation
P:ability.*        # Skills, powers, capabilities
P:emotional.*      # Mood, temperament, reactions
P:temporal.*       # Duration, frequency, timing
P:spatial.*        # Location, distance, orientation
P:material.*       # Substance properties, chemical/physical traits
```

### Technical Implementation
1. **Vector embeddings**: Property descriptions → semantic search
2. **Graph modeling**: Hierarchical relationships, synonyms, antonyms  
3. **Agentic resolution**: LLM-powered property selection from top-K candidates
4. **Context awareness**: Entity type constraints guide suggestions

### Query Method
```python
def resolve_property(context: str, entity_type: str, top_k: int = 5):
    candidates = semantic_search(context, entity_type) 
    prompt = f"Given context '{context}' and entity type '{entity_type}', 
              which property best fits? Options: {candidates}"
    return llm_agent.select(prompt, candidates)
```

---

## 8. Data Pipeline (UPDATED FLOW)

### Confirmed Stages
**Chapter → LLM → Scenes → Chunk Embeddings** ✓

### To Be Confirmed (TBC)
**Scenes → Entity Extraction → Entity Resolution → Claims Extraction → Claim Analysis → Confidence Scoring → Edge Projection**

### Detailed Pipeline
```
1. Chapter Segmentation (LLM) → Scenes
2. Scene Analysis (LLM) → Entities + Events + Raw Claims
3. Entity Resolution (Vector + Rules) → Canonical Entity IDs
4. Claim Mapping (Agentic) → Property/Relation/Action IDs  
5. Claim Validation (Schema + Constraints) → Valid Claims
6. Confidence Aggregation (Formula) → Scored Aggregates
7. Edge Projection (Threshold Filter) → Graph Updates
8. Spoiler Gating (PubCoords) → User-Visible Edges
```

**Critical decision points:**
- Entity resolution accuracy requirements
- Claim validation strictness  
- Confidence thresholds for promotion
- Real-time vs batch processing

---

## 9. Implementation Status Summary

### READY FOR IMPLEMENTATION
- ✅ **Cardinal Entity Types**: Finalized taxonomy
- ✅ **Cross-Work Resolution**: PublicationCoordinates sufficient  
- ✅ **Temporal Relations**: Allen algebra provides foundation
- ✅ **Claim DSL**: Syntax and parser specification complete

### ARCHITECTURALLY SOLVED
- 🏗️ **Entity Versioning**: Identity/Version pattern designed
- 🏗️ **Nested Entities**: Graduated qualifier → promotion approach
- 🏗️ **Confidence System**: Mathematical formula established

### NEEDS DEVELOPMENT
- 🚧 **Property Catalog**: Seed data + vector implementation
- 🚧 **Scene-to-Event**: LLM prompt templates + quality metrics  
- 🚧 **Pipeline Integration**: End-to-end orchestration
- 🚧 **Validation Framework**: Quality gates + error handling

### UNDEFINED/FUTURE
- ❓ **Success Metrics**: Accuracy, coverage, performance targets
- ❓ **UI/UX**: Curation interfaces, query tools
- ❓ **Scale Testing**: Large corpus performance validation

---

## 10. Next Implementation Phase

### POC Priority Order
1. **Property Catalog MVP**: Seed taxonomy + vector search + agentic resolution
2. **Entity Version System**: Identity/Version nodes + claim linking  
3. **Allen Temporal Relations**: Standard relation types + inference rules
4. **Scene-to-Event Pipeline**: LLM extraction + Event entity creation
5. **End-to-end Integration**: Single chapter → knowledge graph demonstration

The system now has clear architectural decisions for all major components, with implementation paths identified for each. The LLM-driven approaches (scene analysis, property resolution) embrace the interpretive nature of narrative understanding while maintaining systematic consistency through structured outputs and validation.