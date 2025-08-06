#!/bin/bash

# Development database reset script for LoreVault Docker container ONLY
# WARNING: This will destroy data in the LoreVault development container!

echo "🔄 Resetting LoreVault development database..."

# Check if Docker container is running
if ! docker ps | grep -q "lorevault-postgres"; then
    echo "❌ Error: lorevault-postgres Docker container is not running!"
    echo "   Start it with: docker-compose up -d"
    exit 1
fi

echo "✅ Found LoreVault postgres container, proceeding with reset..."

# Reset the database schema using Docker exec (SAFE - only affects container)
echo "Dropping and recreating schema in Docker container..."
docker exec lorevault-postgres psql -U lorevault -d lorevault -c "
    DROP SCHEMA public CASCADE; 
    CREATE SCHEMA public; 
    GRANT ALL ON SCHEMA public TO lorevault; 
    GRANT ALL ON SCHEMA public TO public;
"

if [ $? -eq 0 ]; then
    echo "✅ Database reset complete. Starting application..."
    mvn -pl lorevault-api spring-boot:run
else
    echo "❌ Database reset failed!"
    exit 1
fi
