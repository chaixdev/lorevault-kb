# Ports & Adapters Standards Guide

## Core Principles

### 1. **Dependency Direction Rule**
- **Domain** depends on nothing (pure business logic)
- **Application** depends only on Domain + Ports (interfaces)
- **Infrastructure** depends on Application + Domain (implements ports)
- **Web** depends on Application + Domain (calls services)

### 2. **Naming Conventions**

#### Ports (Interfaces)
- **Suffix**: `Port` (e.g., `ContentPersistencePort`, `SceneDetectionPort`)
- **Location**: `application/port/` package
- **Purpose**: Define what the application needs from external systems

#### Adapters (Implementations)  
- **Suffix**: `Adapter` (e.g., `Neo4jContentPersistenceAdapter`, `OpenAiSceneDetectionAdapter`)
- **Location**: `infrastructure/{technology}/` package
- **Purpose**: Implement ports using specific technologies

#### Services (Business Logic)
- **Suffix**: `Service` (e.g., `IngestionService`, `ContentService`)
- **Location**: `application/service/` package  
- **Purpose**: Orchestrate business workflows using ports

### 3. **Package Organization Standards**

```
com.lorevault.api/
├── domain/                    # Pure business logic - no dependencies
│   ├── content/              # Entities: Chapter, Scene, Chunk
│   ├── ingestion/            # Entities: IngestionJob, StatusRecord  
│   └── shared/               # Value objects: PublicationCoordinates
├── application/              # Application layer - depends on domain + ports
│   ├── port/                 # Outbound interfaces
│   ├── service/              # Business logic services
│   └── usecase/              # Optional: complex workflows
├── infrastructure/           # External concerns - implements ports
│   ├── persistence/
│   │   ├── neo4j/           # Neo4j implementation
│   │   └── postgres/        # Future SQL implementation  
│   ├── ai/
│   │   ├── openai/          # OpenAI adapter
│   │   └── local/           # Local model adapter
│   ├── messaging/           # Event publishing
│   └── config/              # Infrastructure configuration
└── web/                     # Inbound adapters - calls application services
    ├── controller/          # REST endpoints
    ├── dto/                 # Web DTOs
    └── config/              # Web configuration
```

### 4. **Interface Design Standards**

#### Port Interface Characteristics
```java
public interface SomePort {
    // ✅ Good: Business-focused method names
    List<Chapter> findChaptersInUniverse(String universe);
    
    // ❌ Avoid: Technology-specific names  
    List<ChapterNode> findChapterNodesByUniverseProperty(String universe);
    
    // ✅ Good: Domain types in signatures
    Chapter save(Chapter chapter);
    
    // ❌ Avoid: Infrastructure types in signatures
    ChapterNode save(ChapterNode node);
    
    // ✅ Good: Meaningful exceptions
    Chapter findById(UUID id) throws ChapterNotFoundException;
    
    // ❌ Avoid: Generic exceptions
    ChapterNode findById(UUID id) throws Exception;
}
```

#### Service Dependencies Standards
```java
@Service
public class IngestionService {
    // ✅ Good: Depend on ports (interfaces)
    private final ContentPersistencePort persistencePort;
    private final SceneDetectionPort sceneDetectionPort;
    private final EmbeddingPort embeddingPort;
    
    // ❌ Avoid: Depend on concrete adapters
    private final Neo4jContentPersistenceAdapter neo4jAdapter;
    private final OpenAiSceneDetectionAdapter openAiAdapter;
}
```

### 5. **Testing Standards**

#### Service Tests (Unit)
```java
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {
    // ✅ Good: Mock ports (interfaces)
    @Mock private ContentPersistencePort persistencePort;
    @Mock private SceneDetectionPort sceneDetectionPort;
    
    @InjectMocks private IngestionService ingestionService;
    
    // Test business logic without infrastructure concerns
}
```

#### Adapter Tests (Integration)
```java
@SpringBootTest
class Neo4jContentPersistenceAdapterIT extends IntegrationTestBase {
    // ✅ Good: Test concrete adapter with real infrastructure
    @Autowired private ContentPersistencePort persistencePort; // Autowire interface
    
    // Test actual database interactions
}
```

### 6. **Configuration Standards**

#### Port Implementation Selection
```java
@Configuration
public class PortConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "lorevault.ai.scene-detection.provider", havingValue = "openai")
    public SceneDetectionPort openAiSceneDetectionPort() {
        return new OpenAiSceneDetectionAdapter(/* dependencies */);
    }
    
    @Bean  
    @ConditionalOnProperty(name = "lorevault.ai.scene-detection.provider", havingValue = "local")
    public SceneDetectionPort localSceneDetectionPort() {
        return new LocalSceneDetectionAdapter(/* dependencies */);
    }
}
```

### 7. **Migration Checklist**

#### For Each Service:
- [ ] Remove direct infrastructure dependencies
- [ ] Add port dependencies via constructor injection
- [ ] Update tests to mock ports instead of concrete classes
- [ ] Verify service only uses domain objects + port interfaces

#### For Each Port:
- [ ] Interface uses domain types in method signatures
- [ ] Methods are business-focused, not technology-focused
- [ ] Proper exception handling defined
- [ ] Documentation includes business intent

#### For Each Adapter:
- [ ] Implements a port interface
- [ ] Located in appropriate infrastructure package
- [ ] Handles translation between domain and infrastructure types
- [ ] Has integration tests with real infrastructure

### 8. **Benefits Validation**

After migration, verify these improvements:

#### ✅ **Testability**
- Services can be unit tested with mocked ports
- Adapters can be integration tested independently
- Clear separation between business logic and infrastructure tests

#### ✅ **Flexibility**  
- Can swap implementations via configuration
- New adapters can be added without changing services
- Technology choices become implementation details

#### ✅ **Maintainability**
- Business logic isolated from infrastructure concerns
- Clear boundaries and responsibilities
- Easier to understand and modify

## Common Anti-Patterns to Avoid

### ❌ **Leaky Abstractions**
```java
// Bad: Infrastructure concerns leak into service
public interface BadPort {
    Neo4jResult executeNeo4jQuery(String cypher); // Neo4j-specific
    JpaEntity saveEntity(JpaEntity entity);        // JPA-specific
}
```

### ❌ **Port Proliferation**
```java
// Bad: Too many tiny, single-method ports
public interface FindChapterPort { Optional<Chapter> findById(UUID id); }
public interface SaveChapterPort { Chapter save(Chapter chapter); }
public interface DeleteChapterPort { void deleteById(UUID id); }

// Good: Cohesive port grouping related operations
public interface ContentPersistencePort {
    Optional<Chapter> findChapterById(UUID id);
    Chapter saveChapter(Chapter chapter);
    void deleteChapter(UUID id);
    // ... other related persistence operations
}
```

### ❌ **Anemic Domain Models**
```java
// Bad: Domain objects with no behavior
public class Chapter {
    private String text;
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}

// Good: Domain objects with business methods
public class Chapter {
    private String text;
    public boolean hasContent() { return text != null && !text.trim().isEmpty(); }
    public int getWordCount() { return text.split("\\s+").length; }
    public String generateContentHash() { /* business logic */ }
}
```
