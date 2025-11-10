# LoreVault

An Agentic Knowledge Ingestion Service for fictional universes.

> **📊 Current Status:** See [docs/QUICK_STATUS.md](docs/QUICK_STATUS.md) for at-a-glance feature status  
> **📖 Full Report:** See [docs/IMPLEMENTATION_STATE.md](docs/IMPLEMENTATION_STATE.md) for comprehensive details

## Quick Start

### Prerequisites

- Java 21
- Maven 3.6+
- Docker and Docker Compose

### Stack (v0.8.3-SNAPSHOT)

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

### Library Management
- `POST /api/library/create-universe` (create universe)
- `POST /api/library/create-series` (create series)
- `POST /api/library/create-book` (create book)

### Content Ingestion
- `POST /api/ingestion/chapters` (submit chapter with multipart form)
- `GET /api/ingestion/jobs/{id}` (job status)
- `GET /api/ingestion/jobs` (list all jobs)

### Search & Query
- `POST /api/search/semantic` (semantic search over content)
- `POST /api/ask/vector` (vector search - debug endpoint)
- `POST /api/ask/rag` (RAG question answering with citations)

### Health & Monitoring
- `GET /api/health` (system health)
- `GET /api/health/llm` (LLM provider health)
- `GET /api/health/embeddings` (embedding provider health)
- `GET /actuator/health` (Spring Boot actuator)
- `GET /actuator/info` (build info)

## Documentation

See `docs/` for comprehensive documentation:

- **[Implementation State Report](docs/IMPLEMENTATION_STATE.md)** - Detailed status of all features (what works, what's missing, what's next)
- **[Project Summary](docs/project_summary.md)** - Vision, roadmap, and long-term goals
- **[Architecture](docs/architecture/)** - System design and technical foundations
- **[API Specifications](docs/api/)** - REST API documentation and Postman collections

## Version Roadmap

- ✅ v0.8.1 - Performance & Architecture Optimization (latest stable)
- 🚧 v0.8.3-SNAPSHOT - Default temporal edges (MEETS) (current development)
- ⏳ v0.9.0 - Timeline & Scene Events (in planning)
- 📋 v0.10.0 - Spoiler-Aware Search
- 📋 v1.0.0 - MVP with Production Polish

See [docs/IMPLEMENTATION_STATE.md](docs/IMPLEMENTATION_STATE.md) for complete roadmap and current feature status.

## Recent Changes (v0.8.3-SNAPSHOT)

- Default temporal edges (MEETS) created during ingestion
- In-chapter edges between consecutive scenes
- Cross-chapter edges linking chapters
- Idempotent, bookId-scoped sweep operations

---

For detailed status of what's implemented, in-progress, or planned, see **[docs/IMPLEMENTATION_STATE.md](docs/IMPLEMENTATION_STATE.md)**.
