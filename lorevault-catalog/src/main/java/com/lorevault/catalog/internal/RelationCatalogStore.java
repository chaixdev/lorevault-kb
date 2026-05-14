package com.lorevault.catalog.internal;

import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationQuery;

import java.util.Optional;

interface RelationCatalogStore {
    Optional<RelationCatalogDefinition> findByDefinitionKey(String definitionKey);
    Optional<RelationCatalogDefinition> findById(RelationCatalogId id);
    RelationCatalogDefinition create(RelationQuery query);
}
