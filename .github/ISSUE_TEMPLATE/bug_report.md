name: Bug report
description: Report a defect with steps, expected vs actual, and environment details
title: "bug: <short description>"
labels: [type/bug, status/triage]
assignees: []
body:
  - type: markdown
    attributes:
      value: |
        Thanks for filing a bug! Please provide enough context so humans and agentic coders can reproduce and fix it.
  - type: textarea
    id: summary
    attributes:
      label: Summary
      description: What happened? Keep it concise.
      placeholder: Brief description of the issue
    validations:
      required: true
  - type: textarea
    id: steps
    attributes:
      label: Steps to reproduce
      description: Include exact commands, inputs, or API calls. Add logs/stack traces if available.
      placeholder: 1. ... 2. ... 3. ...
    validations:
      required: true
  - type: textarea
    id: expected
    attributes:
      label: Expected behavior
    validations:
      required: true
  - type: textarea
    id: actual
    attributes:
      label: Actual behavior
    validations:
      required: true
  - type: input
    id: version
    attributes:
      label: Version/commit
      description: Tag, version, or commit SHA
  - type: textarea
    id: env
    attributes:
      label: Environment
      description: OS, Java/Python/Node versions, Docker, DB, etc.
  - type: textarea
    id: context
    attributes:
      label: Additional context
      description: Links to relevant docs, architectural notes, or diagrams
