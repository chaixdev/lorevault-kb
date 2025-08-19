# Testing Strategy v2.0 - LLM Development Instructions

**Purpose**: Scalable testing strategy for LLM-assisted development in ports & adapters architecture.

**Scope**: Testing patterns, organization, and quality gates for the LoreVault GraphRAG system.

## Core Philosophy

### Architectural Testing Discipline
- **Domain-First**: Test business logic in isolation with mocked ports
- **Port TCKs**: Every port must have reusable contract tests
- **Fake-First**: Use in-memory fakes for rapid iteration, real adapters for validation
- **Architecture Enforcement**: Use ArchUnit to prevent hexagonal boundary violations

### LLM Agent Guidelines
- Generate tests alongside implementation code
- Focus on service-level behavioral tests over granular units
- Use realistic test data scenarios (TestDataBuilder patterns)
- Mock expensive external calls (LLMs, APIs, databases in unit tests)

## Test Architecture

### Testing Pyramid (Ports & Adapters)

e2e out of scope.

```
  Integration Tests (20%) - Port contracts + DB
 ───────────────────────────────────────────────
Service + Domain Tests (75%) - Business logic
```

### Test Categories with JUnit 5 Tags
- `@Tag("unit")` - Service logic with mocked ports (run on save)
- `@Tag("integration")` - Real DB + Spring context (run on commit)  
- `@Tag("system")` - Full application (run in CI)

## Test Organization Patterns

### Class Structure
```java
@DisplayName("Service Name - Feature Description")
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ServiceNameTest {
    
    @DisplayName("Feature Group")
    @Nested
    class FeatureGroup {
        
        @DisplayName("Should behave correctly when condition")
        @Test
        void condition_ShouldBehaveCorrectly() {
            // Test implementation
        }
    }
}
```

### Package Organization
```
src/test/java/
├── service/           # Business logic tests (@Tag("unit"))
├── controller/        # API integration tests (@Tag("integration")) 
├── adapter/          # Port implementation tests
├── tck/              # Port contract definitions (reusable)
├── domain/           # Domain model tests
└── testutil/         # Test builders and utilities
```

### Naming Conventions
- **Service Tests**: `ServiceNameTest.java`
- **Integration**: `ComponentNameIT.java` 
- **Port TCKs**: `PortNameTCK.java`
- **TCK Implementations**: `AdapterNameTCKTest.java`

## Implementation Patterns

### Service Layer Testing (Primary)
- Mock all ports using `@Mock` annotations
- Focus on business behavior, not implementation
- Use `TestDataBuilder` for realistic scenarios
- Verify port interactions for integration flows

### Port Technology Compatibility Kits (TCKs)
- Create abstract test classes for every port interface
- Run same contract tests against all implementations (fake, JPA, etc.)
- Eliminates duplication and ensures adapter conformity

### Integration Testing Guidelines  
- Use Spring test slices: `@DataJpaTest`, `@WebMvcTest`
- Apply Testcontainers selectively for real infrastructure needs
- Enable container reuse: `testcontainers.reuse.enable=true`
- Focus on critical workflows, not exhaustive coverage

**When to Use Testcontainers**:
- ✅ Database persistence logic, complex queries, transaction behavior
- ❌ Service layer business logic, simple request/response validation

**Container Optimization**:
```java
@Testcontainers
public abstract class IntegrationTestBase {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withReuse(true); // Share containers across test classes
}

### Property-Based Testing
- Use jqwik `@Property` tests for domain invariants
- Test value object creation rules and constraints
- Validate parsing and transformation logic

## Test Data Strategy

### Centralized Builders
- **Deterministic**: Fixed clocks (`Clock.fixed()`) and ID generators
- **Realistic**: Domain-specific scenarios (Matrix examples, etc.)
- **Reusable**: `TestDataBuilder` utility class
- **Focused**: Specific builders per test category

### Key Patterns
- Use `SampleChapterLoader` for complex domain objects
- Inject `Clock` and `IdGenerator` to avoid non-deterministic tests
- Create scenario-based test data (not random)

### Assertion Best Practices
- **Descriptive**: `assertThat(response.getJobId()).isNotNull()`
- **Business Rules**: `assertThat(firstResponse.getChapterId()).isEqualTo(secondResponse.getChapterId())` // Same content = same chapter
- **Complete Workflows**: Verify end-to-end scenarios, not just individual method calls

## LLM-Specific Testing

### GraphRAG Component Testing
- **Mock LLM calls** to focus on retrieval logic testing
- **Pre-load test graphs** with known data for deterministic results
- **Test context quality** (retrieved entities/relationships), not LLM output
- **Verify prompt construction** using ArgumentCaptor patterns

### AI Component Reliability
- Test timeout handling with `@Timeout` annotations
- Verify retry/circuit breaker logic with mock failures
- Test authentication and API key validation

## Quality Gates

### Required Thresholds
- **Unit Test Coverage**: 85%+ 
- **Mutation Testing**: 80%+ (PIT configuration)
- **Architecture Violations**: 0 (ArchUnit rules)
- **Test Execution**: Keep builds fast, prioritize feedback speed

### Development Workflow
1. **Service Test First**: Write service-level tests to define business behavior
2. **Implementation**: Implement business logic to satisfy service tests  
3. **Integration Validation**: Add integration tests for database interactions
4. **Edge Case Coverage**: Add contract tests for complex edge cases

### Build Pipeline Integration
```bash
# Developer workflow
mvn test -Dgroups=unit

# Pre-commit  
mvn verify -Dgroups="unit,integration"

# CI pipeline
mvn verify -Dgroups="unit,integration,system" -Dpitest.mutationThreshold=80
```

## Advanced Patterns

### Architecture Testing (ArchUnit)
- Enforce hexagonal boundaries (domain independent of infrastructure)
- Validate port interfaces and adapter dependencies  
- Ensure test naming conventions and package structure

### Contract Testing
- Use Pact for external API integration contracts
- Validate adapter behavior against port expectations
- Test failure scenarios and resilience patterns

### Performance Testing
- Add `@Tag("performance")` for regression tests
- Use `@Timeout` annotations for SLA validation
- Test scalability with parameterized result counts

### Test Maintenance Guidelines

**When to Update Tests**:
- **Service Tests**: When business rules change
- **Integration Tests**: When API contracts or database schema change  
- **Contract Tests**: When data models or validation rules change

**Refactoring Support**: Tests should focus on behavior, not implementation details - this supports refactoring without breaking tests unnecessarily

## LLM Agent Checklist

When generating/modifying tests:
- [ ] Apply appropriate `@Tag` annotation
- [ ] Use `@DisplayName` for specification-like documentation  
- [ ] Organize related tests with `@Nested` classes
- [ ] Mock ports in service tests, use fakes when possible
- [ ] Create realistic test scenarios with `TestDataBuilder`
- [ ] For GraphRAG: mock LLM calls, test retrieval logic
- [ ] Add `@Timeout` for operations with SLA requirements
- [ ] Follow naming conventions and package structure
- [ ] Generate Port TCK when creating new port interfaces
- [ ] Ensure deterministic test data (no random values)
- [ ] Write descriptive assertions that validate business rules
- [ ] Focus tests on behavior, not implementation details
- [ ] Use container reuse for integration tests to optimize build times

This strategy emphasizes architectural discipline while maintaining developer velocity through fast feedback loops and comprehensive automation.
