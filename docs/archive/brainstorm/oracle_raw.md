Bottom line: The foundation is sound, but the concept currently mixes a practical product model with a research-grade knowledge-representation system. For a one-person team, the winning path is: keep the sparse event backbone, add a small set of high-value entity types next, store simple evidence-bearing claims, and drop most of the ontology/governance machinery until real query pain proves it is needed.
From the current code/docs: Scene already doubles as Event (Scene implements Event, :Scene:Event queries, event indexes in Neo4jSchemaInitializer), so temporal groundwork is real. By contrast, claims are still only a three-bin schema placeholder, and there is no implemented catalog, entity resolution, or confidence aggregation pipeline.
A. Model coherence and major gaps
The six entity kinds are a reasonable upper ontology for fiction. I would keep them. They are broad enough to model most narrative worlds without exploding the root taxonomy.
The weakness is exactly where you suspected: Concept is a junk drawer unless you constrain it. Species-as-Concept is fine; so are materials, roles, ideologies, magic systems, and social classes. But if Concept becomes the fallback for “anything unclear,” your graph will slowly lose semantic sharpness. The fix is not more root kinds; it is mandatory subtyping/tagging inside Concept (species, material, role, belief, power, etc.) and strict rules about when something is a Concept versus an Object, Collective, or Event.
The three-bin model is coherent enough. The four-bin model is not worth preserving. Ability should stay collapsed into Ascription or Relation; it adds surface area without adding much retrieval value. In practice, “can fly,” “is telepathic,” and “can wield shardblades” are just properties or capability relations with qualifiers.
The bigger conceptual gap is not the bins; it is fact scope. Many fiction facts are not timeless binary truths:
- “X is king of Y” is time-bounded.
- “X believes Y is dead” is viewpoint-bounded.
- “X appears weak” is perception-bounded.
- “X was in city Z during event E” is event-scoped.
  Your current concept talks a lot about provenance and publication coordinates, but it under-specifies valid-time / event anchoring for claims. pubCoords tells you when the reader learns something, not necessarily when it is true in-world.
  Other under-thought areas:
- Identity policy: aliases, titles, disguises, reincarnations, split identities, merged beings.
- N-ary facts: many facts are not clean binary edges without awkward qualifier bags.
- Evaluation: there is no clear gold set for “good extraction” or “useful query answer.”
- Query contract: what exact user questions must this model answer better than chunk-only RAG?
  B. LLM extraction realism
  Current LLMs can extract explicit, scene-local entities and direct relations from prose reasonably well. They are much less reliable at:
- calibrated numeric certainty
- source attribution in dialogue / free indirect discourse
- polarity vs absence
- implicit comparisons
- consistent qualifier structure across scenes/books
  The biggest trap is false precision. A model can output certainty: 0.73, but that does not mean it knows the difference between 0.73 and 0.58. I would not trust model-generated floats here. Use a small enum or 3-level bucket (explicit, implied, speculative) and keep the original evidence span.
  The two-phase vocabulary idea is realistic only if phase 1 stays simple. “Extract a plain-language relation/property description plus evidence span” is plausible. “Extract rich structured claims with reliable source, polarity, qualifiers, and ontology-ready wording” is where quality drops fast. The model should not be asked to both understand prose and behave like a taxonomy steward.
  So yes: extract-then-map is the right shape, but only with a very small target vocabulary and conservative acceptance rules. If mapping fails often, that is useful signal; it means your catalog is not mature enough yet.
  C. Catalog feasibility
  A one-person team does not need a catalog microservice, hybrid retrieval layer, and curation UI. That is premature architecture.
  You do need a catalog conceptually, but it should start as boring in-app data: a small table/file-backed registry of relation types, core properties, and maybe concept subtypes. Keep it inside the Spring app, versioned in the repo, and editable without new infrastructure.
  BM25 + vector hybrid search is also overkill initially. If you have 30–80 relation/property entries, exact match + aliases + simple fuzzy matching is enough. Embeddings become useful only when the catalog is large enough that humans cannot manage the drift manually.
  The real feasibility rule is simple: if the catalog is small enough to review in a markdown file, it is too small for a service.
  D. Confidence formula realism
  The proposed formula is elegant and premature.
  With one fictional universe, you will not have enough labeled review data to tune α, β, γ, source reliabilities, projection thresholds, and evidence-quality penalties in a way that is defensible. You will have numbers, but they will mostly encode your intuitions, not validated behavior.
  Start with cheap, interpretable signals:
- support count
- contradiction count
- strongest source tier
- earliest evidence
- explicit vs implied evidence class
  Then map those into coarse statuses like supported, contested, weak, denied. That is testable by spot review. A sigmoid-based endorsement model can come later if you accumulate enough adjudicated examples to justify it.
  In short: confidence math should follow evaluation, not precede it.
  E. Graph explosion risk
  For one 20-book series, the raw scale is not the main problem. Even a fairly rich ingestion would likely land in the range of:
- low thousands of scenes
- tens of thousands of chunks
- hundreds to low thousands of canonical entities
- tens to low hundreds of thousands of raw claims
- similar order of magnitude for evidence/projection edges if you stay sparse
  Neo4j can handle that.
  The real risk is write amplification and semantic sprawl, not absolute size. If every claim becomes a node, plus support/deny aggregates, plus projected edges, plus entity-resolution evidence, plus temporal edges, you create a graph that is technically manageable but mentally expensive and operationally noisy.
  Neo4j is still the right home for:
- content hierarchy
- scene/event backbone
- projected entity/event relations
- spoiler-aware query traversal
  It is a tolerable home for raw claims at MVP scale. But raw claims should stay off the hot query path. The moment every user query has to traverse provenance-heavy subgraphs, the model stops paying for itself.
  F. Event DAG sparsity vs utility
  The sparse DAG idea is good. The current implementation already reflects the right instinct: local edges, no dense transitive closure, scene-as-event, certainty carried on temporal relationships.
  But triad-only + retrieval-time Allen composition is not enough for the user question you gave: “What happened to character X between books 3 and 7?” That query is mostly not a temporal-algebra problem. It is an entity-linked event retrieval problem:
1. which scenes/events involve X?
2. what happened in those scenes?
3. how should those events be ordered for the reader?
   Without entity↔scene/event links and event summaries, the DAG adds little. In practice, the best answer path is usually:
- filter by publication range
- retrieve scenes/events involving X
- use explicit temporal edges where available
- otherwise fall back to publication order / scene order
  That is much more robust than betting the product on long-range Allen composition over uncertain edges.
  Landmarks and arcs are especially suspect here. They are analytically nice, but they are not the next thing users need.
  G. What can be killed
  Kill now, probably forever:
- CDSL. The docs already tell you it may be useless. They are right.
- Ability as a separate bin. Keep it collapsed.
- SubstanceScore as a standalone triage subsystem. It adds tuning burden and risks dropping subtle but important evidence.
  Kill until proven necessary:
- Catalog microservice
- Curation UI
- Formula-heavy confidence scoring
- Landmark/Arc as first-class node types
  If you need review, do manual review first. If you need catalogs, keep them in-process first. If you need arcs later, derive them from event clusters instead of enshrining them up front.
  H. Recommended staging
  Your proposed order is close only in the first step.
  What I would do instead:
1. Stabilize scene-as-event, not “events” in the abstract. You already have this in code; formalize it and keep Scene as the default Event representation.
2. Add high-value entities next: Individual, Location, Collective first. Delay Object and most of Concept until you have concrete query demand.
3. Link entities to scenes/chunks before richer claims. That gives you immediate value for “where does X appear,” “who is in this scene,” and event participation.
4. Implement simple raw claim persistence after entity extraction. Use the existing three-bin schema, but only extract ascriptions and relations initially; comparisons can wait.
5. Project a very small set of useful edges. For example: participated_in, located_in, member_of, maybe a few durable properties.
6. Only then deepen temporal modeling where real queries fail. DAG enrichment should come after entity-linked retrieval proves the gap.
   So: Events first, narrowly yes. DAG enrichment second, no. Other entities should come before sophisticated claims math, but simple claim storage should come before any grand temporal enrichment.
   Action plan
1. Treat Scene-as-Event as the canonical MVP event model and stop designing parallel event machinery for now. Add separate Event nodes only for cross-scene or clearly reusable events later.
2. Implement entity extraction for Individual, Location, and Collective first. Require evidence spans and scene/chunk links for every extracted entity mention.
3. Add a minimal identity layer: canonical entity, aliases, and manual merge/split support. Do not attempt full autonomous entity resolution across books on day one.
4. Persist raw claims using the existing three-bin schema, but start with only ascription and relation. Store source role, evidence span, and optional coarse certainty bucket.
5. Keep a tiny in-app catalog for core relation/property IDs. Review provisionals manually instead of building a service or UI.
6. Project only explicit, high-confidence edges onto the main graph. Keep raw claims append-only and off the primary query path.
7. Revisit richer temporal reasoning only after entity-linked event retrieval is working and users still cannot answer chronology-heavy questions. That is the point to test whether landmarks/arcs are actually needed.
   Effort estimate: Medium for the recommended next slice; Large for the original concept as written.
   Why this approach
- It matches what the code already has instead of starting a second architecture beside it.
- It delivers user-visible value faster: character/event/location queries improve immediately.
- It narrows the LLM problem to things models can do tolerably well.
- It preserves a path to richer claims and confidence later without locking you into speculative machinery now.
  Watch out for
- Identity mistakes compound. A bad merge is worse than a missed entity; bias toward under-merging.
- Publication time and story time are different axes. Keep them separate in the model and in query semantics.
- Projection can destroy trust. If weak claims become graph facts too early, users will stop believing the system.
  Escalation triggers
- If provisionals start piling up faster than you can review them, the catalog needs stronger tooling.
- If users need in-universe chronology more than spoiler-aware publication order, temporal reasoning deserves a second phase.
- If provenance-heavy claim storage starts slowing core queries, keep Neo4j for projected graph and move evidence off the hot path later.

Bottom line: After re-checking the work against the repo docs and code, the conclusion holds: LoreVault has a strong practical base, but the envisioned concept goes well beyond what a one-person team should build next. The right direction is to consolidate Scene-as-Event, add a small number of high-value entity types plus simple evidence storage, and permanently drop the parts that behave more like ontology research than product leverage.
Action plan
1. Lock the near-term model to Scene as the primary event carrier. The code already does this via Scene implements Event, :Scene:Event queries, and event indexes.
2. Implement Individual, Location, and Collective extraction before broader taxonomy work. These are the entity types most likely to improve actual user questions.
3. Add simple raw claim persistence using the current three-bin schema. Keep it append-only and evidence-backed, but avoid aggregation math at first.
4. Replace the catalog-service idea with a tiny in-app registry of core property/relation IDs. Manual review beats service architecture here.
5. Project only a small set of explicit, high-value edges into Neo4j. Keep raw claims off the main query path.
6. Revisit richer temporal reasoning only after entity-linked event retrieval is working. Do not invest in arcs/landmarks/CDSL unless real usage forces it.
   Effort estimate: Medium
   Why this approach
- It aligns with what is already real in the code rather than the full research model.
- It pushes effort toward user-visible retrieval gains instead of ontology scaffolding.
- It keeps the architecture simple enough for one person to reason about and maintain.
- It preserves the option to grow into richer claims later without paying the cost now.
  Watch out for
- Under-specified identity handling will hurt more than a thin taxonomy.
- Publication order and story-time order need to stay distinct in both storage and query behavior.
- Confidence numbers will look rigorous before they are actually trustworthy.
  Optional future considerations
- If provisionals become a real operational burden, then add lightweight catalog tooling inside the app.
- If chronology-heavy questions become central, then evaluate whether separate non-scene Event nodes are justified.