# Health Endpoint Specification

## Overview
REST endpoints expose LoreVault API service health and LLM connectivity status.

## Endpoints

### GET /api/health
Purpose: Overall service health with basic dependency status.

Response:
```json
{
  "healthy": boolean,
  "service": "LoreVault API",
  "version": "0.1.0-SNAPSHOT",
  "timestamp": "2025-08-08T10:30:00Z",
  "checks": {
    "llm": {
      "healthy": boolean,
      "description": "Large Language Model API connectivity"
    }
  }
}
```

### GET /api/health/llm
Purpose: Detailed LLM service health with per-model status.

Response:
```json
{
  "healthy": boolean,
  "service": "LLM API",
  "timestamp": "2025-08-08T10:30:00Z",
  "description": "All models operational" | "One or more models have issues",
  "models": {
    "embedding": {
      "healthy": boolean,
      "name": "Embedding Model",
      "status": "operational" | "error",
      "dimensions": 1536
    },
    "nlp-small": {
      "healthy": boolean,
      "name": "NLP Small Model",
      "status": "operational" | "error"
    },
    "nlp-big": {
      "healthy": boolean,
      "name": "NLP Big Model", 
      "status": "operational" | "error"
    }
  }
}
```

## Status Codes
- 200 OK always returned; health state encoded in body.

## Security
- Error details sanitized; no provider secrets or stack traces.

## Dependencies
- LlmHealthCheckService performs checks via Spring AI ChatClient.
- BuildProperties supplies service version information.
