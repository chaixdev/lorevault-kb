package com.lorevault.catalog.internal;

import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationCatalogService;
import com.lorevault.catalog.RelationQuery;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "lorevault.catalog.enabled", havingValue = "true")
public class RelationCatalogServiceImpl implements RelationCatalogService {

    private static final String TX_MANAGER = "catalogTransactionManager";

    private final RelationCatalogStore store;

    public RelationCatalogServiceImpl(RelationCatalogStore store) {
        this.store = store;
    }

    /**
     * Catalog resolution commits in an independent catalog transaction.
     *
     * <p>This is intentional: relation definitions are durable observations of relation-kind
     * semantics and may survive a downstream Neo4j claim-persistence rollback. Definition
     * content is first-write-wins: if a key already exists, later descriptions/display names
     * are ignored and only {@code last_seen} is refreshed by the store.</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = TX_MANAGER)
    public RelationCatalogDefinition resolve(RelationQuery query) {
        return store.findByDefinitionKey(query.definitionKey())
            .or(() -> store.findBestMatch(query))
            .orElseGet(() -> store.create(query));
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS, transactionManager = TX_MANAGER)
    public Optional<RelationCatalogDefinition> findByKey(RelationCatalogId id) {
        return store.findById(id);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS, transactionManager = TX_MANAGER)
    public Optional<RelationCatalogDefinition> findByDefinitionKey(String definitionKey) {
        return store.findByDefinitionKey(definitionKey);
    }
}
