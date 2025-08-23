// Debug queries to understand the actual Neo4j structure

// 1. Check what labels scenes actually have
MATCH (s:Scene)
RETURN labels(s) as sceneLabels, count(s) as count
ORDER BY count DESC;

// 2. Check what relationships connect chapters to scenes  
MATCH (c:Chapter)-[r]->(s:Scene)
RETURN type(r) as relationshipType, count(r) as count;

// 3. Check what properties scenes have for ordering
MATCH (s:Scene)
WHERE s.sceneIndex IS NOT NULL OR s.sceneNumber IS NOT NULL
RETURN s.sceneIndex, s.sceneNumber, s.id, s.chapterId
ORDER BY s.chapterId, coalesce(s.sceneIndex, s.sceneNumber)
LIMIT 10;

// 4. Check specific book structure (Updated with correct relationships)
MATCH (b:Book {id: '8f665364-e96b-48cd-9295-1142f0275a78'})
MATCH (c:Chapter)-[:IN_BOOK]->(b)
MATCH (c)-[:HAS_SCENE]->(s:Scene)
RETURN b.title, c.chapterNumber, s.sceneIndex, labels(s) as sceneLabels, s.id
ORDER BY c.chapterNumber, s.sceneIndex;

// 5. Test the corrected in-chapter edge query manually
MATCH (c:Chapter)-[:HAS_SCENE]->(s:Scene)
WITH c, s ORDER BY s.sceneIndex
WITH c, collect(s) AS scenes
UNWIND range(0, size(scenes) - 2) AS i
WITH c, scenes[i] AS earlier, scenes[i + 1] AS later
RETURN c.chapterNumber, earlier.sceneIndex, later.sceneIndex, 
       'Would create edge from scene ' + toString(earlier.sceneIndex) + ' to ' + toString(later.sceneIndex) as edgeDescription;

// 6. Test if temporal edges exist
MATCH ()-[r:MEETS]->()
RETURN count(r) as existingTemporalEdges;