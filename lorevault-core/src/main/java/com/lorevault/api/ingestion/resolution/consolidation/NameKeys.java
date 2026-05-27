package com.lorevault.api.ingestion.resolution.consolidation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shared key extraction for name-based entity consolidation.
 *
 * <p>Derives identity keys from a normalized name and optional aliases.
 * Every entity type uses the same key derivation:
 * {@code {normalizedName} ∪ {normalized(alias) for each alias}}.
 *
 * <p>Blank and null values are filtered at the individual key level —
 * the returned set may be empty, which tells {@link ConsolidationEngine}
 * to skip that source entirely.
 */
public final class NameKeys {

    private NameKeys() {
        // static utility
    }

    /**
     * Derive identity keys from a normalized name and alias list.
     *
     * @param normalizedName  the primary normalized name (may be null)
     * @param aliases         optional aliases (may be null or empty)
     * @return a non-null, possibly empty set of identity key strings
     */
    public static Set<String> from(String normalizedName, Collection<String> aliases) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addKey(keys, normalizedName);
        if (aliases != null) {
            for (String alias : aliases) {
                addKey(keys, normalizeName(alias));
            }
        }
        return keys;
    }

    /**
     * Derive identity keys from a normalized name only.
     *
     * @param normalizedName  the primary normalized name (may be null)
     * @return a non-null, possibly empty set of identity key strings
     */
    public static Set<String> from(String normalizedName) {
        return from(normalizedName, null);
    }

    /**
     * Normalize a raw name value for use as an identity key.
     *
     * @param value  the raw name (may be null)
     * @return the trimmed, collapsed-whitespace, lowercased name;
     *         null if the result is blank or the input was null
     */
    public static String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ").toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private static void addKey(Set<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key);
        }
    }
}
