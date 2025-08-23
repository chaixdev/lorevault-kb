// Debug queries for temporal edge issue
// Run these in Neo4j Browser to diagnose the problem

// 1. Check if books exist and have proper IDs
MATCH (b:Book)
RETURN b.id, b.title, b.totalChapters
ORDER BY b.title;

// 2. Check if chapters exist and are linked to books
MATCH (b:Book)-[:CONTAINS]->(c:Chapter)
RETURN b.title, c.chapterNumber, c.title, c.id, c.bookId
ORDER BY b.title, c.chapterNumber;

// 3. Check if scenes exist and are linked to chapters
MATCH (c:Chapter)-[:CONTAINS]->(s:Scene)
RETURN c.chapterNumber, c.title, count(s) as sceneCount, collect(s.sceneNumber) as sceneNumbers
ORDER BY c.chapterNumber;

// 4. Check detailed scene structure
MATCH (b:Book)-[:CONTAINS]->(c:Chapter)-[:CONTAINS]->(s:Scene)
RETURN b.title, c.chapterNumber, s.sceneNumber, s.id, s.chapterId, s.bookId
ORDER BY b.title, c.chapterNumber, s.sceneNumber;

// 5. Check for any temporal edges
MATCH ()-[r:MEETS]->()
RETURN count(r) as totalTemporalEdges;

// 6. Check for temporal edges with details
MATCH (s1:Scene)-[r:MEETS]->(s2:Scene)
RETURN s1.sceneNumber, s1.chapterId, r.type, r.confidence, s2.sceneNumber, s2.chapterId
ORDER BY s1.chapterId, s1.sceneNumber;

// 7. Check if the bookId field is populated correctly on scenes
MATCH (s:Scene)
WHERE s.bookId IS NULL
RETURN count(s) as scenesWithoutBookId;