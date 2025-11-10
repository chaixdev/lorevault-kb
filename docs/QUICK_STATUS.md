# LoreVault Quick Status

**Last Updated:** 2025-11-10  
**Version:** 0.8.3-SNAPSHOT  
**Latest Stable:** v0.8.1

> 📖 For comprehensive details, see [IMPLEMENTATION_STATE.md](IMPLEMENTATION_STATE.md)

---

## 🎯 What's Working (Production Ready)

| Feature | Status | Description |
|---------|--------|-------------|
| **Chapter Ingestion** | ✅ | Upload chapters via API, async processing |
| **Scene Detection** | ✅ | AI-powered scene boundary detection |
| **Text Chunking** | ✅ | Semantic chunking with overlap |
| **Vector Embeddings** | ✅ | Generate embeddings for all chunks |
| **Graph Database** | ✅ | Neo4j with Universe→Series→Book→Chapter→Scene→Chunk hierarchy |
| **Semantic Search** | ✅ | Vector search over content with ranking |
| **RAG Q&A** | ✅ | Natural language questions with citations |
| **Library Management** | ✅ | Create universes, series, books |
| **Job Tracking** | ✅ | Async job status monitoring |
| **Health Checks** | ✅ | LLM and embedding provider health |
| **Testing** | ✅ | 274 tests, 85% coverage, mutation testing |

## 🚧 In Progress (Current Development)

| Feature | Status | Target | Description |
|---------|--------|--------|-------------|
| **Temporal Edges** | 🚧 | v0.8.3 | MEETS relationships between scenes |
| **Timeline Modeling** | 🚧 | v0.9.0 | Scene→Event dual-write, temporal relationships |

## 📋 Planned (Future Versions)

| Feature | Target | Description |
|---------|--------|-------------|
| **Entity Extraction** | v1.1-1.5 | Characters, Locations, Items, Factions |
| **Spoiler Filtering** | v0.10.0 | Reading progress-aware search |
| **Entity Graph** | v2.0.0 | Full entity knowledge graph |
| **Web UI** | v3.0.0 | Interactive entity browser |
| **Timeline Reasoning** | v5.0.0 | Advanced temporal queries |

---

## 📊 Key Metrics

```
Code Base:          167 main source files, 70 test files
Test Coverage:      274 tests passing, 85% instruction coverage
Architecture:       Hexagonal (Ports & Adapters)
Quality Gates:      JaCoCo (85%), PIT Mutation (80%), ArchUnit
Tech Stack:         Java 21, Spring Boot 3.5.4, Neo4j 5.x
```

## 🔌 API Endpoints (Live)

### Write Operations
- ✅ `POST /api/library/create-universe`
- ✅ `POST /api/library/create-series`
- ✅ `POST /api/library/create-book`
- ✅ `POST /api/ingestion/chapters` (multipart)

### Read Operations
- ✅ `GET /api/ingestion/jobs/{id}`
- ✅ `GET /api/ingestion/jobs`
- ✅ `POST /api/search/semantic`
- ✅ `POST /api/ask/rag`
- ✅ `GET /api/health`
- ✅ `GET /api/health/llm`
- ✅ `GET /api/health/embeddings`

## 🏗️ Architecture Layers

```
┌─────────────────────────────────────┐
│  Web Controllers                    │  REST API
│  (Command & Query)                  │
├─────────────────────────────────────┤
│  Application Services               │  Business Logic
│  (Ingestion, Search, RAG)           │
├─────────────────────────────────────┤
│  Application Ports                  │  Interfaces
│  (ContentPersistence, Embedding,    │
│   SceneDetection, Search)           │
├─────────────────────────────────────┤
│  Infrastructure Adapters            │  External Systems
│  (Neo4j, OpenAI, In-Memory)         │
└─────────────────────────────────────┘
```

## 🚀 Quick Start

```bash
# Prerequisites: Java 21, Maven 3.6+, Docker

# 1. Start Neo4j
docker compose up -d neo4j

# 2. Build
mvn clean compile

# 3. Run
mvn -pl lorevault-api spring-boot:run

# 4. Verify
curl http://localhost:8080/actuator/health

# 5. Run tests
mvn test
```

## 📝 Current Focus

**v0.8.3-SNAPSHOT Development:**
- Creating default temporal MEETS edges between consecutive scenes
- In-chapter temporal links
- Cross-chapter temporal links
- Idempotent edge creation

**Next Milestone (v0.9.0):**
- Scene → Event dual-write pattern
- Event entity model
- Rich temporal relationship types
- Timeline query APIs

## ⚠️ Known Limitations

- ❌ No entity extraction (Characters, Locations, etc.) yet
- ❌ No spoiler-aware filtering
- ❌ In-memory vector search (default) - not optimized for scale
- ❌ No authentication/authorization
- ⚠️ Some flaky tests excluded by default
- ⚠️ Limited timeline modeling (basic MEETS only)

## 🎓 Learning Resources

- **New Users:** Start with [IMPLEMENTATION_STATE.md](IMPLEMENTATION_STATE.md)
- **Architecture:** See [architecture/](architecture/)
- **API Usage:** See [api/specifications/](api/specifications/)
- **Testing:** See [development/current/testing/](development/current/testing/)
- **Vision:** See [project_summary.md](project_summary.md)

---

## 📈 Version History

| Version | Date | Highlights |
|---------|------|------------|
| v0.8.1 | 2025-08-21 | BookTitle/ChapterTitle mapping fixes, ingestion refactor |
| v0.8.0 | 2025-08-21 | Testing rewrite, RAG/Query improvements, health checks |
| v0.7.0 | - | Vector search integration |
| v0.6.0 | - | Publication coordinates & hierarchy |
| v0.5.0 | - | Neo4j data model foundation |
| v0.4.0 | - | Production polish & architecture |
| v0.3.0 | - | Scene detection & hierarchical structure |
| v0.2.0 | - | Content storage & segmentation |
| v0.1.0 | - | API shell & basic job lifecycle |

---

**TL;DR:** Core content ingestion and semantic search are **fully functional and production-ready**. Entity extraction and advanced timeline features are **on the roadmap**. The system can ingest chapters, detect scenes, generate embeddings, store in Neo4j, and answer questions about the content.

For detailed information, see **[IMPLEMENTATION_STATE.md](IMPLEMENTATION_STATE.md)**.
