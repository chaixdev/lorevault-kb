# LoreVault UI Functionality & API Research

*Author: Technical Advisory*  
*Date: August 20, 2025*  
*Context: Research phase - identifying end-user UI needs and corresponding API design*

## End User Personas & Use Cases

### Primary Persona: The Lore Explorer
**Profile:** Fan of complex fictional universes who wants to understand connections, track characters, and explore without spoilers.

**Core Needs:**
- Browse and discover entities (characters, locations, factions)
- Understand relationships and connections
- Search for specific information or scenes
- Set reading progress to get spoiler-safe responses
- Get answers to lore questions

**Key Constraint:** End users are read-only. No content submission, no conflict resolution, no admin functions.

## Core UI Functionalities (End-User Focus)

### 1. **Content Discovery & Navigation**

#### a) **Entity Browser**
*"I want to browse all characters in the Cosmere universe up to my current reading progress"*

**UI Elements:**
- Filterable list (by status, type, universe)
- Sort options (alphabetical, importance, most-connected)
- Search/filter bar
- Pagination
- Reading progress selector/indicator

**API Needs:**
```http
GET /api/query/entity-browser
  ?universe=cosmere
  &type=character
  &status=alive,unknown
  &sortBy=importance
  &search=kal
  &page=1&limit=20
  &publicationCoordinates=cosmere:book2:chapter15
```

**Response Requirements:**
- Entity summary data (name, primary info, thumbnail)
- Metadata (total count, has-next-page)
- Filter facets (available status values within spoiler bounds)
- Spoiler-safe descriptions

#### b) **Entity Detail View**  
*"I want to see everything about Kaladin that I'm allowed to know at my reading progress"*

**UI Elements:**
- Entity header (name, aliases, status)
- Spoiler-safe description/summary
- Recent mentions with context (within reading bounds)
- Key relationships visualization
- Timeline of events (up to current progress)
- Related entities

**API Needs:**
```http
GET /api/query/character-detail-view/{id}
  ?publicationCoordinates=cosmere:book2:chapter15
  &includeTimeline=true
  &maxMentions=10
  &maxRelations=15
```

**Response Requirements:**
- Core entity data (spoiler-filtered)
- Contextualized mentions (only from chapters user has "read")
- Relationship data filtered by publication coordinates
- Timeline events scoped to entity and reading progress
- Clear indicators of spoiler boundaries

### 2. **Search & Question Answering**

#### a) **Semantic Search**
*"I want to find scenes about bridge crew training, but only from content I've already read"*

**UI Elements:**
- Natural language search bar
- Search result cards with relevance scores
- Source attribution (chapter, scene) 
- Context snippets
- Filter by content type (scenes, characters, locations)
- Reading progress boundary indicators

**API Needs:**
```http
POST /api/query/semantic-search
{
  "query": "bridge crew training scenes",
  "publicationCoordinates": "cosmere:book2:chapter15",
  "contentTypes": ["scene", "character"],
  "limit": 20,
  "includeContext": true
}
```

#### b) **Question Answering**
*"What is the relationship between Kaladin and Syl, based on what I've read so far?"*

**UI Elements:**
- Question input field
- Generated answer with confidence indicator
- Source citations (clickable to source material, within reading bounds)
- Spoiler boundary warnings ("More information available after Chapter X")
- Related questions suggestions

**API Needs:**
```http
POST /api/query/ask-question
{
  "question": "What is the relationship between Kaladin and Syl?",
  "publicationCoordinates": "cosmere:book2:chapter15",
  "focusEntities": ["kaladin", "syl"],
  "answerLength": "detailed"
}
```

### 3. **Relationship & Timeline Exploration**

#### a) **Relationship Explorer**
*"I want to see how all the Bridge Four members are connected, based on my reading progress"*

**UI Elements:**
- Interactive network graph
- Node details on hover/click
- Relationship type filtering
- Depth controls
- Spoiler boundary indicators on nodes/edges
- Reading progress timeline scrubber

**API Needs:**
```http
GET /api/query/relationship-network
  ?focusEntity=kaladin
  &includeTypes=friend,ally,subordinate
  &maxDepth=2
  &publicationCoordinates=cosmere:book2:chapter15
  &includeStrength=true
```

#### b) **Timeline View**
*"I want to see the chronological order of events involving Kaladin, up to where I am in the story"*

**UI Elements:**
- Chronological event timeline
- Event detail cards
- Filter by event type, participants
- Zoom controls (book/chapter/scene views)
- Clear spoiler boundary line ("You are here")
- Future events grayed out or hidden

**API Needs:**
```http
GET /api/query/timeline-view
  ?focusEntity=kaladin
  &publicationCoordinates=cosmere:book2:chapter15
  &eventTypes=battle,meeting,discovery
  &granularity=chapter
```

### 4. **Reading Progress & Personalization**

#### a) **Reading Progress Management**
*"I want to set where I am in the story so everything stays spoiler-free"*

**UI Elements:**
- Universe selection dropdown
- Book/chapter progress sliders
- Visual progress indicators
- "Safe browsing" mode toggle
- Quick progress presets ("Just finished Book 1", "Caught up")

**API Needs:**
```http
PUT /api/query/reading-progress
{
  "universe": "cosmere", 
  "publicationCoordinates": "cosmere:book2:chapter15"
}

GET /api/query/reading-progress?universe=cosmere
```

**UI State Management:**
- Store progress in browser localStorage
- Include in all API requests as parameter
- Clear visual indicators throughout UI

#### b) **Favorites & Bookmarks**
*"I want to bookmark interesting entities and come back to them"*

**UI Elements:**
- Bookmark buttons on entity pages  
- Favorites list
- Custom notes on bookmarked entities
- "Reading lists" or collections

**API Needs:**
```http
POST /api/query/bookmark-entity
{
  "entityId": "kaladin-stormblessed",
  "publicationCoordinates": "cosmere:book2:chapter15",
  "notes": "Interesting character development"
}

GET /api/query/bookmarks
  ?universe=cosmere
  &publicationCoordinates=cosmere:book2:chapter15
```

### 5. **Universal Search & Discovery**

#### a) **Global Search**
*"I want to search across all types of content within my reading bounds"*

**UI Elements:**
- Universal search bar (in header/nav)
- Quick results dropdown
- Full search results page
- Content type tabs (Characters, Locations, Events, Scenes)
- Reading progress context always visible

**API Needs:**
```http
GET /api/query/universal-search
  ?q=storm
  &universe=cosmere
  &publicationCoordinates=cosmere:book2:chapter15
  &contentTypes=character,location,event
  &limit=50
```

#### b) **Content Recommendations**
*"Show me related content I might be interested in, without spoilers"*

**UI Elements:**
- "Related characters" on entity pages
- "You might also like" suggestions
- "Popular entities" within reading progress
- "Recently viewed" history

**API Needs:**
```http
GET /api/query/recommendations
  ?basedOn=kaladin-stormblessed
  &type=similar_characters
  &publicationCoordinates=cosmere:book2:chapter15
  &limit=10
```

## Cross-Cutting UI Concerns

### 1. **Publication Coordinates System**
**Core Principle:** Every API request includes user's reading progress, backend handles spoiler filtering

**Implementation Pattern:**
```http
# Every query includes current reading position
GET /api/query/character-detail-view/{id}
  ?publicationCoordinates=cosmere:book2:chapter15

# Empty param = full corpus access (admin/curator view)
GET /api/query/character-detail-view/{id}
  ?publicationCoordinates=

# Missing param = same as empty (backward compatibility)
GET /api/query/character-detail-view/{id}
```

**UI State Management:**
- User sets reading progress once per universe
- Stored in browser localStorage with fallback to session
- Automatically included in all API requests
- Clear UI indicators showing current spoiler boundaries

### 2. **Eventually Consistent Design**
**Core Principle:** UI designed around data that may be slightly stale, updates through polling

**Design Patterns:**
- **Optimistic Loading:** Show cached/stale data immediately, refresh in background
- **Polling Strategy:** Long-form polling (30s-60s) for non-critical updates
- **Manual Refresh:** User-triggered refresh actions for critical views
- **Staleness Indicators:** Timestamps and "last updated" indicators throughout UI

**No Real-time Requirements:**
- No WebSockets or Server-Sent Events needed
- No live collaborative editing
- Content updates are infrequent (admin-driven)
- User personalization changes are immediate (localStorage)

### 3. **Performance & Caching**
- Quick entity lookups with spoiler filtering
- Fast search response times
- Efficient relationship traversals
- Client-side caching of user preferences

**API Implications:**
- Aggressive HTTP caching with publication coordinates as cache key
- Pre-computed expensive views at publication boundaries
- Pagination for large result sets
- Client-side caching of static reference data (universes, publication structure)

### 4. **Responsive Design Needs**
- **Mobile:** Essential info, simple navigation, spoiler controls prominent
- **Tablet:** Rich browsing, relationship graphs, timeline views
- **Desktop:** Full detail views, multi-pane layouts, advanced filtering

**API Implication:** Same endpoints, UI decides level of detail to request
```http
GET /api/query/character-detail-view/{id}
  ?publicationCoordinates=cosmere:book2:chapter15
  &maxRelations=5    # Mobile: fewer relations
  &maxRelations=20   # Desktop: more relations
```

## API Design Patterns Emerging

### 1. **View-Based Query Endpoints**
Each major UI screen gets its own optimized endpoint:
- `/api/query/entity-detail-view/{id}` 
- `/api/query/entity-browser`
- `/api/query/relationship-network`
- `/api/query/timeline-view`
- `/api/query/semantic-search`
- `/api/query/universal-search`

### 2. **Publication-Coordinate-Aware Responses**
All query endpoints respect `publicationCoordinates` parameter:
- Spoiler filtering applied at data layer
- Relationships filtered by publication boundaries
- Timeline events truncated at reading progress
- Search results constrained to "read" content

### 3. **Metadata-Rich Responses**
Responses include UI guidance and spoiler context:
```json
{
  "data": { /* primary spoiler-filtered data */ },
  "metadata": {
    "publicationCoordinates": "cosmere:book2:chapter15",
    "hasMoreSpoilerContent": true,
    "nextSpoilerBoundary": "cosmere:book2:chapter16", 
    "lastUpdated": "2025-08-20T10:30:00Z",
    "suggestedActions": ["bookmark", "explore-relations"],
    "relatedQueries": ["/timeline-view?focus=kaladin"]
  }
}
```

### 4. **Client-Side State Management**
For personalization without server-side sessions:
```javascript
// UI manages reading progress locally
const readingProgress = {
  "cosmere": "cosmere:book2:chapter15",
  "wheel-of-time": "wot:book5:chapter12"
};

// Included in every API request
fetch(`/api/query/character-detail-view/kaladin?publicationCoordinates=${readingProgress.cosmere}`);
```

## Research Questions for API Design

1. **Publication Coordinates Complexity:** How deep does spoiler filtering need to go in nested relationships and references?

2. **Endpoint Granularity:** How specific should view-based endpoints be? When do we consolidate vs specialize?

3. **Caching with Spoiler Context:** How do we efficiently cache responses that vary by publication coordinates?

4. **Client-Side State:** What's the right balance between client-side state management and server-side personalization?

5. **Eventually Consistent UX:** How do we design UI that feels responsive while working with potentially stale data?

6. **Performance at Scale:** How do publication-coordinate-aware queries perform with large knowledge graphs?

## Next Steps (End-User UI Focus)

1. **Prototype Core Experience:** 
   - Reading progress selector
   - Character detail view with spoiler filtering
   - Basic entity browser

2. **Test Publication Coordinates:**
   - Implement spoiler filtering in backend
   - Validate UI patterns for spoiler boundaries
   - Test caching strategies

3. **Validate with Frontend Developer:**
   - Build actual screens against designed APIs
   - Measure developer experience and iteration speed
   - Identify friction points in the CQRS query pattern

4. **Iterate Based on Usage:**
   - Refine endpoints based on real UI development
   - Optimize performance bottlenecks
   - Enhance spoiler filtering based on user behavior patterns

**Focus:** Create an exceptional end-user lore exploration experience. Admin functions can use existing tools (Postman, direct DB access) until much later in the project.
