package com.lorevault.catalog;

import java.util.UUID;

public record RelationCatalogId(UUID value) {
    public static RelationCatalogId random() {
        return new RelationCatalogId(UUID.randomUUID());
    }

    public static RelationCatalogId fromString(String uuidString) {
        return new RelationCatalogId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
