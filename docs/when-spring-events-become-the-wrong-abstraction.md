# When Spring ApplicationEvents Become the Wrong Abstraction

*Lessons from replacing an event-driven pipeline with a state machine*

---

I built an ingestion pipeline. Thirteen stages — scene segmentation, chunking, embedding, entity resolution, book-level reduction, a terminal fan-in barrier. Each stage ran asynchronously. Each stage published a domain event when it finished. The next stage listened for that event and fired.

It was idiomatic Spring. It compiled clean. It passed review.

It was also fundamentally broken.

The in-memory `ConcurrentHashMap` keeping track of which branches had completed wasn't the bug — it was the canary. The real problem was that I had used a *notification* mechanism as an *orchestration* framework, and the two are not the same thing.

---

## Where Spring Events Belong

Let me be clear: Spring ApplicationEvents are excellent. The framework nudges you toward them because they genuinely solve a whole class of problems. You just need to recognize which class.

### Linear chains with no branching

`A → B → C`. Each step finishes, publishes, the next step triggers. No fan-in. No barriers. No conditional branching. The implicit `@EventListener` chain is exactly right — simple, debuggable, no infrastructure.

### True pub/sub: one publisher, N unrelated subscribers

When a single event should trigger multiple *independent* reactions:

```java
// Publisher: "I finished chunking. I don't care who knows."
eventPublisher.publishEvent(new ChunkingCompletedEvent(...));

// Subscriber A: update metrics dashboard
// Subscriber B: write audit log entry
// Subscriber C: invalidate a cache
// Subscriber D: start the next pipeline step
```

This is the canonical use case. The publisher emits a fact. Subscribers react in parallel. No subscriber blocks another. No subscriber knows about another. The publisher doesn't import any subscriber's class. Clean architecture.

### Cross-module boundaries where importing would be worse

When `catalog` needs to tell `search` that a relation was created, but importing `SearchService` into the catalog module would create a dependency cycle:

```java
// catalog → shared event ← search
// catalog knows nothing about search, and vice versa
```

Events act as a dependency-inversion boundary. The shared event class lives in a common package. Both modules depend on the event, not on each other. This is the pattern Spring's event bus was designed for.

### Fire-and-forget instrumentation

Metrics, tracing, audit, logging — things where you want zero latency impact on the main flow and dropping an event is acceptable:

```java
eventPublisher.publishEvent(new StageTimingEvent(stage, elapsedMs));
// If MetricsService is down, the pipeline continues. Nobody cares.
```

---

## The Boundary

The trouble starts when you need *coordination between subscribers*. The moment one subscriber's execution depends on another subscriber having finished, you've crossed from pub/sub into orchestration. And ApplicationEvents have no opinion about orchestration.

Here's the checklist. If any of these are true, events are the wrong layer of abstraction:

| You need... | Why events can't do it |
|-------------|----------------------|
| **Fan-in barriers** — "start E only after B, C, *and* D all complete" | Events fire independently. There's no "wait for N events" primitive. You'll end up building a coordinator in memory. |
| **Ordered execution** — "C must run before D" | Events fire synchronously in the publisher's thread or asynchronously with no guarantee. Ordering between different publishers is undefined. |
| **Cascade invalidation** — "re-running B means D and E must also re-run" | Events are history. "B completed" is a fact, not a state. You can't un-publish it. |
| **State query** — "is the pipeline done? what stage is it at?" | Events are transient. There's no durable representation of "where are we?" unless you build one separately. |
| **Recovery** — "the JVM crashed after B finished but before C started" | The event was consumed and lost. There's no event log to replay from. The next handler never knew it was supposed to run. |
| **Conditional branching** — "if B succeeds go to C; if B fails go to X" | Events carry a result, but the dispatch logic is distributed across `@EventListener` annotations. The branch is implicit, not explicit. |

Every one of these requires a central entity that knows the full topology and can make decisions across subscribers. Events are inherently decentralized — that's their strength, and that's exactly what makes them wrong here.

---

## Why Spring Makes This Hard to See

The framework rewards the pattern:

```java
// This feels right. It compiles. It's in the Spring docs.
@EventListener
public void onSceneSegmentationComplete(SceneSegmentationCompletedEvent event) {
    // chunk text...
    eventPublisher.publishEvent(new ChunkingCompletedEvent(...));
}
```

You get compile-time type checking. IDE navigation works. The method signature is clean. Every tutorial shows something like this. Chain a few together and it looks like a pipeline — and in the simple case, it is.

Spring doesn't warn you when four stages become twelve and suddenly you've got a DAG with fan-in barriers. The framework has no opinion about orchestration complexity. It just gives you the pub/sub primitive and trusts you to know when you've outgrown it.

The hard-won lesson isn't "don't use ApplicationEvents." It's: **when your problem requires coordination between subscribers, you need an orchestrator *above* the events, not built *from* them.**

In my rewrite, the system still uses Spring events — but only two generic ones:

```java
StageTriggered   // coordinator → handler: "your turn"
StageCompleted   // handler → coordinator: "done, here's the result"
```

The events became the communication primitive between the coordinator and handlers. The coordinator — backed by durable state in the graph database — became the topology-aware entity that knows what runs when, what blocks what, and how to recover. The events stopped pretending to be the orchestrator and became what they're actually good at: notification.

---

## A Practical Heuristic

Before wiring a new stage with `@EventListener`, ask yourself one question:

> "Does the downstream handler need to know that the **upstream handler specifically** finished, or does it only need to know that **its trigger condition is met**?"

If the answer is the former, events are fine — the publisher's identity matters, and coupling is acceptable. If the answer is the latter, you've got an orchestration problem and events are just the transport layer, not the solution.

Your `ConcurrentHashMap` isn't the canary. It's the fire.

---

*May 2026 — a reflection from the LoreVault ingestion pipeline rebuild*
