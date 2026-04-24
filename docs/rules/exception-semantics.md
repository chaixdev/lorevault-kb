# Exception Semantics

**Status:** Active

## Core Rule

Expected failure modes in business workflows must not be represented only as generic `RuntimeException`, `IllegalStateException`, or message text.

If the system already knows a failure mode is meaningful and handled — including retryable LLM variance, domain mismatch, or other expected-but-unsuccessful business outcomes — it must be surfaced as a meaningful business exception or structured failure type.

## Why This Rule Exists

Generic exceptions erase intent.

That causes predictable problems:

- expected business outcomes can look like unexpected technical crashes
- retryability gets inferred from message strings instead of stable semantics
- operators and future maintainers can misread severity
- downstream status/failure handling becomes brittle and harder to audit

LoreVault already has a better precedent in parts of the ingestion pipeline:

- typed exception
- structured failure payload
- explicit retryability/status propagation

New work should follow that direction instead of adding more string-driven classification.

## Rules

### Rule 1 — Use business exceptions for expected business failures

Use a typed business exception (or equivalent structured failure type) when a failure is:

- foreseeable in normal operation
- meaningful to the business workflow
- intentionally handled by the caller or pipeline
- important to classify as retryable or non-retryable

Examples:

- LLM output variance that can fail local validation or localization
- domain mismatch or unsupported-but-expected business state
- partial pipeline outcomes that should become structured stage failures

### Rule 2 — Do not encode failure meaning only in message text

Message strings may help operators, but they must not be the only place where failure semantics live.

Avoid patterns where code decides behavior by checking whether the exception message contains specific phrases unless there is no better boundary available yet.

If behavior differs based on failure meaning, that meaning should live in:

- the exception type
- structured failure metadata
- or an explicit classification object

### Rule 3 — Preserve retryability as an explicit semantic

Retryability is separate from severity.

An expected business failure may still be retryable.

Do not assume:

- business exception = non-retryable
- runtime exception = severe/unexpected

The code must classify retryability deliberately and consistently.

### Rule 4 — Keep unexpected technical defects distinct

Not every failure should become a business exception.

Keep generic/unexpected technical failures as defects when they represent:

- programmer mistakes
- violated invariants that should never occur in normal workflow
- infrastructure failures with no domain-level meaning yet
- adapter/bootstrap failures that are not business outcomes

Do not convert genuine defects into polite domain wrappers just to reduce log noise.

### Rule 5 — Prefer structured failure payloads at workflow boundaries

At ingestion or other workflow boundaries, meaningful failures should preserve structured context where appropriate, such as:

- failure code
- stage
- exception type
- retryable flag
- safe diagnostic details

If a boundary already persists or emits structured failure information, new business exceptions should integrate with that mechanism instead of bypassing it.

## Practical Guidance

Before introducing a generic catch/rethrow, ask:

1. Is this a known failure mode of the business workflow?
2. Will the caller handle or classify it differently from a defect?
3. Does retryability matter here?
4. Will operators or downstream status handling benefit from a stable failure code/type?

If the answer to any of those is yes, prefer a meaningful business exception or structured failure model.

## Current Precedent In LoreVault

Use these as the baseline pattern to extend rather than inventing ad hoc wrappers:

- `TriadAnalysisException`
- `SceneDetectionException`
- `SemanticSearchException`
- `EntityLookupException`
- `ChapterSubmissionLookupException`
- `EmbeddingGenerationException`
- `IngestionFailure`
- `IngestionFailureCarrier`
- `PipelineStageSupport`
- `IngestionFailedEvent`

Current boundary application examples:

- chapter-submission lookup failures fail closed instead of degrading into duplicate-work creation
- search/entity-lookup backend failures stay distinct from legitimate empty/no-evidence retrieval outcomes
- JSON query endpoints may map typed retrieval failures to service-unavailable responses while preserving generic defects as `500`
- HTMX/UI query endpoints may render typed retrieval failures as user-facing error fragments without pretending the request succeeded semantically

## Anti-Patterns

- `throw new RuntimeException("expected business failure...")`
- rewrapping a typed business failure immediately as a generic runtime error
- using `exception.getMessage().contains(...)` as the primary semantic classifier when a typed boundary is available
- collapsing backend failure into empty/false result without deciding whether that means “no result” or “business/infrastructure failure”

## Related Work

- The scene-localization anchor-mismatch path is a concrete example of this rule.
- Similar future audit work should follow this rule rather than expanding the rule into ticket tracking.
