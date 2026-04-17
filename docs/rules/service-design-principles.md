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

## Additional Heuristics

### Good signs

- a service owns one meaningful business capability
- the public methods form a cohesive workflow surface
- related logic stays together instead of hopping across helper services

### Warning signs of over-segmentation

- a service has only 1-3 pass-through methods
- multiple services are always called together
- several services share the same dependencies and change together
- method names just repeat the service name with no stronger concept

### Warning signs of under-segmentation

- one service handles multiple unrelated business capabilities
- one service directly coordinates several external systems with little cohesion
- the public surface has grown too large to understand as one capability

## Implementation Guidance

When implementing a feature:

1. start with one service that handles the whole business operation
2. split only when you encounter a real external boundary
3. prefer private methods for internal workflow steps
4. ask whether the service name describes a complete user-meaningful capability

When consolidating existing code:

1. identify service clusters that always work together
2. merge them into the primary business workflow service
3. convert thin helpers back into private methods when appropriate
4. keep only ports that represent real external systems

## Testing Implication

Prefer tests that exercise the full business operation at a meaningful boundary rather than preserving artificial service splits.

## Why This Rule Exists

LoreVault already paid the cost of over-segmentation. These rules exist to stop the codebase from drifting back toward thin wrappers, fake boundaries, and choreographed internal service hops.
