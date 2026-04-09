# Service Design Principles

**Status:** Active

## Core Rules

- create services for business capabilities, not incidental helper logic
- extract ports only for real external boundaries
- keep closely related workflow logic together
- prefer private methods over creating thin delegating services

## Smells To Avoid

- services with only a handful of pass-through methods
- internal validation or utility services with no independent business meaning
- multiple services that always change together and share the same dependencies
- service boundaries created only to preserve an old architecture style

## Practical Guidance

- if a user could describe the operation as a meaningful capability, a service may be justified
- if the logic only supports one larger workflow internally, keep it inside that workflow's service
- if the boundary is Neo4j, an LLM provider, or another external system, abstraction may be justified

## Why This Rule Exists

LoreVault already paid the cost of over-segmentation. These rules exist to stop the codebase from drifting back toward thin wrappers, fake boundaries, and choreographed internal service hops.

Primary source:
- `../development/current/architecture/service-design-principles.md`
