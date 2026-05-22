package com.lorevault.catalog;

public record RelationQuery(
    String definitionKey,
    String rawName,
    String subjectKind,
    String objectKind,
    String description,
    String certainty
) {}
