#!/bin/bash

# Development database reset script for LoreVault Neo4j + PostgreSQL Docker containers.
# WARNING: This will destroy ALL data in both databases!
#
# Default mode preserves the PostgreSQL catalog schema and Flyway history while
# truncating catalog data. Use --catalog-schema when an evolving wipe-state V1
# catalog migration changed and Flyway schema history should be rebuilt on the
# next API start.

RESET_CATALOG_SCHEMA=false

if [ "${1:-}" = "--catalog-schema" ]; then
    RESET_CATALOG_SCHEMA=true
fi

echo "🔄 Resetting LoreVault development databases..."

# --- Neo4j ---
if ! docker ps | grep -q "lorevault-neo4j"; then
    echo "❌ Error: lorevault-neo4j Docker container is not running!"
    echo "   Start it with: docker-compose up -d"
    exit 1
fi

echo "✅ Found LoreVault Neo4j container, resetting..."
docker exec lorevault-neo4j cypher-shell -u neo4j -p neosecret -d neo4j "
    MATCH (n) DETACH DELETE n;
"

if [ $? -ne 0 ]; then
    echo "❌ Neo4j database reset failed!"
    exit 1
fi
echo "✅ Neo4j database reset complete."

# --- PostgreSQL Catalog ---
if ! docker ps | grep -q "lorevault-postgres"; then
    echo "⚠️  lorevault-postgres container is not running — skipping catalog reset."
else
    echo "✅ Found LoreVault PostgreSQL container, resetting..."
    if [ "$RESET_CATALOG_SCHEMA" = true ]; then
        docker exec lorevault-postgres psql -U lorevault -d lorevault_catalog -c "
            DROP TABLE IF EXISTS catalog_definition_signature CASCADE;
            DROP TABLE IF EXISTS catalog_definition_variant CASCADE;
            DROP TABLE IF EXISTS catalog_definition CASCADE;
            DROP TABLE IF EXISTS flyway_schema_history CASCADE;
        " 2>&1 | grep -v "DROP TABLE\|NOTICE"
    else
        docker exec lorevault-postgres psql -U lorevault -d lorevault_catalog -c "
            TRUNCATE catalog_definition_variant, catalog_definition_signature, catalog_definition CASCADE;
        " 2>&1 | grep -v "TRUNCATE TABLE\|NOTICE"
    fi

    if [ ${PIPESTATUS[0]} -ne 0 ]; then
        echo "❌ PostgreSQL catalog reset failed!"
        exit 1
    fi
    echo "✅ PostgreSQL catalog reset complete."
fi

echo "✅ All database resets complete!"
