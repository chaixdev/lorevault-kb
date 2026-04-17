# LV-084-1 Verification Checklist

This checklist helps you validate default temporal edges (MEETS) end-to-end.

## Prerequisites

- JDK 21
- Docker available (for Testcontainers)
- Neo4j Testcontainers will be started by tests automatically

## 1) Automated tests

- Run workspace task: Maven: Test
- Expected: All tests pass (including CrossChapterTemporalEdgesIntegrationTest and DefaultTemporalEdgeServiceIntegrationTest)

## 2) Manual graph inspection (optional)

If you run the app and ingest a book with chapters and scenes:

- Verify in-chapter default edges exist: N-1 edges per chapter with N scenes
- Verify cross-chapter edges exist: exactly one MEETS from last scene of chapter N to first of chapter N+1 when both exist

Use the verification queries from processes/lv-084-1-implementation-summary.md:

- Count in-chapter MEETS for a chapter
- Verify cross-chapter MEETS across adjacent chapters
- Sample MEETS edges and properties (type, confidence)

## 3) Idempotency

- Re-run ingestion for the same book (or re-trigger default edge creation)
- Expected: Edge counts remain unchanged; queries return the same counts

## 4) Expected properties

- All default MEETS edges have properties: type = 'HEURISTIC', confidence = 0.5

## Troubleshooting

- If counts look low, ensure scenes have valid sceneIndex and chapters have chapterNumber
- Ensure `Chapter-[:IN_BOOK]->Book` and `Chapter-[:HAS_SCENE]->Scene` links exist (ingestion should establish these)
- See test `CrossChapterTemporalEdgesIntegrationTest` for a reference fixture
