# LoreVault Rules

This directory contains contributor guidance and durable rules.

Rules docs capture coding preferences, documentation conventions, code hygiene expectations, and repeatable guidance that should shape future work across the repository.

Rules should be self-contained. They may link to other rules, ADRs, or patterns when that improves canonical guidance, but they should not depend on planning or brainstorm material.

## Use This Folder For

- coding rules and architectural preferences
- guidance on reuse, boundaries, and hygiene
- documentation writing conventions
- contributor checklists that should remain durable

## Do Not Use This Folder For

- one-off ticket notes
- current mechanism explanations
- present-state topology or coupling maps (belongs in `../patterns/`)
- speculative design work
- historical refactor logs

Rules should be short, opinionated, and easy to apply repeatedly.

If a rule needs rationale or mechanism context that only exists in exploratory docs, promote the necessary truth into canonical docs rather than linking outward.

## Current Rules

- [Code organization guidance](code-organization-guidance.md)
- [Development workflow](development-workflow.md)
- [Exception semantics](exception-semantics.md)
- [Service design principles](service-design-principles.md)
- [Specification documentation guidelines](spec-documentation-guidelines.md)
- [Architecture documentation guidelines](architecture-documentation-guidelines.md)
- [Developer testing workflow](developer-testing-workflow.md)
- [Logging philosophy](logging-philosophy.md)
- [Coding standards](coding-standards.md)
- [LoreVault module and domain model conventions](lorevault-module-conventions.md)
