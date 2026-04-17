# LV-084-5 — Add Swagger/OpenAPI for API Spec Autogeneration [technical story]

Status: COMPLETE

## Context

We need a reliable, automated source of truth for the LoreVault API surface so clients and internal tools can discover endpoints, payloads, and error models. Adding OpenAPI/Swagger integration will allow us to auto-generate an API specification, publish docs, and enable tooling (SDK generation, Postman collections, contract checks) aligned with our CQRS design.

**Required reading:**

- `/docs/architecture/02-functional-viewpoint.md` (CQRS patterns and command/query segregation)
- `/docs/api/specifications/rest-api-specification.md` (current hand-maintained spec)
- `/lorevault-api/src/main/java/com/lorevault/api/web/` (current controllers and route structure)
- `/docs/rules/spec-documentation-guidelines.md` (documentation standards)

## Problem

The current REST API spec is manually maintained and can drift from the implementation. We need the source of truth to be the running application, with clear annotations and consistent conventions, producing an OpenAPI spec we can version and distribute.

## Requirements

- Springdoc OpenAPI integration for Spring Boot 3
- Autogenerate OpenAPI 3.0+ spec for both Command and Query controllers
- Grouping and tags should reflect CQRS:
  - Commands: `Catalog Commands`, `Ingestion Commands`
  - Queries: existing query controllers (if any) grouped under `Query`
- Provide a served UI for local development (Swagger UI)
- Provide a JSON/YAML spec endpoint (e.g., `/v3/api-docs`, `/v3/api-docs.yaml`)
- Preserve idempotency and business-intent semantics in summaries/descriptions
- Add basic metadata (title, version, description) and license in OpenAPI
- Ensure endpoints and models include validation constraints and examples where helpful

## Constraints

- Follow existing controller patterns and package structure
- Do not add runtime dependencies that require external services
- Keep annotations focused and minimal—prefer central configuration for global metadata
- Anonymize or hide internal endpoints if any (e.g., health checks) from public groupings

## Technical Implementation

- Add Springdoc OpenAPI dependency to `lorevault-api/pom.xml`
  - `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- Create a configuration class `OpenApiConfig` under `com.lorevault.api.config` that:
  - Sets OpenAPI info (title, description, version from project)
  - Defines groupings/tags for command vs query controllers
  - Optionally defines servers if behind a gateway in future
- Annotate controllers with `@Tag` and key methods with `@Operation` where summaries are valuable
  - `CatalogCommandController` tagged `Catalog Commands`
  - `CommandIngestionController` tagged `Ingestion Commands`
- Expose default endpoints:
  - Swagger UI: `/swagger-ui.html` and `/swagger-ui/index.html`
  - API docs: `/v3/api-docs`, `/v3/api-docs.yaml`
- Update docs to reference the generated spec and UI

## Deliverables

1. Dependency added and configuration class for OpenAPI metadata and grouping
2. Tags applied to command controllers; optional `@Operation` summaries
3. Generated spec available locally at `/v3/api-docs(.yaml)` and UI at `/swagger-ui/`
4. Documentation updates in `/docs/api/README.md` with usage and screenshots (optional)
5. CI note to publish or artifact the OpenAPI JSON/YAML on release (follow-up ticket)

## Acceptance Criteria

- [ ] Visiting `/swagger-ui/` shows the LoreVault API with grouped tags
- [ ] `/v3/api-docs` returns a valid OpenAPI JSON with all command endpoints
- [ ] Models (DTOs) include validation constraints in the schema
- [ ] Controllers are tagged logically by CQRS responsibility
- [ ] No sensitive internal endpoints are exposed in public groupings

## Quality Gates

- [ ] Maven build succeeds with the new dependency
- [ ] `mvn test` remains green; no changes to business logic
- [ ] Spot check of a few endpoints (create-universe, create-book, ingest) display correct request/response schemas
- [ ] Docs updated to guide developers to the new UI and spec endpoints

## Out of Scope

- Authentication/security schemes for OpenAPI (will be addressed later)
- Publishing the spec to an external portal (future ticket)
- SDK generation (future ticket)

## Notes

Keep annotations lightweight to avoid duplication. Lean on `OpenApiConfig` for global metadata and grouping. Maintain DDD/CQRS language in summaries to reinforce intent-based API design.
