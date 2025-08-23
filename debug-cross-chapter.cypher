// Cross-chapter verification and candidate pairing

// Show pairs of (last of N) -> (first of N+1) that should be linked
MATCH (b:Book {id: $bookId})
MATCH (c1:Chapter)-[:IN_BOOK]->(b)
MATCH (c2:Chapter)-[:IN_BOOK]->(b)
WHERE c2.chapterNumber = c1.chapterNumber + 1

OPTIONAL MATCH (c1)-[:HAS_SCENE]->(s1:Scene)
WITH b, c1, c2, s1 ORDER BY c1.chapterNumber, s1.sceneIndex DESC
WITH b, c1, c2, head(collect(s1)) AS lastScene

OPTIONAL MATCH (c2)-[:HAS_SCENE]->(s2:Scene)
WITH b, c1, c2, lastScene, s2 ORDER BY c2.chapterNumber, s2.sceneIndex ASC
WITH c1, c2, lastScene, head(collect(s2)) AS firstScene

RETURN c1.chapterNumber AS fromChapter,
       lastScene.sceneIndex AS lastSceneIndex,
       c2.chapterNumber AS toChapter,
       firstScene.sceneIndex AS firstSceneIndex,
       lastScene.id AS lastSceneId,
       firstScene.id AS firstSceneId;

// Check if MEETS exists between those pairs
MATCH (s1:Scene {id: $lastSceneId})-[r:MEETS]->(s2:Scene {id: $firstSceneId})
RETURN count(r) AS meetsCount;
