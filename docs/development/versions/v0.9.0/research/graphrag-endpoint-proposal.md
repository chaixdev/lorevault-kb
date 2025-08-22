# GraphRAG Endpoint Proposal (Research)

Status: Draft (research-only)

Purpose
Propose a new endpoint `/api/query/ask/graphrag` to demonstrate incremental improvements in graph-aware, timeline-ordered answers without changing existing QA endpoints.

Endpoint

- Method: POST
- Path: `/api/query/ask/graphrag`
- Status: Target 0.8.6 (research handoff)

Request

```json
{
  "question": "What happened so far?",
  "filters": {
    "universe": "Cosmere",
    "series": "Mistborn",
    "bookNumber": 1,
    "uptoChapter": 15
  },
  "includeEvidence": false,
  "topKEvents": 50
}
```

Notes

- uptoChapter enforces chapter-level spoiler gating
- topKEvents caps context size; Events ordered by temporal edges then sceneIndex
- includeEvidence toggles edge rationales/offsets in response

Response

```json
{
  "answer": "So far, ...",
  "timeline": [
    { "eventId": "e1", "title": "...", "description": "...", "chapterNumber": 1, "sceneIndex": 1 }
  ],
  "evidence": [
    { "fromEventId": "e1", "toEventId": "e2", "temporalRelation": "MEETS", "certainty": "HEURISTIC", "rationale": "Chapter sequence" }
  ],
  "metadata": {
    "question": "What happened so far?",
    "uptoChapter": 15,
    "usedEvents": 32,
    "processingTimeMs": 1120
  }
}
```

Implementation notes

- Retrieval composes ordered Events up to chapter N using TEMPORAL edges (fallback sceneIndex)
- Summarization runs over Event descriptions/snippets; citations optional
- No feature flags; availability controlled by deployment branch/environment
