package com.lorevault.api.search;

/**
 * Classified intent of a Q&A question, used to route to the appropriate retrieval strategy.
 *
 * <ul>
 *   <li>{@link #ENTITY_LOOKUP} — question targets a specific named individual or location
 *       (e.g. "Who is Vin?", "Describe Luthadel"). Routes to the Cypher template lane.</li>
 *   <li>{@link #NARRATIVE_QA} — question asks about events, scenes, or story content.
 *       Routes to the vector-seeded graph expansion lane.</li>
 *   <li>{@link #AMBIGUOUS} — intent is unclear; falls through to the narrative QA lane.</li>
 * </ul>
 */
public enum QuestionIntent {
    ENTITY_LOOKUP,
    NARRATIVE_QA,
    AMBIGUOUS
}
