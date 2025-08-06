#!/bin/bash

# Development database reset script
# WARNING: This will destroy all data!

echo "🔄 Resetting development database..."

# Option 1: Drop and recreate the database
echo "Dropping and recreating database..."
psql -h localhost -U postgres -c "DROP DATABASE IF EXISTS lorevault_dev;"
psql -h localhost -U postgres -c "CREATE DATABASE lorevault_dev;"

# Option 2: Alternative - use Flyway clean
# mvn -pl lorevault-api flyway:clean flyway:migrate

echo "✅ Database reset complete. Starting application..."
mvn -pl lorevault-api spring-boot:run -Dspring.profiles.active=dev
