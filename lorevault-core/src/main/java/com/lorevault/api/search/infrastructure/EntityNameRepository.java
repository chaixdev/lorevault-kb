package com.lorevault.api.search.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads all known entity display names (individuals and locations) from Neo4j.
 *
 * <p>Results are used to seed the {@link KnownEntityTrie} at startup and on refresh.</p>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
class EntityNameRepository {

    private final Neo4jClient neo4jClient;

    /**
     * Returns all distinct display names for {@code IndividualMention} nodes.
     */
    Collection<String> loadIndividualNames() {
        return loadDistinctNames("IndividualMention");
    }

    /**
     * Returns all distinct display names for {@code LocationMention} nodes.
     */
    Collection<String> loadLocationNames() {
        return loadDistinctNames("LocationMention");
    }

    private Set<String> loadDistinctNames(String label) {
        try {
            Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            neo4jClient
                    .query("MATCH (n:" + label + ") WHERE n.displayName IS NOT NULL RETURN DISTINCT n.displayName AS name")
                    .fetchAs(String.class)
                    .mappedBy((ts, record) -> record.get("name").asString())
                    .all()
                    .stream()
                    .filter(n -> n != null && !n.isBlank())
                    .forEach(names::add);
            log.debug("Loaded {} distinct {} display names", names.size(), label);
            return names;
        } catch (Exception e) {
            log.warn("Failed to load {} names from Neo4j: {}", label, e.getMessage());
            return Set.of();
        }
    }
}
