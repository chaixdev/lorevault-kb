Here’s a compact, endpoints-only reference (grouped by domain, with planned version tags).

# LoreVault REST Endpoints (Compact)

## Content Ingestion

* **POST** `/api/ingest/submit-file` — Submit a `.txt`/`.md` chapter for processing. *(v0.3.x)*

## Job Monitoring

* **GET** `/api/jobs/{jobId}` — Get status for a specific job. *(v0.3.x)*
* **GET** `/api/jobs` — List jobs (supports filtering/pagination). *(v0.3.x)*

## Lore Exploration — Chapters

* **GET** `/api/lore/{universe}/chapters` — List chapters for a universe. *(planned v0.3.0+)*
* **GET** `/api/lore/chapters/{chapterId}/entities` — Entities mentioned in a chapter. *(v0.4.0)*

## Lore Exploration — Entities

* **GET** `/api/lore/entities` — List entities (filter by universe/series/type, etc.). *(v0.4.0)*
* **GET** `/api/lore/entities/{entityId}` — Get entity details. *(v0.4.0)*
* **GET** `/api/lore/entities/{entityId}/mentions` — Mentions (with chapter context). *(v0.4.0)*
* **GET** `/api/lore/entities/{entityId}/relations` — Relations for an entity. *(v0.4.0)*

## Lore Exploration — Relations (Global)

* **GET** `/api/lore/relations` — List relations globally (filterable). *(v0.4.0)*

## Search & QA

* **GET** `/api/lore/search` — Keyword search across lore. *(v0.4.0)*
* **POST** `/api/lore/semantic-search` — Embedding-based semantic search. *(v0.4.0)*
* **POST** `/api/lore/qa` — RAG question-answer over knowledge base. *(v0.4.0+)*

## Graph Exploration

* **GET** `/api/lore/graph/neighbors` — Neighborhood/ego graph for an entity. *(v1.0.0)*
* **POST** `/api/lore/graph/paths` — Compute paths between entities. *(v1.0.0)*

## Timelines & Discovery

* **GET** `/api/lore/timeline` — Timeline events within scope (universe/series/book). *(v1.0.0)*
* **GET** `/api/lore/autocomplete` — Autocomplete for names/aliases/terms. *(v0.4.0)*
* **GET** `/api/lore/facets` — Faceted counts to power filters. *(v0.4.0)*

# json structure
Got it! Here’s a compact, implementation-ready **Entity** JSON model that’s general across types, with type-specific fields placed under a dedicated `properties` map.

# Entity Read Model

## Common Fields (type-agnostic)

```json
{
  "id": "uuid",                       // stable entity id
  "slug": "string",                   // URL-friendly, unique per universe
  "type": "character|location|organization|item|event|term|creature|custom",
  "universe": "string",
  "series": "string|null",
  "name": "string",
  "aliases": ["string"],
  "summary": "string|null",           // 1–~600 chars
  "status": "canonical|candidate|merged|deprecated",
  "canonicalConfidence": 0.0,         // 0..1
  "disambiguation": "string|null",    // short qualifier if needed

  "properties": {                     // type-specific map (see examples)
    "...": "any"
  },
  "context": {                        // first/last occurrence in corpus
    "firstSeen": {
      "chapterId": "uuid",
      "chapterTitle": "string",
      "order": 123                    // chapter order within scope
    },
    "lastSeen": {
      "chapterId": "uuid",
      "chapterTitle": "string",
      "order": 456
    }
  },

  "stats": {
    "mentions": 0,
    "relations": 0,
    "chaptersAppearedIn": 0,
    "evidenceCount": 0
  },

  "tags": ["string"],                 // free-form, optional

  "provenance": {                     // traceability
    "createdAt": "2025-08-06T10:15:00Z",
    "updatedAt": "2025-08-10T12:00:00Z",
    "sourceJobs": ["uuid"],           // ingestion jobIds that touched it
    "sourceChapters": ["uuid"]        // chapters contributing evidence
  },

  "schemaVersion": "1.0"
}
```

### Minimal Representation (for lists)

Return a trimmed projection by default:

```json
{
  "id": "uuid",
  "slug": "string",
  "type": "character",
  "name": "string",
  "universe": "string",
  "series": "string|null",
  "summary": "string|null",
  "stats": { "mentions": 12, "relations": 3 }
}
```

---

## Type-Specific `properties` Examples

### Character

```json
{
  "properties": {
    "species": "Human",
    "birthName": "string|null",
    "titles": ["string"],
    "affiliations": ["uuid"],            // org entityIds
    "abilities": ["Allomancy: Pewter", "Allomancy: Tin"],
    "homeLocationId": "uuid|null",       // location entityId
    "lifeStatus": "alive|deceased|unknown",
    "age": "string|number|null",         // flexible for fantasy timelines
    "appearance": {
      "eyeColor": "string|null",
      "hairColor": "string|null",
      "notableMarks": ["string"]
    }
  }
}
```

### Location

```json
{
  "properties": {
    "locationType": "city|region|building|realm|planet|other",
    "parentLocationId": "uuid|null",
    "world": "string|null",
    "coordinates": { "lat": null, "lon": null },  // keep null if not applicable
    "aliasesLocal": ["string"]
  }
}
```

### Organization

```json
{
  "properties": {
    "orgType": "crew|guild|army|nobleHouse|other",
    "leaders": ["uuid"],                  // character entityIds
    "members": ["uuid"],
    "headquartersId": "uuid|null",
    "founding": { "era": "string|null", "chapterId": "uuid|null" }
  }
}
```

### Item / Artifact

```json
{
  "properties": {
    "itemType": "weapon|relic|book|artifact|other",
    "ownerIds": ["uuid"],
    "abilities": ["string"],
    "origin": "string|null",
    "material": "string|null"
  }
}
```

### Event

```json
{
  "properties": {
    "eventType": "battle|meeting|journey|other",
    "participants": ["uuid"],             // entities involved
    "locationId": "uuid|null",
    "chapterId": "uuid|null",
    "sequenceOrder": 102                  // timeline order within scope
  }
}
```

---

## Notes for API Usage

* **Projection & Expansion**

  * Lists return the **minimal** representation.
  * Use `fields=` to project specific fields.
  * Use `include=mentions,relations,context` to enrich the detail view (the base **Entity** object stays as defined above).
* **Stability**

  * `properties` is the only place type-specific keys live; adding new keys there is **non-breaking**.
* **IDs vs Slugs**

  * `id` is authoritative; `slug` is for human-readable routes within a universe.

---

## Example: Full Character Entity

```json
{
  "id": "e-123",
  "slug": "kelsier",
  "type": "character",
  "universe": "Cosmere",
  "series": "Mistborn",
  "name": "Kelsier",
  "aliases": ["The Survivor of Hathsin"],
  "summary": "Charismatic crew leader and symbol of skaa rebellion.",
  "status": "canonical",
  "canonicalConfidence": 0.94,
  "disambiguation": null,
  "properties": {
    "species": "Human",
    "titles": ["The Survivor"],
    "affiliations": ["org-crew-1"],
    "abilities": ["Allomancy: Pewter", "Allomancy: Tin"],
    "homeLocationId": "loc-luthadel",
    "lifeStatus": "deceased",
    "appearance": { "eyeColor": null, "hairColor": null, "notableMarks": ["arm scars"] }
  },
  "context": {
    "firstSeen": { "chapterId": "c-1", "chapterTitle": "Prologue", "order": 1 },
    "lastSeen":  { "chapterId": "c-27", "chapterTitle": "Epilogue", "order": 27 }
  },
  "stats": { "mentions": 312, "relations": 27, "chaptersAppearedIn": 19, "evidenceCount": 47 },
  "tags": ["rebellion", "mistborn"],
  "provenance": {
    "createdAt": "2025-08-06T10:15:00Z",
    "updatedAt": "2025-08-10T12:00:00Z",
    "sourceJobs": ["job-abc", "job-def"],
    "sourceChapters": ["c-1", "c-5", "c-12"]
  },
  "schemaVersion": "1.0"
}
```

## Example: Full Location Entity

```json
{
  "id": "loc-luthadel",
  "slug": "luthadel",
  "type": "location",
  "universe": "Cosmere",
  "series": "Mistborn",
  "name": "Luthadel",
  "aliases": [],
  "summary": "Capital of the Final Empire.",
  "status": "canonical",
  "canonicalConfidence": 0.97,
  "disambiguation": null,
  "properties": {
    "locationType": "city",
    "parentLocationId": null,
    "world": "Scadrial",
    "coordinates": { "lat": null, "lon": null }
  },
  "identifiers": {},
  "context": {
    "firstSeen": { "chapterId": "c-2", "chapterTitle": "Arrival", "order": 2 },
    "lastSeen":  { "chapterId": "c-24", "chapterTitle": "Aftermath", "order": 24 }
  },
  "stats": { "mentions": 198, "relations": 41, "chaptersAppearedIn": 16, "evidenceCount": 33 },
  "tags": ["capital"],
  "provenance": {
    "createdAt": "2025-08-06T10:16:00Z",
    "updatedAt": "2025-08-10T12:05:00Z",
    "sourceJobs": ["job-ghi"],
    "sourceChapters": ["c-2", "c-9", "c-13"]
  },
  "schemaVersion": "1.0"
}
```
