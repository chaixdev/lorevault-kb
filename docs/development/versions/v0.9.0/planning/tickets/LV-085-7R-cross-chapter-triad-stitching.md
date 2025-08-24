# LV-085-7R — Cross-chapter triad stitching [feature]

Problem

- Triads should bridge adjacent chapters without exploding graph size.

Proposal

- Introduce a windowed neighbor strategy that considers N previous and next chapters (configurable) when building triads, with guards:
  - cap triads per focal event
  - prefer relations in precedence set for distant links

Scope

- Neighbor provider: windowed strategy
- Guards: caps and fallbacks
- Tests: ensure only windowed neighbors are used and caps enforced

Acceptance criteria

- [ ] Triads include cross-chapter edges within window
- [ ] Caps prevent excessive triad generation in tests

Quality gates

- [ ] Build and unit tests pass; ArchUnit rules preserved.
