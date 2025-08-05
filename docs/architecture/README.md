# LoreVault: System Architecture Documentation

## Overview

This directory contains the complete architectural documentation for the **LoreVault** system using the Rozanski & Woods architectural viewpoints and perspectives methodology. Each viewpoint examines the system from a different stakeholder perspective to provide comprehensive architectural understanding.

## Architecture Philosophy

The architecture is designed to be robust, scalable, and maintainable, leveraging the Spring Boot ecosystem. It treats AI components not as magic boxes, but as specialized services with distinct responsibilities, integrated into a familiar enterprise application pattern.

### Key Architectural Decisions

1. **Command Query Responsibility Segregation (CQRS)**: Separates write operations (complex processing) from read operations (fast queries)
2. **Hybrid AI Architecture**: Local Gemma 3B for cost-effective pre-processing + External LLMs for complex reasoning
3. **Asynchronous Processing**: Non-blocking ingestion with background processing pipelines
4. **Container-First Deployment**: Docker-based deployment for consistency and scalability

## Architectural Viewpoints

### [1. Context Viewpoint](./01-context-viewpoint.md)
**Stakeholders:** System administrators, external integrators, business stakeholders  
**Concerns:** System boundaries, external dependencies, user interactions

Describes the relationships between LoreVault and its environment, including external AI services, users, and system boundaries.

### [2. Functional Viewpoint](./02-functional-viewpoint.md)
**Stakeholders:** Developers, architects, testers  
**Concerns:** System functionality, component responsibilities, interfaces

Details the runtime functional elements, their responsibilities, and interactions. Focuses on the CQRS pattern and component architecture.

### [3. Information Viewpoint](./03-information-viewpoint.md)
**Stakeholders:** Database administrators, data architects, developers  
**Concerns:** Data structures, persistence, information flow

Describes the data architecture, including PostgreSQL schema design, vector storage, and information processing flow.

### [4. Concurrency Viewpoint](./04-concurrency-viewpoint.md)
**Stakeholders:** Performance engineers, developers, system administrators  
**Concerns:** Concurrent processing, threading, asynchronous operations

Explains how the system handles concurrent requests, background processing, and the asynchronous ingestion pipeline.

### [5. Deployment Viewpoint](./05-deployment-viewpoint.md)
**Stakeholders:** DevOps engineers, system administrators, infrastructure teams  
**Concerns:** Physical deployment, containerization, infrastructure requirements

Details the deployment strategy, containerization approach, and infrastructure requirements including local AI model hosting.

## Quick Navigation

- **🚀 Getting Started**: See [Context Viewpoint](./01-context-viewpoint.md) for system overview
- **🏗️ Implementation**: Start with [Functional Viewpoint](./02-functional-viewpoint.md) for component details
- **💾 Data Design**: Review [Information Viewpoint](./03-information-viewpoint.md) for database schema
- **⚡ Performance**: Check [Concurrency Viewpoint](./04-concurrency-viewpoint.md) for async processing
- **🐳 Operations**: See [Deployment Viewpoint](./05-deployment-viewpoint.md) for infrastructure

## Architecture Principles

1. **Cost-Conscious AI**: Local processing for high-volume tasks, external APIs for complex reasoning
2. **Fail-Safe Design**: Graceful degradation with fallback mechanisms
3. **Scalable by Design**: Horizontal scaling capabilities at all layers
4. **Observable Operations**: Comprehensive monitoring and logging throughout
5. **API-First**: Clean separation between processing and consumption

## Related Documentation

- [Project Summary](../project_summary.md) - High-level project goals and scope
- [Technical Notes](../technical_notes.md) - Implementation decisions and trade-offs
- [Development Setup](../../README.md) - Getting started with development
