# LoreVault API Curl Catalog

Complete, copy-pasteable curl examples for every ingestion endpoint.

## Prerequisites

```bash
# Start Neo4j and the API
docker-compose up -d neo4j
./scripts/dev-api.sh start

# Verify health
curl -s localhost:18080/actuator/health | jq .
```

## Library Commands

### Create Universe

```bash
curl -s -X POST localhost:18080/api/command/library/create-universe \
  -H 'Content-Type: application/json' \
  -d '{"name":"My Universe"}'
# → {"universeId":"...","name":"...","slug":"...","created":true,...}
```

### Create Series

```bash
curl -s -X POST localhost:18080/api/command/library/create-series \
  -H 'Content-Type: application/json' \
  -d '{"universeId":"UNIVERSE_ID","name":"My Series"}'
# → {"seriesId":"...","universeId":"...","name":"...","created":true,...}
```

### Create Book

```bash
curl -s -X POST localhost:18080/api/command/library/create-book \
  -H 'Content-Type: application/json' \
  -d '{"universeId":"UNIVERSE_ID","seriesId":"SERIES_ID","title":"My Book","bookNumber":1}'
# → {"bookId":"...","universeId":"...","title":"...","bookNumber":1,"created":true,...}
```

## Ingestion: Full Pipeline

### Submit Chapter (triggers full async pipeline)

```bash
curl -s -X POST localhost:18080/api/command/ingest \
  -F "file=@chapter.txt" \
  -F "bookId=BOOK_ID" \
  -F "chapterNumber=1" \
  -F "chapterTitle=Chapter One"
# → {"jobId":"...","chapterId":"...","message":"Chapter submitted successfully for processing"}
```

## Ingestion: Prepare (Stage-by-Stage)

### Prepare Chapter (no pipeline trigger)

```bash
curl -s -X POST localhost:18080/api/command/ingest/prepare \
  -H 'Content-Type: application/json' \
  -d '{
    "bookId":"BOOK_ID",
    "chapterNumber":1,
    "chapterTitle":"The Kevin Jenkins Experience",
    "chapterText":"It was a dark and stormy night..."
  }'
# → {"jobId":"...","chapterId":"..."}
```

## Ingestion: Chapter-Scoped Stages

All chapter-scoped stage endpoints accept optional `fireEvents` and `jobId` query parameters:

- `fireEvents=true` — publish domain events after stage completion (triggers downstream pipeline)
- `fireEvents=false` (default) — run stage in isolation, no cascade
- `jobId=UUID` — track stage execution within an ingestion job

### Detect Scenes

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/CHAPTER_ID/detect-scenes"
# → {"step":"SCENE_DETECTION","scope":"chapter","scopeId":"...","success":true,
#    "summary":"Detected 5 scenes","durationMs":37470,"retryable":false,
#    "counts":{"scenesDetected":5,"relationClaims":10}}

# With cascade:
curl -s -X POST "localhost:18080/api/command/ingest/chapters/CHAPTER_ID/detect-scenes?fireEvents=true&jobId=JOB_ID"
```

### Chunk

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/CHAPTER_ID/chunk"
# → {"step":"CHUNKING","scope":"chapter","scopeId":"...","success":true,
#    "summary":"Created 12 chunks from 5 scenes","durationMs":1500,"retryable":false,
#    "counts":{"chunksCreated":12}}
```

### Embed

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/CHAPTER_ID/embed"
# → {"step":"EMBEDDING","scope":"chapter","scopeId":"...","success":true,
#    "summary":"Generated embeddings for 12 chunks","durationMs":25000,"retryable":false,
#    "counts":{"embeddingsGenerated":12}}
```

### Resolve Individuals

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/CHAPTER_ID/resolve-individuals"
# → {"step":"INDIVIDUAL_RESOLUTION","scope":"chapter","scopeId":"...","success":true,
#    "summary":"Resolved 8 individuals from 15 mentions","durationMs":5000,"retryable":false,
#    "counts":{"rawIndividualsProcessed":15,"chapterIndividualsCreated":8}}
```

### Resolve Collectives

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/CHAPTER_ID/resolve-collectives"
# → {"step":"COLLECTIVE_RESOLUTION","scope":"chapter","scopeId":"...","success":true,
#    "summary":"Resolved 3 collectives from 7 mentions","durationMs":3000,"retryable":false,
#    "counts":{"rawCollectivesProcessed":7,"chapterCollectivesCreated":3}}
```

### Resolve Locations

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/CHAPTER_ID/resolve-locations"
# → {"step":"LOCATION_RESOLUTION","scope":"chapter","scopeId":"...","success":true,
#    "summary":"Resolved 4 locations from 9 mentions","durationMs":3500,"retryable":false,
#    "counts":{"rawLocationsProcessed":9,"chapterLocationsCreated":4}}
```

### Resolve Objects

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/CHAPTER_ID/resolve-objects"
# → {"step":"OBJECT_RESOLUTION","scope":"chapter","scopeId":"...","success":true,
#    "summary":"Resolved 2 objects from 6 mentions","durationMs":2800,"retryable":false,
#    "counts":{"rawObjectsProcessed":6,"chapterObjectsCreated":2}}
```

### Resolve Events

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/CHAPTER_ID/resolve-events"
# → {"step":"EVENT_RESOLUTION","scope":"chapter","scopeId":"...","success":true,
#    "summary":"Resolved 6 events from 20 mentions","durationMs":8000,"retryable":false,
#    "counts":{"rawMentionsProcessed":20,"chapterEventsCreated":6,"failedCorefWindowCount":0}}
```

## Ingestion: Book-Scoped Stages

Book-scoped stages reduce chapter-level entities to book-level entities. They accept the same `fireEvents` and `jobId` parameters.

### Reduce Individuals

```bash
curl -s -X POST "localhost:18080/api/command/ingest/books/BOOK_ID/reduce-individuals"
# → {"step":"BOOK_INDIVIDUAL_REDUCTION","scope":"book","scopeId":"...","success":true,
#    "summary":"Reduced 12 chapter individuals to 8 book individuals","durationMs":2000,"retryable":false,
#    "counts":{"chapterIndividualsProcessed":12,"bookIndividualsCreated":8}}
```

### Reduce Collectives

```bash
curl -s -X POST "localhost:18080/api/command/ingest/books/BOOK_ID/reduce-collectives"
# → {"step":"BOOK_COLLECTIVE_REDUCTION","scope":"book","scopeId":"...","success":true,
#    "summary":"Reduced 5 chapter collectives to 3 book collectives","durationMs":1800,"retryable":false,
#    "counts":{"chapterCollectivesProcessed":5,"bookCollectivesCreated":3}}
```

### Reduce Locations

```bash
curl -s -X POST "localhost:18080/api/command/ingest/books/BOOK_ID/reduce-locations"
# → {"step":"BOOK_LOCATION_REDUCTION","scope":"book","scopeId":"...","success":true,
#    "summary":"Reduced 6 chapter locations to 4 book locations","durationMs":1900,"retryable":false,
#    "counts":{"chapterLocationsProcessed":6,"bookLocationsCreated":4}}
```

### Reduce Objects

```bash
curl -s -X POST "localhost:18080/api/command/ingest/books/BOOK_ID/reduce-objects"
# → {"step":"BOOK_OBJECT_REDUCTION","scope":"book","scopeId":"...","success":true,
#    "summary":"Reduced 3 chapter objects to 2 book objects","durationMs":1700,"retryable":false,
#    "counts":{"chapterObjectsProcessed":3,"bookObjectsCreated":2}}
```

### Legacy URLs (307 Redirect)

The old `resolve-*` book-level URLs redirect to the new `reduce-*` URLs with a 307 Temporary Redirect:

```bash
curl -s -X POST "localhost:18080/api/command/ingest/books/BOOK_ID/resolve-individuals" -v 2>&1 | grep "< HTTP"
# → < HTTP/1.1 307 Temporary Redirect
```

Clients should update to use `/reduce-*` URLs directly.

## Ingestion: Rerun

### Rerun ANN for Event Embedding Candidates

```bash
curl -s -X POST "localhost:18080/api/command/ingest/events/rerun-ann?universeId=UNIVERSE_ID"
```

## Query: Stage Discoverability

### List Available Stages

```bash
curl -s localhost:18080/api/query/ingestion/stages | jq .
# → {
#     "stages": [
#       {"key":"detect-scenes","scope":"chapter","description":"Detect semantic scene boundaries in chapter text","prerequisites":[]},
#       {"key":"chunk","scope":"chapter","description":"Split detected scenes into text chunks for embedding","prerequisites":["detect-scenes"]},
#       {"key":"embed","scope":"chapter","description":"Generate vector embeddings for scene chunks","prerequisites":["chunk"]},
#       {"key":"resolve-individuals","scope":"chapter","description":"Resolve individual entity mentions across scenes","prerequisites":["detect-scenes"]},
#       {"key":"resolve-collectives","scope":"chapter","description":"Resolve collective entity mentions across scenes","prerequisites":["detect-scenes"]},
#       {"key":"resolve-locations","scope":"chapter","description":"Resolve location entity mentions across scenes","prerequisites":["detect-scenes"]},
#       {"key":"resolve-objects","scope":"chapter","description":"Resolve object entity mentions across scenes","prerequisites":["detect-scenes"]},
#       {"key":"resolve-events","scope":"chapter","description":"Resolve narrative events across scenes","prerequisites":["detect-scenes"]},
#       {"key":"reduce-individuals","scope":"book","description":"Reduce chapter-level individuals to book-level entities","prerequisites":["resolve-individuals"]},
#       {"key":"reduce-collectives","scope":"book","description":"Reduce chapter-level collectives to book-level entities","prerequisites":["resolve-collectives"]},
#       {"key":"reduce-locations","scope":"book","description":"Reduce chapter-level locations to book-level entities","prerequisites":["resolve-locations"]},
#       {"key":"reduce-objects","scope":"book","description":"Reduce chapter-level objects to book-level entities","prerequisites":["resolve-objects"]}
#     ]
#   }
```

## Query: Job Status

### Get Job Status

```bash
curl -s localhost:18080/api/query/jobs/JOB_ID | jq .
```

### List Jobs

```bash
curl -s "localhost:18080/api/query/jobs?limit=20&offset=0" | jq .
```

## Error Responses

### Invalid UUID (400)

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/not-a-uuid/detect-scenes"
# → {"error":{"code":"INVALID_CHAPTER_ID","message":"Chapter ID must be a valid UUID","details":{"chapterId":"not-a-uuid"}},"timestamp":"...","path":"/api/command/ingest/chapters/not-a-uuid/detect-scenes"}
```

### Not Found (404)

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/00000000-0000-0000-0000-000000000000/detect-scenes"
# → (empty 404 response)
```

### Stage Failed (200 with success=false)

# When a stage fails but the request was valid:
# → {"step":"SCENE_DETECTION","scope":"chapter","scopeId":"...","success":false,
#    "summary":"Scene detection failed: LLM API timeout after 60s","durationMs":60043,"retryable":true,
#    "counts":{}}
```

## Agentic Workflow Example

Complete stage-by-stage workflow for an agent driving ingestion:

```bash
# 1. Create library
UNIVERSE_ID=$(curl -s -X POST localhost:18080/api/command/library/universe \
  -H 'Content-Type: application/json' -d '{"name":"Test Universe"}' | jq -r '.id')

SERIES_ID=$(curl -s -X POST localhost:18080/api/command/library/series \
  -H 'Content-Type: application/json' \
  -d "{\"universeId\":\"$UNIVERSE_ID\",\"name\":\"Test Series\"}" | jq -r '.id')

BOOK_ID=$(curl -s -X POST localhost:18080/api/command/library/book \
  -H 'Content-Type: application/json' \
  -d "{\"universeId\":\"$UNIVERSE_ID\",\"seriesId\":\"$SERIES_ID\",\"title\":\"Test Book\",\"bookNumber\":1}" | jq -r '.id')

# 2. Prepare chapter (no pipeline trigger)
PREPARE_RESULT=$(curl -s -X POST localhost:18080/api/command/ingest/prepare \
  -H 'Content-Type: application/json' \
  -d "{\"bookId\":\"$BOOK_ID\",\"chapterNumber\":1,\"chapterTitle\":\"Chapter 1\",\"chapterText\":\"It was a dark and stormy night...\"}")
JOB_ID=$(echo $PREPARE_RESULT | jq -r '.jobId')
CHAPTER_ID=$(echo $PREPARE_RESULT | jq -r '.chapterId')

# 3. Discover available stages
curl -s localhost:18080/api/query/ingestion/stages | jq '.stages[] | .key'

# 4. Run scene detection (isolated, no cascade)
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/detect-scenes" | jq .

# 5. Inspect results in Neo4j
# MATCH (rc:RelationClaim) WHERE rc.chapterId = $CHAPTER_ID RETURN rc.relationName, rc.subjectName, rc.objectName

# 6. Run next stage when ready
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/resolve-individuals" | jq .

# 7. Or run a stage and let it cascade
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/detect-scenes?fireEvents=true&jobId=$JOB_ID" | jq .

# 8. Full pipeline (existing endpoint, unchanged)
curl -s -X POST localhost:18080/api/command/ingest \
  -F "bookId=$BOOK_ID" \
  -F "chapterNumber=1" \
  -F "file=@chapter.txt"
```