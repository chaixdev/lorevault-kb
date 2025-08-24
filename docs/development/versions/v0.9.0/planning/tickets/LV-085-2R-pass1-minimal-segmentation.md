# LV-085-2R — Pass 1 minimal segmentation contract [refactor]

Problem

- Pass 1 output is coupled to Pass 2 assumptions, complicating triad pivot.

Proposal

- Define a minimal Pass 1 output contract suitable for triad-based analysis:
  - Scenes: id, text_range, summary, events[] with offsets
  - Events: id, title, summary, source_offsets
  - No cross-scene edges in Pass 1; only local metadata and offsets

Scope

- Update DTOs and serialization for Pass 1 output to match contract.
- Adjust any mappers/services producing Pass 1 artifacts.
- Write snapshot tests to lock the JSON schema for a sample chapter.

Acceptance criteria

- [ ] Unit/snapshot tests verify Pass 1 JSON matches new contract.
- [ ] Code compiles; no Pass 2 coupling remains in Pass 1 modules.

Quality gates

- [ ] Build and tests pass; ArchUnit rules preserved.
