// Phase 0 normalization for Scene-as-Event and TEMPORAL relationship properties.
// Idempotent and safe to rerun.

// Ensure all Scene nodes carry the Event label expected by timeline read paths.
MATCH (s:Scene)
WHERE NOT s:Event
SET s:Event;

// Normalize legacy TEMPORAL relationship property names.
// Legacy writes used t.type / t.confidence.
// Canonical properties are t.temporalRelation / t.certainty / t.weight.
MATCH ()-[t:TEMPORAL]->()
FOREACH (_ IN CASE WHEN t.type IS NOT NULL AND t.temporalRelation IS NULL THEN [1] ELSE [] END |
  SET t.temporalRelation = t.type
)
FOREACH (_ IN CASE WHEN t.confidence IS NOT NULL AND t.certainty IS NULL THEN [1] ELSE [] END |
  SET t.certainty = 'Heuristic'
)
FOREACH (_ IN CASE WHEN t.confidence IS NOT NULL AND t.weight IS NULL THEN [1] ELSE [] END |
  SET t.weight = toFloat(t.confidence)
)
REMOVE t.type, t.confidence;
