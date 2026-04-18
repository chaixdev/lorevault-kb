# Architecture Decisions

This directory contains accepted architectural decisions.

ADRs document moments where LoreVault reached a real fork in the road, chose one path, and wants to preserve the reasoning behind that choice.

## ADRs Are For

- recording decisions that were actually made
- preserving why one option was chosen over viable alternatives
- documenting lasting architectural constraints and trade-offs
- helping future contributors understand historical context without re-litigating settled choices

## ADRs Are Not For

- open-ended future guidance
- implementation walkthroughs
- speculative design sketches
- current mechanism explanations that belong in `../patterns/`

## Suggested Shape

Each ADR should be concise and answer:

1. What decision was made?
2. What options were considered?
3. Why was this option chosen?
4. What implications follow from the decision?

If the decision is later replaced, the ADR should remain as history and point to the newer record.

## Index

- [001 - Neo4j for graph and vectors](001-neo4j-for-graph-and-vectors.md)
- [002 - Keep and upgrade Spring AI](002-keep-and-upgrade-spring-ai.md)
- [003 - Prefer direct services over ports and mappers](003-prefer-direct-services-over-ports-and-mappers.md)
- [004 - Keep the event-driven ingestion pipeline](004-keep-the-event-driven-ingestion-pipeline.md)
- DEPRECATED: [005 - Move structured output from XML to JSON](005-move-structured-output-from-xml-to-json.md) NOT ADOPTED. IT AINT BROKE,DONT FIX IT.
- [006 - Spoiler-aware search design](006-spoiler-aware-search-design.md)
- [007 - Adopt scoped identity ladder](007-adopt-scoped-identity-ladder.md)
- [008 - Define ingestion completion across parallel branches](008-define-ingestion-completion-across-parallel-branches.md)
- [009 - Structured logging philosophy](009-structured-logging-philosophy.md)
