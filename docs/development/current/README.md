# Current System Documentation

This area contains the living documentation for the current system state.

## Contents

- **[data-model/](data-model/)** — Current database schemas and entity models
- **[processes/](processes/)** — Current business process specifications
- **[testing/](testing/)** — Active testing strategies and practices
- **[configuration/](configuration/)** — System configuration and setup guides

## Guidance

- Keep this directory aligned with the implemented system (not plans)
- Move milestone-specific content into `../versions/{version}/...`
- Update references in the root `docs/README.md` when paths change

## Temporal edges (developer note)

- New temporal precedence edges use a single relationship type `:TEMPORAL` and encode the relation via `t.relation`.
- Canonical relations are defined in `lorevault-api/src/main/java/com/lorevault/api/domain/timeline/CanonicalRelation.java`.
- Edge lifecycle status is defined in `lorevault-api/src/main/java/com/lorevault/api/domain/timeline/EdgeStatus.java`.
- Normalization utility mapping the 13 Allen relations to the 7 canonical ones is at `lorevault-api/src/main/java/com/lorevault/api/domain/timeline/RelationNormalizer.java` with tests under `.../domain/timeline/RelationNormalizerTest.java`.
