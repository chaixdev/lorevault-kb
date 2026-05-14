package com.lorevault.catalog.internal;

import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationCatalogService;
import com.lorevault.catalog.RelationQuery;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No-op catalog service used when the catalog module is disabled.
 * All operations throw UnsupportedOperationException — callers should
 * use degradation mode (try/catch) when the catalog is disabled.
 */
@Service
@ConditionalOnProperty(name = "lorevault.catalog.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpRelationCatalogService implements RelationCatalogService {

    @Override
    public RelationCatalogDefinition resolve(RelationQuery query) {
        throw new UnsupportedOperationException("Catalog module is disabled");
    }

    @Override
    public Optional<RelationCatalogDefinition> findByKey(RelationCatalogId id) {
        throw new UnsupportedOperationException("Catalog module is disabled");
    }

    @Override
    public Optional<RelationCatalogDefinition> findByDefinitionKey(String definitionKey) {
        throw new UnsupportedOperationException("Catalog module is disabled");
    }
}
