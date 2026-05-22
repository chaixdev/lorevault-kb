# LoreVault Core Development Philosophy

## Principle

**Balance clean, robust enterprise code with pragmatic delivery. Perfect is the enemy of great.**

## Decision Framework

When evaluating implementation approaches, always consider multiple options and select the one that best balances:

- **Code maintainability and clarity**
- **System robustness and error handling** 
- **Implementation complexity vs. benefit**
- **Integration with existing Spring Boot architecture**
- **Alignment with CQRS and hybrid AI processing patterns**

## When to Favor Different Approaches

### Favor Simplicity When:
- Building foundational features
- Complexity outweighs the benefit
- Risk of over-engineering is high
- Existing Spring Boot patterns work well
- Time-to-market is critical

### Favor Robustness When:
- Building user-facing API features
- Failure could cause data loss or corruption
- Integrating with external systems (AI APIs)
- Database consistency is critical
- Asynchronous processing reliability is important
- Security implications are significant

### Favor Flexibility When:
- Requirements are likely to evolve
- Building foundation for future features
- User configuration is involved
- Multiple external AI service integrations needed
- Multi-tenant considerations apply
- Extensibility is a key requirement

## Technical Debt Management

- **Document shortcuts**: Always explain why a simpler approach was chosen
- **Explain trade-offs**: Be explicit about what you're trading off
- **Prefer simple over "future-proof"**: Unless architectural implications are significant
- **Address when blocking**: Technical debt becomes priority when it blocks future implementation

## Spring Boot Specific Guidelines

### CQRS Architecture Alignment
- **Command Path**: Complex, asynchronous operations (ingestion, processing)
- **Query Path**: Simple, fast, direct database access
- Consider which path your feature affects and design accordingly

### Database Considerations
- **Schema Changes**: Use Flyway to create schemas consistently. While LoreVault remains in wipe-state development with no durable shipped schema, keep each schema area collapsed into one evolving `V1__...sql`; use Git history as development-stage versioning and reset local database/schema history after checksum changes. Add incremental `V2+` migrations only after a durable schema boundary is explicitly declared.
- **Transaction Boundaries**: Consider carefully, especially with async processing
- **pgvector Integration**: Leverage semantic search capabilities appropriately

### AI Integration Patterns
- **Local AI First**: Use for cost-effective pre-processing
- **External AI**: Reserve for complex reasoning and synthesis
- **Hybrid Approach**: Combine both for optimal cost/quality balance

## Code Quality Standards

### Spring Boot Patterns
- **Controllers**: RESTful endpoints with proper HTTP status codes
- **Services**: Business logic layer with transaction management
- **Repositories**: Data access with Spring Data JPA
- **DTOs**: Request/response objects with validation annotations
- **Configuration**: Spring configuration classes where needed

### Error Handling
- **Proper HTTP Status Codes**: Use semantically correct status codes
- **Graceful Degradation**: System should handle failures elegantly
- **Meaningful Error Messages**: Both for developers and users
- **Logging**: Use SLF4J with appropriate log levels

### Validation and Security
- **Input Sanitization**: Always validate and sanitize user input
- **Data Consistency**: Maintain referential integrity
- **Authorization**: Implement appropriate access controls
- **Rate Limiting**: Consider for user-facing APIs

## Testing Philosophy Reference

Follow the comprehensive testing strategy documented in `/docs/spec/testing-strategy.md`:

- **Service Layer Focus**: Primary testing at business logic level
- **Integration Testing**: For database interactions and workflows
- **Resource-Conscious**: Strategic use of infrastructure (Testcontainers)
- **90%+ Coverage**: For service layer business logic

## Documentation Standards

### Focus Areas
- **Why over What**: Explain decisions, not just implementation
- **Architectural Impact**: How does this affect the overall system?
- **Integration Points**: What does this connect to?
- **Trade-offs Made**: What alternatives were considered and why rejected?

### Update Requirements
- **Architecture docs** (`/docs/architecture/`): For architectural impact
- **Spec docs** (`/docs/spec/`): For data model or process changes
- **API documentation**: For new endpoints
- **README**: For user-facing changes

## Anti-Patterns to Avoid

### ❌ Don't:
- Assume requirements without clarification
- Skip context gathering before implementation
- Implement without considering alternatives
- Ignore existing architectural patterns
- Break API backward compatibility
- Skip comprehensive testing
- Ignore database transaction boundaries
- Over-engineer without clear benefit
- Under-engineer critical reliability features

### ✅ Do:
- Ask for clarification when requirements are unclear
- Gather full architectural context before designing
- Present alternatives with clear trade-offs
- Follow existing Spring Boot best practices
- Consider CQRS command vs query implications
- Implement proper error handling and validation
- Maintain API backward compatibility
- Handle database transactions appropriately
- Follow the established testing strategy

## Core Principle Reminder

**The goal is shipping working features that integrate well with the existing Spring Boot architecture and follow enterprise patterns, not achieving theoretical perfection.**

Focus on:
1. **Working software** over comprehensive documentation
2. **Integration** over isolated perfection  
3. **Practical solutions** over theoretical ideals
4. **Maintainable code** over clever implementations
5. **Business value** over technical sophistication
