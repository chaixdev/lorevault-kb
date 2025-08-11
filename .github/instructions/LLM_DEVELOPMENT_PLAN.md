# LLM Development Plan for LoreVault Issue Implementation

## Core Development Philosophy

**See**: `/github/instructions/CORE_DEVELOPMENT_PHILOSOPHY.md` for comprehensive development guidelines.

**Quick Reference**: Balance clean, robust enterprise code with pragmatic delivery. Consider multiple approaches and select based on maintainability, robustness, complexity, and architectural alignment.

> NOTE (v0.4.0 pivot): The project has migrated from an RDBMS approach to a graph-based persistence layer (Neo4j). All plan language is now persistence-neutral. Semantic embeddings and true semantic/vector search are explicitly deferred to v0.5.0. Until then, the semantic search endpoint is retained but returns a Not Implemented response.

## Issue Implementation Workflow

### Phase 1: Context Gathering (MANDATORY)

Before implementing any issue, the LLM MUST gather complete context:

#### 1.1 Read Project Vision
```markdown
📖 REQUIRED READING:
- /docs/project_summary.md (complete system vision and architecture)
- /docs/architecture/ (all viewpoint documents for architectural context)
- /docs/spec/ (relevant specifications for the feature domain)
```

#### 1.2 Assess Current Implementation State
```markdown
🔍 IMPLEMENTATION AUDIT:
- Examine source code in lorevault-api/src/main/java/
- Review persistence layer definitions (e.g., node/entity models, repositories, migrations or setup scripts)
- Review existing tests in src/test/java/ for patterns
- Identify integration points with existing components
- Document gaps between current state and requirements
```

#### 1.3 Understand System Context
```markdown
🏗️ ARCHITECTURAL CONTEXT:
- CQRS impact: Command path vs Query path?
- Spring Boot component integration requirements
- Persistence model implications (graph representation, relationships, indexing strategy, future vector/semantic capabilities)
- Asynchronous processing workflow effects
- External service dependencies (AI APIs)
```

### Phase 2: Solution Design & User Verification (MANDATORY)

#### 2.1 Generate Alternative Approaches
The LLM MUST consider at least 2-3 different implementation approaches:

```markdown
🎯 SOLUTION ALTERNATIVES:

### Approach 1: [Name]
**Description**: [Technical approach with key architectural decisions]
**Files**: [Specific files to modify]
**Pros**: [Key advantages]
**Cons**: [Key limitations]  
**Complexity**: [Low/Medium/High]
**Integration Impact**: [Minimal/Moderate/Significant]
**CQRS Impact**: [Command/Query/Both]

### Approach 2: [Name]
[Same format as above]

### Approach 3: [Name]
[Same format as above]
```

#### 2.2 Solution Selection & Recommendation
Use the decision framework from `/github/instructions/CORE_DEVELOPMENT_PHILOSOPHY.md` to evaluate and select the best approach.

### Phase 3: Conflict Resolution (CRITICAL)

#### 3.1 Identify Potential Conflicts
```markdown
🔍 SOLUTION ANALYSIS FOR ISSUE [Issue Number]

## Context Summary
[Brief summary of issue requirements and current system state]

## Proposed Solutions

I've analyzed [2-3] different approaches for implementing this feature:

### 🥇 RECOMMENDED: [Approach Name]
**What it does**: [Clear description of the technical approach]
**Key benefits**: [Top 2-3 advantages, technically specific]
**Trade-offs**: [Honest assessment of limitations or costs]
**Implementation scope**: [Files that will be modified, approximate complexity]

### 🥈 Alternative: [Approach Name]  
**What it does**: [Clear description of the technical approach]
**Why not recommended**: [Specific technical reasons for ranking lower]
**Would be better if**: [Conditions under which this might be preferred]

### 🥉 Alternative: [Approach Name]
**What it does**: [Clear description of the technical approach]
**Why not recommended**: [Specific technical reasons for ranking lower]
**Would be better if**: [Conditions under which this might be preferred]

## My Recommendation & Reasoning

I recommend **[Approach Name]** because:
1. [Specific technical reason 1]
2. [Specific technical reason 2] 
3. [Specific technical reason 3]

This approach [connects to broader system architecture/project goals].

## Questions for You

Before I proceed with implementation, I'd like your input on:
1. Does this approach align with your vision for [specific aspect]?
2. Are you comfortable with [specific trade-off or limitation]?
3. Would you prefer a different balance between [competing concerns]?

**Please let me know**:
- ✅ Proceed with recommended approach
- 🔄 Discuss modifications to the recommended approach  
- 🔀 Consider a different alternative
- ❓ Need clarification about [specific technical detail]
```

#### 2.4 User Collaboration Process

**Encourage technical discussion**: Challenge analysis, defend choices with specifics, remain open to user insights, iterate until satisfied.

**Collaboration continues until**: User explicitly approves an approach OR requests specific alternative OR agrees on hybrid approach.

**Collaboration patterns**: Simpler approach requests → Justify complexity or simplify. Technical challenges → Provide rationale or alternatives. New constraints → Re-analyze approaches.

#### 2.5 Iterative Refinement Process

**When user challenges analysis**: Listen carefully → Ask clarifying questions → Propose modifications → Re-evaluate → Present refined analysis → Continue until approval.

**The LLM should adapt solutions collaboratively rather than defending fixed positions.**

### Phase 3: Conflict Resolution (CRITICAL)

**When conflicts/ambiguities detected, LLM MUST ask for clarification**:

```markdown
🤔 CLARIFICATION NEEDED:

**[Conflict 1]**: [Description] → **Options**: [Approaches] → **Recommendation**: [Suggested approach]
**[Conflict 2]**: [Description] → **Options**: [Approaches] → **Recommendation**: [Suggested approach]

Please provide guidance on: [Specific questions]
```

**NEVER**: Make assumptions when requirements are unclear.

### Phase 4: Implementation (SYSTEMATIC)

**⚠️ PREREQUISITE**: Implementation may ONLY begin after receiving explicit user approval from Phase 2.3.

**Follow the Testing Strategy**: Implement according to `/docs/spec/testing-strategy.md`:
1. **Service Tests First**: Write service-level tests that define business behavior
2. **Implementation**: Implement the business logic to satisfy service tests  
3. **Integration Tests**: Add integration tests for persistence interactions if needed
4. **Edge Case Coverage**: Add specific contract tests for complex edge cases

**Persistence Changes**: If needed, create appropriate migration or schema update artifacts (e.g., graph refactor scripts, index creation guidance). Avoid premature vector/embedding scaffolding before v0.5.0.

**Spring Boot Patterns**: Follow existing patterns for Controllers, Services, Repositories, DTOs

### Phase 5: User Verification & Testing (MANDATORY)

**⚠️ CRITICAL**: Before documentation, the user MUST be able to build, run, and see the feature working.

#### 5.1 🛑 VERIFICATION GATE: "Show Me It Works"

**The LLM MUST provide specific verification instructions**:

```markdown
🔍 VERIFICATION INSTRUCTIONS FOR ISSUE [Issue Number]

### Build & Run
1. Build: `mvn clean install` (from project root)
2. Run: `mvn -pl lorevault-api spring-boot:run` (start the application)
3. Test API: Use provided curl commands or test endpoints
4. Monitor: Check application logs for expected behavior

### Verification Steps
1. **[Step 1]**: [Action] → [Expected result]
2. **[Step 2]**: [Action] → [Expected result]  
3. **[Step 3]**: [Action] → [Expected result]

### Success Indicators  
- ✅ [Observable success indicator 1]
- ✅ [Observable success indicator 2]
- ✅ [Observable success indicator 3]
```

#### 5.2 User Confirmation Protocol

**User must confirm before documentation**:
- ✅ "I can build and run the application successfully"
- ✅ "I can see the expected API behavior"
- ✅ "Feature works as specified"
- ✅ "Tests pass and provide good coverage"

**If verification fails**: LLM must debug and fix before documentation.

### Phase 6: Documentation Update (FINAL STEP)

**⚠️ PREREQUISITE**: Documentation may ONLY begin after successful user verification from Phase 5.2.

**Update relevant documentation**:
- **Architecture docs** (`/docs/architecture/`): If architectural impact
- **Spec docs** (`/docs/spec/`): If data model or process changes  
- **API documentation**: If new endpoints or changes (e.g., placeholder behaviors)
- **README**: If user-facing changes

**Documentation focus**: Why decisions were made, architectural impact, integration points

### Phase 7: Git Commit (MANDATORY)

**⚠️ PREREQUISITE**: Must commit changes after documentation is complete.

#### 7.1 Commit Message Template

**Use this exact format**:

```
feat: implement issue [Issue Number] - [Brief description]

✅ IMPLEMENTED:
- [Key feature 1 - e.g., new REST endpoint, service layer logic]
- [Key feature 2 - e.g., schema changes, integration logic] 
- [Key feature 3 - e.g., validation, error handling, testing]

🔍 VERIFICATION:
- [How user can verify it works - API calls, queries]
- [Observable behavior/output - HTTP responses, log messages]

📋 INTEGRATION:
- [How it integrates with existing Spring Boot architecture]
- [Any dependencies or configuration requirements]
- [Testing approach and coverage]
```

#### 7.2 Commit Instructions

**The LLM MUST provide these exact git commands**:

```bash
# Stage all changes
git add .

# Commit with template message
git commit -m "feat: implement issue [Issue Number] - [Brief description]

✅ IMPLEMENTED:
- [List key features implemented]

🔍 VERIFICATION:
- [How to verify it works]

📋 INTEGRRATION:
- [Integration details]"

# Push changes (if working with remote)
git push origin main
```

#### 7.3 Example Commit Message

```
feat: implement issue 0.1.1 - basic chapter ingestion endpoint

✅ IMPLEMENTED:
- POST /api/chapters endpoint with content validation
- ChapterService with deduplication logic via content hashing
- IngestionJob entity with status tracking
- Input validation and error handling with proper HTTP status codes

🔍 VERIFICATION:
- Build: mvn clean install
- Run: mvn -pl lorevault-api spring-boot:run
- Test: POST to http://localhost:8080/api/chapters with chapter JSON
- Verify: Check response includes jobId and HTTP 202 status

📋 INTEGRATION:
- Follows CQRS pattern (command path implementation)
- Uses current persistence layer with job tracking
- Service-level tests with 90%+ coverage
- Ready for async processing pipeline integration
```

**CRITICAL**: LLM must provide specific commit instructions after every successful issue implementation.

## Decision Making Guidelines

**Reference**: See `/github/instructions/CORE_DEVELOPMENT_PHILOSOPHY.md` for comprehensive decision framework.

**When in doubt**: Refer to `/docs/spec/testing-strategy.md` for testing approach, `/docs/architecture/` for architectural patterns, and existing codebase for Spring Boot conventions.

## Quality Assurance

**Definition of Done**: Meets requirements → Works with existing system → Tests provide good coverage → Documentation updated → Follows project patterns → Integration verified.

## Anti-Patterns to Avoid

**❌ Don't**: Assume requirements, skip context gathering, implement without alternatives, ignore architectural patterns, skip testing.

**✅ Do**: Ask for clarification, gather full context, present alternatives with trade-offs, follow existing patterns, implement comprehensive testing.

**Remember**: The goal is shipping working features that integrate well with the existing architecture, not theoretical perfection.
