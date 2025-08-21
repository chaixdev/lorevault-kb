name: Feature request
description: Propose a new capability or enhancement
title: "feat: <short description>"
labels: [type/feature, status/discovery]
assignees: []
body:
  - type: markdown
    attributes:
      value: |
        Propose a change. Include motivation, constraints, and acceptance criteria. Reference docs in /docs when relevant.
  - type: textarea
    id: problem
    attributes:
      label: Problem statement
      description: Why do we need this? Who benefits?
    validations:
      required: true
  - type: textarea
    id: proposal
    attributes:
      label: Proposed solution
      description: Describe the approach. Include alternatives if relevant.
    validations:
      required: true
  - type: textarea
    id: context
    attributes:
      label: Context links
      description: Link to relevant files (docs/, code, diagrams)
  - type: textarea
    id: acceptance
    attributes:
      label: Acceptance criteria
      description: Bullet list of verifiable outcomes
      placeholder: |
        - [ ] ...
        - [ ] ...
