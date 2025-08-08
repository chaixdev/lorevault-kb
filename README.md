# LoreVault

An Agentic Knowledge Ingestion Service for fictional universes.

## Quick Start

### Prerequisites

- Java 21
- Maven 3.6+
- Docker and Docker Compose

### Setup

1. **Start the PostgreSQL database:**
   ```bash
   docker-compose up -d postgres
   ```

2. **Build the project:**
   ```bash
   mvn clean compile
   ```

3. **Run the application:**
   ```bash
   mvn -pl lorevault-api spring-boot:run
   ```

4. **Verify the application is running:**
   - Health check: http://localhost:8080/actuator/health
   - Custom status: http://localhost:8080/api/status

### Running Tests

```bash
mvn test
```

## Development

### Environment Configuration

Copy `.env.example` to `.env` and adjust values as needed:

```bash
cp .env.example .env
```

### Database Management

The application connects to PostgreSQL by default. For development:

- **Start database:** `docker-compose up -d postgres`
- **Stop database:** `docker-compose down`
- **Reset database:** `docker-compose down -v && docker-compose up -d postgres`

### Project Structure

```
lorevault/
├── lorevault-api/          # REST API module
│   ├── src/main/java/      # Application source
│   ├── src/main/resources/ # Configuration files
│   └── src/test/          # Tests
├── docker-compose.yml     # Development database
├── pom.xml               # Parent POM
└── README.md            # This file
```

## API Endpoints

- `GET /api/status` - Application status
- `GET /actuator/health` - Health check endpoint  
- `GET /actuator/info` - Application information
- `GET /api/health` - Service health monitoring
- `GET /api/health/llm` - LLM connectivity validation

## Documentation

### 📚 Architecture & Specifications

- **[Architecture Documentation](docs/architecture/)** - Complete architectural viewpoints using Rozanski & Woods methodology
- **[Technical Specifications](docs/specs/)** - Detailed specs bridging architecture to implementation
- **[Project Summary](docs/project_summary.md)** - High-level vision and roadmap

### 🤖 AI Integration

- **[Scene Detection Specification](docs/specs/scene-detection-specification.md)** - XML-based AI scene boundary detection
- **[Health Endpoint Specification](docs/specs/health-endpoint-specification.md)** - LLM service monitoring

### 🏗️ Implementation Status

- ✅ **v0.4.0** - Production Polish & Architecture (Current)
- � **v0.5.0** - Vector Embeddings & Semantic Search (Next)
- 📋 **v0.6.0** - Entity Extraction & Recognition (Future)
