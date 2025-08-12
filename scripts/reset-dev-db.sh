#!/bin/bash

# Development database reset script for LoreVault Neo4j Docker container ONLY
# WARNING: This will destroy data in the LoreVault development container!

echo "🔄 Resetting LoreVault development Neo4j database..."

# Check if Docker container is running
if ! docker ps | grep -q "lorevault-neo4j"; then
    echo "❌ Error: lorevault-neo4j Docker container is not running!"
    echo "   Start it with: docker-compose up -d"
    exit 1
fi

echo "✅ Found LoreVault Neo4j container, proceeding with reset..."

# Reset the Neo4j database using cypher-shell (SAFE - only affects container)
echo "Clearing all data in Neo4j database..."
docker exec lorevault-neo4j cypher-shell -u neo4j -p neosecret -d neo4j "
    MATCH (n) DETACH DELETE n;
"

if [ $? -eq 0 ]; then
    echo "✅ Neo4j database reset complete!"
    echo "🚀 You can now start the application with: mvn -pl lorevault-api spring-boot:run"
else
    echo "❌ Neo4j database reset failed!"
    exit 1
fi
