package com.lorevault.catalog.internal;

import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationKindSignature;
import com.lorevault.catalog.RelationQuery;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for M0. Replaced by PostgresRelationCatalogStore in M1.
 */
class InMemoryRelationCatalogStore implements RelationCatalogStore {

    private final Map<String, RelationCatalogDefinition> byKey = new ConcurrentHashMap<>();
    private final Map<UUID, RelationCatalogDefinition> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<RelationCatalogDefinition> findByDefinitionKey(String definitionKey) {
        return Optional.ofNullable(byKey.get(definitionKey));
    }

    @Override
    public Optional<RelationCatalogDefinition> findById(RelationCatalogId id) {
        return Optional.ofNullable(byId.get(id.value()));
    }

    @Override
    public RelationCatalogDefinition create(RelationQuery query) {
        RelationCatalogId id = RelationCatalogId.random();
        Instant now = Instant.now();
        var def = new RelationCatalogDefinition(
            id,
            query.definitionKey(),
            query.rawName(),
            query.description(),
            query.subjectKind() != null && query.objectKind() != null
                ? List.of(new RelationKindSignature(query.subjectKind(), query.objectKind()))
                : List.of(),
            query.rawName() != null ? List.of(query.rawName()) : List.of(),
            now, now, now
        );
        byKey.put(def.definitionKey(), def);
        byId.put(def.id().value(), def);
        return def;
    }
}
