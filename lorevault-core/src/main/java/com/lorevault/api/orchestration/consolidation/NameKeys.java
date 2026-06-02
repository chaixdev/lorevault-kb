package com.lorevault.api.orchestration.consolidation;

import com.lorevault.api.common.NameNormalizer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Identity key extraction for entity consolidation.
 *
 * <p>Produces a set of normalized identity keys from a source's primary name
 * and its aliases. Two sources that share any key are considered the same
 * entity for connected-components clustering.
 *
 * <p>Keys are normalized via {@link NameNormalizer#normalize(String)}. Null and
 * blank values are excluded from the result.
 */
public final class NameKeys {

    private NameKeys() {}

    /**
     * Extract identity keys from a normalized name and a collection of aliases.
     *
     * <p>The result includes the normalizedName (if non-blank) plus each
     * normalized alias (if non-blank). This enables transitive merging:
     * if source A has key "kevin jenkins" and source B has keys
     * {"kevin jenkins", "jenkins"}, they share "kevin jenkins" and merge.
     *
     * @param normalizedName  the primary normalized name (already lowercased, trimmed)
     * @param aliases          raw aliases to normalize and include
     * @return set of identity keys, empty if normalizedName is blank and no valid aliases
     */
    public static Set<String> from(String normalizedName, Collection<String> aliases) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addKey(keys, normalizedName);
        if (aliases != null) {
            for (String alias : aliases) {
                addKey(keys, NameNormalizer.normalize(alias));
            }
        }
        return keys;
    }

    /**
     * Extract identity keys from a normalized name only (no aliases).
     *
     * @param normalizedName  the primary normalized name
     * @return set containing the normalizedName if non-blank, empty otherwise
     */
    public static Set<String> from(String normalizedName) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addKey(keys, normalizedName);
        return keys;
    }

    private static void addKey(Set<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key);
        }
    }
}