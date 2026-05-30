package com.lorevault.api.graph.event.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
public class EventMentionComponentLookup {

    private final Neo4jClient neo4jClient;

    public EventMentionComponentLookup(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public List<SameEventComponentRow> findSameEventComponents(UUID chapterId) {
        return new ArrayList<>(neo4jClient.query("""
                        MATCH (root:EventMention {chapterId: $chapterId})
                        OPTIONAL MATCH path = (root)-[:SAME_EVENT*0..]-(peer:EventMention {chapterId: $chapterId})
                        WHERE all(n IN nodes(path) WHERE n.chapterId = $chapterId)
                        WITH root, collect(DISTINCT coalesce(peer.id, root.id)) AS componentMemberIds
                        WITH root,
                             [x IN componentMemberIds | toString(x)] AS componentMemberStrings
                        WITH root,
                             componentMemberStrings,
                             reduce(minId = componentMemberStrings[0], x IN componentMemberStrings |
                                 CASE WHEN x < minId THEN x ELSE minId END
                             ) AS componentId
                        RETURN toString(root.id) AS mentionId, componentId
                        """)
                .bind(chapterId.toString())
                .to("chapterId")
                .fetchAs(SameEventComponentRow.class)
                .mappedBy((typeSystem, record) -> new SameEventComponentRow(
                        record.get("mentionId").asString(),
                        record.get("componentId").asString()
                ))
                .all());
    }

    public record SameEventComponentRow(String mentionId, String componentId) {
    }
}
