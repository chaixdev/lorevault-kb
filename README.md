# LoreVault

An Agentic Knowledge Ingestion Service for fictional universes.

## Quick Start

### Prerequisites

- Java 21
- Maven 3.6+
- Docker and Docker Compose

### Stack

- Spring Boot 3
- Neo4j (graph persistence with native vector indexing)
- Spring AI (LLM integration for scene detection and RAG)
- CQRS-based API architecture (command/query separation)

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
   ./scripts/dev-api.sh run
   ```

4. **Verify:**
   - Health: <http://localhost:18080/actuator/health>
   - API status: <http://localhost:18080/api/status>

### Running Tests

```bash
mvn test
```

Tests spin up a Neo4j Testcontainer automatically.

## Development

### Environment Configuration

Copy `.env.example` to `.env` if present:

```bash
cp .env.example .env
```

The app reads runtime configuration from environment variables referenced in
`lorevault-web/src/main/resources/application.yml`. Maven does **not** auto-load
`.env`, so source it before running the app unless your IDE task already does that.

Preferred local run command:

```bash
./scripts/dev-api.sh run
```

Background workflow:

```bash
./scripts/dev-api.sh start
./scripts/dev-api.sh logs
```

Other helper commands:

- `./scripts/dev-api.sh status`
- `./scripts/dev-api.sh stop`
- `./scripts/dev-api.sh restart`

### Neo4j Management

(See `docker-compose.yml` for credentials.)

- Start: `docker-compose up -d neo4j`
- Stop: `docker-compose down`
- Reset: `docker-compose down -v && docker-compose up -d neo4j`

Repo-local helpers:

- Start/stop/status/logs for the API: `./scripts/dev-api.sh <run|start|stop|restart|status|logs>`
- Reset local dev DB only: `./scripts/reset-dev-db.sh`
- Reset + seed canonical sample data: `./scripts/prepare-dev-environment.sh`

`scripts/prepare-dev-environment.sh` assumes both the app and Neo4j are already running.

Minimal constraint applied automatically on startup:

- `Chapter.contentHash` UNIQUE

### Project Structure

```text
lorevault/
├── lorevault-core/
│   ├── src/main/java/
│   └── src/main/resources/
├── lorevault-web/
│   ├── src/main/java/
│   ├── src/main/resources/
│   └── src/test/
├── docs/
├── docker-compose.yml
├── pom.xml
└── README.md
```

## API Endpoints

### Command Endpoints

- `POST /api/command/ingest` - Submit content files for processing
- `POST /api/command/library/create-universe` - Create universe hierarchy
- `POST /api/command/library/create-series` - Create series within universe  
- `POST /api/command/library/create-book` - Create book within series

### Query Endpoints

- `GET /api/query/jobs` - List ingestion jobs with filtering
- `GET /api/query/jobs/{id}` - Get specific job status
- `POST /api/query/ask/vector` - Semantic search over content
- `POST /api/query/ask/rag` - RAG-based question answering
- `GET /api/query/health` - System health and diagnostics

### Legacy/System Endpoints

- `GET /api/status` - Basic API status
- `GET /actuator/health` - Spring Boot health check
- `GET /actuator/info` - Application information

## Documentation

See `docs/` for architecture viewpoints & specifications.

## Version Roadmap

- ✅ Service consolidation & CQRS API (current)
- ⏳ Timeline modeling with Scene-as-Event entities (upcoming)
- 📝 Spoiler-aware search with publication coordinates
- 📝 Production-ready MVP with comprehensive testing
- 📝 Entity extraction (Characters, Locations, etc.)

## Migration Notes

Post-refactor consolidation: Service architecture streamlined to 3 main areas (Ingestion, Query, System). Legacy endpoints maintained for backward compatibility. CQRS command/query separation provides clean API patterns. Neo4j native vector indexing provides efficient semantic search.

---

All major functionality (ingestion, semantic search, RAG Q&A) fully operational with consolidated architecture.
