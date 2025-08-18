# Testing Strategy Specification

**Purpose**: Define a comprehensive testing strategy that prioritizes abstraction-level testing, business intent communication, and judicious use of infrastructure resources.

**Scope**: Testing methodology, test categorization, tooling guidelines, and implementation patterns for the LoreVault system.

**Dependencies**: Architecture documentation, core data model specification, development workflow guidelines.

## Core Testing Philosophy

### Abstraction-Level Testing Preference

We prioritize testing at meaningful abstraction levels rather than granular unit testing for several key reasons:

1. **Stability**: Higher-level tests are more resilient to internal refactoring and implementation changes
2. **Business Intent**: Tests communicate business requirements and user scenarios more clearly
3. **Self-Documentation**: Test scenarios serve as living documentation of system behavior
4. **Integration Confidence**: Validates actual system behavior rather than isolated components

### Resource-Conscious Infrastructure Usage

Infrastructure resources (Testcontainers, external services) should be used strategically:

- **Reserve for Database Interactions**: Use real databases only when testing actual persistence logic
- **Mock Everything Else**: Prefer mocking for services, external APIs, and non-persistence components
- **Optimize Build Times**: Minimize container startup overhead through judicious usage patterns

## Test Categorization Framework

### 1. Service Layer Tests (Primary Focus)

**Purpose**: Test business logic at the service abstraction level
**Approach**: Mock repositories and external dependencies, focus on business rules
**Tooling**: Mockito for mocking, realistic test data setup
**Scope**: Single service with all dependencies mocked

```java
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {
    @Mock private ChapterRepository chapterRepository;
    @Mock private JobRepository jobRepository;
    @InjectMocks private IngestionService ingestionService;
    
    // Test business logic: deduplication, job creation, status management
}
```

**Test Scenarios**:
- Business rule validation (e.g., duplicate content handling)
- State transition logic (e.g., job lifecycle management)
- Error handling and recovery scenarios
- Data transformation and validation
- Complex business workflows

### 2. Integration Tests (Database Interactions)

**Purpose**: Validate actual database operations and data persistence
**Approach**: Use Testcontainers with production-matching database
**Tooling**: Spring Boot Test + Testcontainers + real database
**Scope**: Full Spring context with real database, mocked external services

```java
@SpringBootTest
@Testcontainers
class IngestionControllerIntegrationTest extends IntegrationTestBase {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
    
    // Test complete request flows with real database persistence
}
```

**Test Scenarios**:
- End-to-end request/response flows
- Database constraint validation
- Transaction behavior and rollback scenarios
- Data consistency across related entities
- Performance with realistic data volumes

### 3. Contract Tests (Minimal Unit Testing)

**Purpose**: Validate critical interfaces and data contracts
**Approach**: Fast, isolated tests for specific contracts
**Tooling**: Plain JUnit, minimal setup
**Scope**: Individual classes with critical boundary logic

**Use Cases**:
- DTO validation and serialization
- Custom validation logic
- Utility functions with complex logic
- Critical algorithm implementations

### 4. End-to-End Tests (System Validation)

**Purpose**: Validate complete system behavior from user perspective
**Approach**: Full application with all real components
**Tooling**: TestContainers + MockMvc or WebTestClient
**Scope**: Complete application stack

**Test Scenarios**:
- Critical user journeys
- System-wide error handling
- Performance under load
- Security and authorization flows

## Implementation Guidelines

### Test Data Management

#### Realistic Test Data Strategy

Use the existing `SampleChapterLoader` pattern for meaningful test data:

```java
@UtilityClass
public class TestDataBuilder {
    public static SubmitChapterRequest buildRealisticChapter() {
        return SampleChapterLoader.loadSampleChapter("kevin_jenkins");
    }
    
    public static Chapter buildPersistedChapter() {
        // Create pre-saved test entities
    }
}
```

#### Data Setup Patterns

**For Service Tests**: Use builder patterns and mocked returns
```java
when(chapterRepository.findByContentHash(anyString()))
    .thenReturn(Optional.of(buildExistingChapter()));
```

**For Integration Tests**: Use realistic data with proper cleanup
```java
@Transactional
@Rollback
class IntegrationTest {
    // Tests automatically rollback, ensuring clean state
}
```

### Testcontainer Usage Guidelines

#### When to Use Testcontainers

✅ **Appropriate Usage**:
- Testing database persistence logic
- Validating complex queries and constraints
- Testing transaction behavior
- Integration test scenarios requiring real data

❌ **Avoid For**:
- Testing business logic that doesn't touch the database
- Simple request/response validation
- Service layer unit tests
- Quick feedback development cycles

#### Optimization Strategies

```java
// Base class pattern for shared container management
@Testcontainers
public abstract class IntegrationTestBase {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withReuse(true); // Share containers across test classes
            
    // Common setup for all database-dependent tests
}

// Focused test base for specific infrastructure (Neo4j example)
@DataNeo4jTest
public abstract class Neo4jIntegrationTestBase {
    private static final Neo4jContainer<?> neo4j = SharedNeo4jTestContainer.getInstance();
    
    @DynamicPropertySource
    static void configureNeo4j(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
    }
}
```

**Container Reuse Configuration**:
```properties
# ~/.testcontainers.properties
testcontainers.reuse.enable=true
```

### Test Organization Structure

```
src/test/java/
├── com/lorevault/api/
│   ├── service/                    # Service layer tests (primary focus)
│   │   ├── IngestionServiceTest.java
│   │   ├── ChapterServiceTest.java
│   │   └── ValidationServiceTest.java
│   ├── controller/                 # Integration tests
│   │   ├── IngestionControllerIntegrationTest.java
│   │   └── HealthControllerIntegrationTest.java
│   ├── repository/                 # Database contract tests
│   │   ├── ChapterRepositoryTest.java
│   │   └── JobRepositoryTest.java
│   ├── model/                      # Data contract tests
│   │   └── PublicationCoordinatesTest.java
│   ├── testutil/                   # Test utilities
│   │   ├── SampleChapterLoader.java
│   │   ├── TestDataBuilder.java
│   │   └── MockConfigurationHelper.java
│   └── test/                       # Base test classes
│       ├── IntegrationTestBase.java
│       └── Neo4jIntegrationTestBase.java
```

### Naming Conventions

#### Test Class Naming
- **Service Tests**: `[ServiceName]Test.java`
- **Integration Tests**: `[Component]IntegrationTest.java`
- **Contract Tests**: `[Model/Interface]Test.java`

#### Test Method Naming
Use descriptive, behavior-focused names:
```java
// Good: Describes behavior and expected outcome
void submitChapter_WhenContentExists_ShouldCreateNewJobForExistingChapter()
void getJobStatus_WhenJobNotFound_ShouldReturnEmpty()

// Avoid: Implementation-focused names
void testSubmitChapter()
void shouldReturnStatus()
```

### Assertion Patterns

#### Prefer Descriptive Assertions
```java
// Good: Clear intent and comprehensive validation
assertThat(response.getJobId()).isNotNull();
assertThat(response.getChapterId()).isEqualTo(existingChapter.getId());
assertThat(response.getMessage()).contains("submitted successfully");

// Good: Business rule validation
assertThat(firstResponse.getChapterId())
    .isEqualTo(secondResponse.getChapterId()); // Same content = same chapter
assertThat(firstResponse.getJobId())
    .isNotEqualTo(secondResponse.getJobId()); // Different processing jobs
```

#### Validation Strategies
```java
// Validate complete workflows
@Test
void submitSampleChapter_ShouldCompleteFullWorkflow() {
    // Submit chapter
    SubmitChapterResponse response = submitChapter(sampleChapter);
    
    // Verify immediate response
    assertThat(response.getJobId()).isNotNull();
    
    // Verify job completion
    JobStatusResponse status = getJobStatus(response.getJobId());
    assertThat(status.getIsComplete()).isTrue();
    assertThat(status.getProgressPercent()).isEqualTo(100);
}
```

## Testing Workflow Integration

### Development Workflow

1. **Service Test First**: Write service-level tests to define business behavior
2. **Implementation**: Implement the business logic to satisfy service tests
3. **Integration Validation**: Add integration tests for database interactions
4. **Edge Case Coverage**: Add specific contract tests for complex edge cases

### Continuous Integration Considerations

#### Test Execution Strategy
```yaml
# CI Pipeline test stages
stages:
  - fast-tests:     # Service and contract tests (~30 seconds)
      - Service layer tests
      - Model validation tests
      - Quick contract tests
  
  - integration:    # Database integration tests (~2-3 minutes)
      - Database interaction tests
      - Full workflow validation
  
  - system:         # End-to-end tests (~5-10 minutes)
      - Complete system validation
      - Performance benchmarks
```

#### Resource Management
- **Parallel Execution**: Service tests run in parallel (no shared resources)
- **Sequential Integration**: Database tests run sequentially (shared container state)
- **Container Optimization**: Reuse containers where possible

### Test Maintenance Guidelines

#### When to Update Tests

**Service Tests**: Update when business rules change
**Integration Tests**: Update when API contracts or database schema change
**Contract Tests**: Update when data models or validation rules change

#### Refactoring Support

Tests should support refactoring by focusing on behavior rather than implementation:

```java
// Good: Tests behavior, resilient to implementation changes
void submitChapter_ShouldHandleDuplicateContent() {
    // Focus on input/output behavior, not internal implementation
}

// Avoid: Tightly coupled to implementation details
void submitChapter_ShouldCallRepositoryFindMethod() {
    // Breaks when implementation changes
}
```

## Quality Metrics and Goals

### Test Coverage Guidelines

- **Service Layer**: 90%+ coverage of business logic paths
- **Integration Flows**: 100% coverage of critical user journeys
- **Error Handling**: 100% coverage of error scenarios
- **Edge Cases**: Focus on business-critical edge cases

### Performance Targets

- **Service Tests**: < 5 seconds total execution time
- **Integration Tests**: < 2 minutes total execution time
- **Full Test Suite**: < 5 minutes total execution time
- **Container Startup**: < 30 seconds per test class

### Maintenance Metrics

- **Test Stability**: < 1% flaky test rate
- **Maintenance Overhead**: Tests should not require updates for internal refactoring
- **Documentation Value**: Tests should clearly communicate business intent

## Migration Strategy

### Current State Assessment

Existing test structure shows good patterns:
- `IngestionServiceTest`: Excellent service-level testing with Mockito
- `IngestionControllerIntegrationTest`: Good integration testing with Testcontainers
- `SampleChapterLoader`: Realistic test data management

### Improvement Opportunities

1. **Expand Service Testing**: Add service tests for all business logic components
2. **Optimize Container Usage**: Implement container reuse and selective usage
3. **Add Contract Tests**: Create focused tests for critical data contracts
4. **Enhance Test Data**: Expand realistic test data scenarios

### Implementation Phases

**Phase 1**: Optimize existing tests (container reuse, performance)
**Phase 2**: Expand service-level test coverage
**Phase 3**: Add contract tests for critical interfaces
**Phase 4**: Implement comprehensive test data management

## Conclusion

This testing strategy prioritizes meaningful, stable tests that communicate business intent while optimizing resource usage. By focusing on abstraction-level testing and judicious use of infrastructure resources, we create a test suite that supports rapid development while maintaining high confidence in system behavior.

The strategy emphasizes practical testing patterns that have proven effective in Spring Boot applications, balancing comprehensiveness with maintainability and build performance.
