package com.lorevault.catalog;

import java.util.Optional;

public interface RelationCatalogService {

    /**
     * Resolve a relation query to a catalog definition.
     *
     * Three-tier matching: exact match on definitionKey → semantic pgvector
     * match → create new definition if nothing matches. Definition metadata is
     * intentionally first-write-wins; later observations refresh last-seen
     * metadata but do not rewrite the original display name or description.
     *
     * @param query the relation to resolve
     * @return the matched (or newly created) catalog definition
     */
    RelationCatalogDefinition resolve(RelationQuery query);

    /**
     * Find a catalog definition by its stable identity.
     */
    Optional<RelationCatalogDefinition> findByKey(RelationCatalogId id);

    /**
     * Find a catalog definition by its exact-match lookup key.
     */
    Optional<RelationCatalogDefinition> findByDefinitionKey(String definitionKey);
}
