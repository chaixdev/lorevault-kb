package com.lorevault.catalog;

import java.util.Optional;

public interface RelationCatalogService {

    /**
     * Resolve a relation query to a catalog definition.
     *
     * Two-tier matching: exact match on definitionKey → create new definition
     * if nothing matches. Signature-based disambiguation is deferred to a
     * future milestone (embedding similarity via pgvector).
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
