package com.lorevault.api.graph.timeline.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class CertaintyWeights {
    private static final Map<CertaintyLevel, Double> WEIGHTS;

    static {
        EnumMap<CertaintyLevel, Double> m = new EnumMap<>(CertaintyLevel.class);
        m.put(CertaintyLevel.EXPLICIT, 0.95);
        m.put(CertaintyLevel.STRONGLY_IMPLIED, 0.80);
        m.put(CertaintyLevel.WEAKLY_IMPLIED, 0.60);
        m.put(CertaintyLevel.HEURISTIC, 0.50);
        WEIGHTS = Collections.unmodifiableMap(m);
    }

    private CertaintyWeights() { }

    public static Map<CertaintyLevel, Double> weights() {
        return WEIGHTS;
    }

    public static double weightOf(CertaintyLevel level) {
        if (level == null) {
            // Default to HEURISTIC weight for unknown/null certainty levels
            // This handles the edge case where consecutive scene edges have no explicit certainty
            return WEIGHTS.get(CertaintyLevel.HEURISTIC);
        }
        
        Double w = WEIGHTS.get(level);
        if (w == null) {
            throw new IllegalArgumentException("No weight for certainty level: " + level);
        }
        return w;
    }
}
