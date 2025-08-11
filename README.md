# LoreVault

An Agentic Knowledge Ingestion Service for fictional universes.

## Quick Start

### Prerequisites

- Java 21
- Maven 3.6+
- Docker and Docker Compose

### Stack (v0.4.0)

- Spring Boot 3
- Neo4j (graph persistence) – replaces prior Postgres/JPA
- Spring AI (LLM scene detection only; semantic search deferred)

### Setup

1. **Start Neo4j:**
   ```bash
   docker-compose up -d neo4j
   ```

2. **Build the project:**
   ```bash
   mvn clean compile
   ```

3. **Run the application:**
   ```bash
   mvn -pl lorevault-api spring-boot:run
   ```

4. **Verify:**
   - Health: http://localhost:8080/actuator/health
   - API status: http://localhost:8080/api/status

### Running Tests

```bash
mvn test
```

Tests spin up a Neo4j Testcontainer automatically.

## Development

### Environment Configuration

Copy `.env.example` to `.env` if present (optional):

```bash
cp .env.example .env
```

### Neo4j Management

(See `docker-compose.yml` for credentials.)

- Start: `docker-compose up -d neo4j`
- Stop: `docker-compose down`
- Reset: `docker-compose down -v && docker-compose up -d neo4j`

Minimal constraint applied automatically on startup:

- `Chapter.contentHash` UNIQUE

### Project Structure

```
lorevault/
├── lorevault-api/
│   ├── src/main/java/
│   ├── src/main/resources/
│   └── src/test/
├── docs/
├── docker-compose.yml
├── pom.xml
└── README.md
```

## API Endpoints

- `GET /api/status`
- `GET /actuator/health`
- `GET /actuator/info`
- `GET /api/health`
- `GET /api/health/llm`
- `POST /api/ingestion/chapters` (submit chapter)
- `GET /api/ingestion/jobs/{id}` (job status)
- `GET /api/ingestion/jobs` (list jobs)
- `POST /api/search/semantic` → 501 (semantic search deferred to v0.5.0)

## Documentation

See `docs/` for architecture viewpoints & specifications.

## Version Roadmap

- ✅ v0.4.0 Graph Migration & Architecture hardening (current)
- ⏳ v0.5.0 Semantic Search & Embeddings (upcoming)
- 📝 v0.6.0 Entity Extraction

## Migration Notes

Legacy Postgres/JPA artifacts removed; domain objects now plain POJOs mapped to Neo4j node models. Temporary mapper will be deleted once ingestion flow no longer constructs transitional objects.

---

Semantic search currently returns 501 until embeddings are introduced.
