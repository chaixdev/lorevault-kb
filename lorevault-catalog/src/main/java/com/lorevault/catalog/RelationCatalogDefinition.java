package com.lorevault.catalog;

import java.time.Instant;
import java.util.List;

public record RelationCatalogDefinition(
    RelationCatalogId id,
    String definitionKey,
    String displayName,
    String description,
    List<RelationKindSignature> signatures,
    List<String> rawNameVariants,
    Instant created,
    Instant updated,
    Instant lastSeen
) {}
