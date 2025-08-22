# Ingestion Changes for Skeleton Timeline (v0.9.0)

Overview

We extend the chapter ingestion to produce Events (from Scenes) and temporal links between consecutive scenes, with certainty and rationale captured.

LLM Responsibilities

- Segmentation: ordered scenes with offsets
- Temporal relation: relation to previous scene (temporalRelation)
- Certainty classification: CertaintyLevel enum
- Rationale: short quote or explanation text
- Flags: Flashback, ParallelPOV, Dream (others deferred)

Service Responsibilities

- Convert Segments → :Event:Scene nodes (Entity subtype with dual label)
- Create temporal edges Event[i-1] → Event[i] with temporalRelation, certainty, weight, rationale, offsets
- Map certainty → numeric weight using mapping table
- Persist raw LLM JSON as evidence alongside normalized records (optional)
- Add cross-chapter edges: last Event of chapter k to first of k+1 as MEETS@Heuristic; upgrade via LLM when confident

Data Flow Adjustments

1. Preload prior context: last scene summary of previous chapter/book
2. Prompt LLM for segmentation + temporal assessment
3. Validate LLM JSON against schema (existing XML/JSON patterns)
4. Persist: Events, TemporalEdges, Evidence
5. Publish: Event IDs back to caller in ingestion job result

Backfill/Migration

- For existing ingested chapters, create Events per stored Scene and connect consecutive Events with meets@Heuristic edges
- Attach provenance where available (scene text range); otherwise mark rationale="migration default"

Observability

- Log temporal link counts and certainty distribution per chapter
