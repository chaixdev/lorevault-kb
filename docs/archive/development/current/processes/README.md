# Process Specifications

## Core Business Processes

### Content Processing
- **[Scene Detection Specification](scene-detection-specification.md)** - AI-powered scene boundary detection
- **[Text Chunking Specification](text-chunking-specification.md)** - Content segmentation for embeddings

### Retrieval Processes  
- **[Spoiler-Aware Retrieval Process](spoiler-aware-retrieval-process.md)** - Publication coordinate filtering

## Process Architecture

LoreVault implements an asynchronous, event-driven processing pipeline:

1. **Ingestion**: File validation → Content extraction → Job creation
2. **Processing**: Scene detection → Text chunking → Embedding generation  
3. **Storage**: Neo4j persistence with relationships and coordinates
4. **Retrieval**: Semantic search with hierarchy-aware filtering
5. **QA**: RAG synthesis with source attribution

Each process maintains clear boundaries and error handling for robustness.

## 🔧 Update Instructions

**For LLM Assistants and Contributors:**

### Folder Boundaries
- **Root level**: Business process specifications and workflow documentation
- **Do not add**: Data schemas (go to `../data-model/schemas/`), API specs (go to `../../../api/specifications/`)

### When Adding Process Documentation
- **New workflows**: Create new specification files with clear naming
- **Process changes**: Update existing specifications with version notes
- **AI integration**: Document LLM interaction patterns and retry strategies

### Cross-Reference Requirements
- **Link to data models**: Use `../data-model/` paths for entity references
- **Reference APIs**: Use `../../../api/specifications/` paths for endpoint interactions
- **Architecture context**: Use `../../../architecture/` paths for system context

### Naming Conventions
- Process specs: `[process-name]-specification.md`
- Use imperative mood for process descriptions
- Include sequence diagrams for complex workflows
- Document error handling and retry logic

### Content Requirements
- Clear process boundaries and inputs/outputs
- Integration points with other system components
- Performance requirements and success criteria
- Failure modes and recovery procedures
