# CQRS Query-Side Design Exploration

*Author: Technical Advisory*  
*Date: August 20, 2025*  
*Context: Learning project - exploring intent-driven CQRS query endpoints for UI development*

## Decision: Double Down on CQRS Query-Side Pattern

**Rationale:** This is a learning project, and we've already committed to CQRS. Let's fully explore the intent-driven query paradigm and discover its limits, tradeoffs, and developer experience friction firsthand.

## Intent-Driven Query Endpoint Design

### Core Principle
Each query endpoint should express a **user/UI intent**, not a generic data access pattern.

**Instead of:**
```http
GET /api/entities/characters/123
GET /api/entities/123/relationships  
GET /api/entities/123/mentions
```

**Design for UI intent:**
```http
GET /api/query/character-detail-view/123
GET /api/query/relationship-explorer/123  
GET /api/query/timeline-view?focus=character:123
```

## Proposed Query Endpoint Categories

### 1. **Detail Views** (Entity-focused)
```http
# Character detail page
GET /api/query/character-detail-view/{id}
  ?spoilerLevel=book2
  &includeTimeline=true
  &maxRelations=10

# Location detail page  
GET /api/query/location-detail-view/{id}
  ?spoilerLevel=book2
  &includeEvents=true
  &includeResidents=true

# Response optimized for character detail UI
{
  "character": { "id", "name", "description", "status" },
  "recentMentions": [...],  // Last 5, with context
  "keyRelations": [...],    // Top 10 by strength/importance
  "timeline": [...],        // Character-specific events
  "conflictsCount": 2,      // For UI badges
  "lastUpdated": "2025-08-20T10:30:00Z"
}
```

### 2. **List/Browse Views** (Collection-focused)
```http
# Character browser/list page
GET /api/query/character-browser
  ?universe=cosmere
  &status=alive,unknown
  &hasConflicts=true
  &sortBy=lastMentioned
  &page=1&limit=20

# Entity search across types
GET /api/query/entity-search
  ?q=bridge four
  &types=character,location,faction
  &spoilerLevel=book2
  &limit=50
```

### 3. **Exploration Views** (Relationship-focused)
```http
# Relationship explorer (graph view)
GET /api/query/relationship-explorer/{entityId}
  ?depth=2
  &relationTypes=friend,family,enemy
  &includeStrength=true

# Timeline view (temporal relationships)
GET /api/query/timeline-view
  ?universe=cosmere
  &scope=character:kaladin
  &spoilerLevel=book2
  &eventTypes=battle,meeting,discovery
```

### 4. **Search & Discovery**
```http
# Semantic search results
POST /api/query/semantic-search
{
  "query": "bridge crew training scenes",
  "spoilerLevel": "book2", 
  "resultTypes": ["scene", "character", "location"],
  "limit": 20
}

# Autocomplete/suggestions
GET /api/query/suggestions
  ?q=kal
  &types=character,location
  &universe=cosmere
```

### 5. **Dashboard/Summary Views**
```http
# Universe overview (dashboard)
GET /api/query/universe-dashboard/{universeId}
  ?spoilerLevel=book2

# Conflict resolution dashboard
GET /api/query/conflict-dashboard
  ?universe=cosmere
  &status=pending
  &assignedTo=me
```

## Response Design Principles

### 1. **UI-Optimized Projections**
Each endpoint returns exactly what that UI screen needs, pre-computed where possible.

```json
{
  "primaryData": { /* Main entity/data */ },
  "relatedData": { /* Supporting data for UI */ },
  "metadata": {
    "spoilerLevel": "book2",
    "lastUpdated": "2025-08-20T10:30:00Z",
    "conflicts": 0,
    "processingTime": "45ms"
  },
  "uiHints": {
    "hasMore": true,
    "nextPage": "/api/query/...",
    "suggestedActions": ["resolve-conflict", "view-timeline"]
  }
}
```

### 2. **Consistency Across Endpoints**
- Standard error format
- Common metadata structure
- Consistent spoiler filtering
- Uniform pagination

### 3. **Evolution-Friendly**
- Version in accept headers: `Accept: application/vnd.lorevault.v1+json`
- Additive changes only within versions
- Clear deprecation path

## Learning Goals & Experiments

### Phase 1: Core UI Patterns
**Timeline:** v0.9.0 - v1.0.0

**Implement:**
1. `character-detail-view` - Most complex UI screen
2. `character-browser` - List/pagination patterns  
3. `semantic-search` - Already partially implemented

**Learn:**
- How complex do query parameters get?
- Do we need sub-resources or nested endpoints?
- Performance characteristics of intent-driven queries
- Caching patterns and effectiveness

### Phase 2: Relationship Complexity  
**Timeline:** v1.1.0 - v1.2.0

**Implement:**
1. `relationship-explorer` - Graph traversal patterns
2. `timeline-view` - Temporal relationship queries
3. `universe-dashboard` - Aggregated data patterns

**Learn:**
- Where do we hit N+1 query problems?
- How do we handle deep relationship traversals?
- What are the performance limits?
- Caching complexity for related data

### Phase 3: Advanced Patterns
**Timeline:** v1.3.0+

**Implement:**
1. Real-time updates (WebSocket + query refresh)
2. Batch operations for performance
3. Advanced filtering and faceted search

**Learn:**
- When do we need to break the single-endpoint rule?
- How complex can spoiler filtering get?
- What developer experience friction emerges?

## Success Metrics

### Developer Experience
- **Frontend velocity:** Time to implement new UI screens
- **API discoverability:** How easily can frontend dev understand available queries?
- **Debugging experience:** How easy to trace issues from UI to backend?

### Technical Performance  
- **Response times:** Target <200ms for detail views, <100ms for lists
- **Cache hit rates:** HTTP caching effectiveness
- **Database query efficiency:** N+1 problems, query count per endpoint

### Flexibility & Evolution
- **API evolution:** How often do UI changes require backend changes?
- **Query reuse:** How much overlap between different UI screens?
- **Edge case handling:** How gracefully do we handle complex user scenarios?

## Key Questions to Answer

1. **Endpoint Granularity:** Too many specific endpoints vs too few generic ones?
2. **Parameter Complexity:** When do query params become unwieldy?
3. **Response Size:** When do responses get too large? How to handle pagination in complex views?
4. **Spoiler Filtering:** How deep into the data model does context-aware filtering need to go?
5. **Real-time Updates:** How do intent-driven queries work with live data updates?
6. **Performance Boundaries:** Where does this pattern start breaking down?

## Expected Challenges & Mitigation

### Challenge 1: Endpoint Proliferation
**Risk:** Too many specific endpoints becomes hard to maintain
**Mitigation:** Start with core UI patterns, look for consolidation opportunities

### Challenge 2: Parameter Explosion  
**Risk:** Query parameters become complex and error-prone
**Mitigation:** Use request bodies for complex filtering, validate with JSON schemas

### Challenge 3: Caching Complexity
**Risk:** Intent-driven responses harder to cache effectively
**Mitigation:** Design cache keys around user context (universe + spoiler level + entity)

### Challenge 4: Frontend Coupling
**Risk:** UI changes requiring backend changes
**Mitigation:** Over-deliver data initially, then optimize. Use feature flags for experimental UI features.

## Decision Points

We'll evaluate at each phase:

1. **v1.0.0:** Do we continue with pure CQRS queries or add GraphQL layer?
2. **v1.2.0:** Do we need batch endpoints or stay with single-intent pattern?
3. **v1.5.0:** Do we add a generic query language on top, or stay domain-specific?

**The goal:** Push the CQRS query-side pattern to its limits and document the journey for future teams facing similar architectural decisions.
