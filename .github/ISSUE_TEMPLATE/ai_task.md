name: AI task (agent-ready)
description: A context-rich task optimized for AI agents
title: "ai: <short description>"
labels: [ai/task, status/ready]
assignees: []
body:
  - type: markdown
    attributes:
      value: |
        This template produces tickets with the context an AI coding agent needs.
  - type: textarea
    id: objective
    attributes:
      label: Objective
      description: One sentence goal for the task
    validations:
      required: true
  - type: textarea
    id: context
    attributes:
      label: Context (short)
      description: Key excerpts or links from docs/ and code that the agent should read first
      placeholder: |
        - docs/... (line refs optional)
        - src/... (symbols)
    validations:
      required: true
  - type: textarea
    id: plan
    attributes:
      label: Suggested steps
      description: 3-7 concrete steps with any constraints
  - type: textarea
    id: deliverables
    attributes:
      label: Deliverables
      placeholder: |
        - Updated files: ...
        - Tests: ...
        - Docs: ...
    validations:
      required: true
  - type: textarea
    id: acceptance
    attributes:
      label: Acceptance criteria
      placeholder: |
        - [ ] Unit/integration tests pass
        - [ ] Lint/typecheck clean
        - [ ] Docs updated
