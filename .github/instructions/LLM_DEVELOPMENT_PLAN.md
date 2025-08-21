# LLM Development Plan for LoreVault Issue Implementation

## Core Development Philosophy

**See**: `/github/instructions/CORE_DEVELOPMENT_PHILOSOPHY.md` for comprehensive development guidelines.

**Quick Reference**: Balance clean, robust enterprise code with pragmatic delivery. Consider multiple approaches and select based on maintainability, robustness, complexity, and architectural alignment.

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
**Testing Strategy**: [Service-level tests, integration needs, mock requirements]
**Pros**: [Key advantages]
**Cons**: [Key limitations]  
**Complexity**: [Low/Medium/High]
**Integration Impact**: [Minimal/Moderate/Significant]
**CQRS Impact**: [Command/Query/Both]

### Approach 2: [Name]
[Same format as above - include Testing Strategy for each approach]

### Approach 3: [Name]
[Same format as above - include Testing Strategy for each approach]
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

### Phase 4: Test-Driven Implementation (SYSTEMATIC)

**⚠️ PREREQUISITE**: Implementation may ONLY begin after receiving explicit user approval from Phase 2.3.

#### 4.1 Test Design First (MANDATORY)

**Follow the Testing Strategy**: Refer to `/docs/development/testing/developer-testing-workflow.md` for the complete testing workflow, Maven commands, and quality gates.

**Key Principles**:
- Service-level tests are the primary focus (business behavior validation)
- Integration tests only when persistence/external contracts matter
- Use proper test tags: `@Tag("unit")`, `@Tag("integration")`, `@Tag("architecture")`
- Follow hexagonal architecture testing patterns (domain at center, mock ports)

**Test Structure Template**:
```java
@ExtendWith(MockitoExtension.class)
class [ServiceName]Test {
    @Mock private [Repository] repository;
    @Mock private [ExternalService] externalService;
    @InjectMocks private [ServiceName] service;
    
    // Test business logic, state transitions, error handling
}
```

#### 4.2 Implementation Workflow

1. **Write Service Tests First**: Define expected business behavior through tests
2. **Implement Business Logic**: Code to satisfy service tests
3. **Add Integration Tests**: Only if persistence/external integration is core to the feature
4. **Edge Case Coverage**: Add specific tests for complex edge cases
5. **Test Cleanup**: Ensure no unnecessary mocking, clean test structure

**Persistence Changes**: If needed, create appropriate migration or schema update artifacts (e.g., graph refactor scripts, index creation guidance). Avoid premature vector/embedding scaffolding before v0.5.0.

**Spring Boot Patterns**: Follow existing patterns for Controllers, Services, Repositories, DTOs

### Phase 5: Test Verification & Validation (MANDATORY)

**⚠️ CRITICAL**: All tests must pass and provide meaningful coverage before user verification.

#### 5.1 🧪 TEST VALIDATION GATE: "Tests Define and Verify Behavior"

**The LLM MUST verify test quality and coverage using the workflow from `/docs/development/testing/developer-testing-workflow.md`**:

```markdown
🔍 TEST VALIDATION CHECKLIST FOR ISSUE [Issue Number]

### Maven Commands (per developer-testing-workflow.md)
1. **Fast Loop**: `mvn test` - Unit tests only (default development cycle)
2. **Integration Check**: `mvn verify -P integration-tests` - Unit + integration tests  
3. **Coverage Gate**: `mvn verify -P coverage-gate` - Enforce strict coverage thresholds
4. **Architecture Validation**: `mvn test -P architecture-tests` - ArchUnit boundary enforcement

### Test Quality Assessment
1. **Business Intent**: Do tests clearly communicate what the feature does?
2. **Realistic Data**: Are tests using realistic test data scenarios?
3. **Proper Tags**: Are tests tagged correctly (`@Tag("unit")`, `@Tag("integration")`)?
4. **Test Maintenance**: Are tests structured for easy maintenance and understanding?

### Success Criteria
- ✅ **Fast Loop Passes**: `mvn test` completes successfully
- ✅ **Service-Level Coverage**: Main business workflows tested with mocked dependencies
- ✅ **Integration Points**: Database/external service interactions validated (if applicable)
- ✅ **Test Clarity**: Tests serve as living documentation of requirements
```

#### 5.2 🛑 USER VERIFICATION GATE: "Show Me It Works"

**After test validation passes, provide user verification instructions**:

```markdown
🔍 USER VERIFICATION INSTRUCTIONS FOR ISSUE [Issue Number]

### Build & Run (using developer-testing-workflow.md commands)
1. **Build**: `mvn clean install` (from project root)
2. **Run**: `mvn -pl lorevault-api spring-boot:run -Dspring-boot.run.profiles=dev` (start application)  
3. **Test API**: Use provided curl commands or test endpoints
4. **Monitor**: Check application logs for expected behavior

### Verification Steps  
1. **[Step 1]**: [Action] → [Expected result]
2. **[Step 2]**: [Action] → [Expected result]
3. **[Step 3]**: [Action] → [Expected result]

### Success Indicators
- ✅ [Observable success indicator 1]
- ✅ [Observable success indicator 2] 
- ✅ [Observable success indicator 3]
```

#### 5.3 User Confirmation Protocol

**User must confirm before documentation**:
- ✅ "All tests pass with good coverage and clear business intent"
- ✅ "I can build and run the application successfully"
- ✅ "I can see the expected API behavior" 
- ✅ "Feature works as specified"

**If verification fails**: LLM must debug and fix before documentation.

### Phase 6: Documentation Update (FINAL STEP)

**⚠️ PREREQUISITE**: Documentation may ONLY begin after successful test validation and user verification from Phase 5.

**Update relevant documentation**:
- **Architecture docs** (`/docs/architecture/`): If architectural impact
- **Spec docs** (`/docs/spec/`): If data model or process changes
- **API documentation**: If new endpoints or changes (e.g., placeholder behaviors)  
- **Testing documentation**: Update testing examples if new patterns introduced
- **README**: If user-facing changes

**Documentation focus**: Why decisions were made, architectural impact, integration points, testing approach

### Phase 7: Git Commit (MANDATORY)

**⚠️ IMPORTANT**: Must commit changes after documentation is complete.


####  Commit Instructions

**The LLM MUST provide these exact git commands**:

```bash
# Stage all changes
git add .

# Commit with template message  
git commit -m "
{{A compact, high level summary of all changes in this commit}}
"

# Push changes (if working with remote)
git push origin main
```

## Decision Making Guidelines

**Reference**: See `/github/instructions/CORE_DEVELOPMENT_PHILOSOPHY.md` for comprehensive decision framework.

**Testing Reference**: See `/docs/development/testing/developer-testing-workflow.md` for detailed testing workflow, Maven profiles, and quality gates.

**When in doubt**: Refer to `/docs/architecture/` for architectural patterns and existing codebase for Spring Boot conventions.

## Quality Assurance

**Definition of Done**: Meets requirements → **Tests define and verify business behavior** → Works with existing system → **Test coverage provides confidence and documentation** → Documentation updated → Follows project patterns → Integration verified.

**Testing Quality Standards**:
- Service-level tests communicate business intent clearly
- Tests use realistic data and scenarios
- Mock strategy follows testing-strategy.md guidelines  
- Integration tests only where persistence/external contracts matter
- Test maintenance considerations (readability, stability)

## Anti-Patterns to Avoid

**❌ Don't**: Assume requirements, skip context gathering, implement without alternatives, ignore architectural patterns, **implement before writing tests**, **over-mock or under-mock dependencies**.

**✅ Do**: Ask for clarification, gather full context, present alternatives with trade-offs, follow existing patterns, **write service tests first**, **implement comprehensive but practical testing**.

**Testing Anti-Patterns**:
- ❌ **Testing implementation details** instead of business behavior
- ❌ **Excessive unit testing** without service-level coverage  
- ❌ **Infrastructure overuse** (Testcontainers everywhere)
- ❌ **Unmaintainable test code** with complex setup

**Remember**: The goal is shipping working features with **test-verified business behavior** that integrate well with the existing architecture, not theoretical perfection.
