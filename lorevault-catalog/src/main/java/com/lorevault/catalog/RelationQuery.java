package com.lorevault.catalog;

import java.util.Optional;
import java.util.UUID;

public record RelationQuery(
    String definitionKey,
    String rawName,
    String subjectKind,
    String objectKind,
    String description,
    String certainty,
    String evidenceReference,
    UUID chapterId,
    UUID sceneId,
    Optional<String> cappedEvidenceSnippet
) {}
