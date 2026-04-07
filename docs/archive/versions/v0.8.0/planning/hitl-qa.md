
# HITL Knowledge Graph Building – Design Insights (Q\&A)

---

## Q1: How should we handle drafts versus the canonical knowledge graph?

**Answer:**
We need a safe way to let users create and edit **provisional graph data** before it is reviewed and promoted to the canonical knowledge graph (KG).

**Explored Alternatives:**

1. **Multiple Databases per Neo4j Instance (Preferred if available)**

   * *Description:* Keep a `kg_prod` database for canonical data and a `kg_drafts` database for provisional edits. Users write drafts into `kg_drafts`, and validated changes are later merged into `kg_prod`.
   * *Rationale:* Provides clean isolation—draft data never touches production. No risk of accidental leakage.
   * *Constraints:* Requires Neo4j **Enterprise edition** or an Aura tier that supports multi-DB. Transactions cannot span databases, so promotion is an application-level operation.

2. **Single Database + Role-Based or Fine-Grained Access Control (Enterprise)**

   * *Description:* Store drafts in the same database but restrict access via Neo4j’s access control (labels, rel types, properties).
   * *Rationale:* Keeps infrastructure simpler—only one DB to manage.
   * *Constraints:* Still a risk of "prod pollution" if permissions or queries are misconfigured. Requires Enterprise.

3. **Single Database + Namespacing with Labels/Properties (Community Edition fallback)**

   * *Description:* Tag provisional nodes and relationships with `:Provisional` + `draftOwnerId`.
   * *Rationale:* Works in all editions; no Enterprise features required.
   * *Constraints:* Drafts share space with production—risk of forgotten or orphaned provisional data. Requires periodic cleanup.

**Recommendation:**
Use **separate databases** if supported by your Neo4j deployment. Fall back to namespacing only if constrained to Community edition.

---

## Q2: What does the data structure for visualizing graphs look like?

**Answer:**
Most visualization libraries expect a structure with two arrays:

```json
{
  "nodes": [
    { "id": "n1", "label": "Person", "title": "Ada Lovelace" }
  ],
  "links": [
    { "id": "e1", "source": "n1", "target": "n2", "type": "MENTIONS" }
  ]
}
```

* **Nodes** carry IDs, labels, and optional attributes for styling.
* **Edges/Links** connect `source` and `target`, with a `type` (relationship) and properties.

This pattern maps easily to libraries like **Cytoscape.js**, **Sigma.js**, **Vis.js**, or **D3-force**.

---

## Q3: Can Cypher queries directly drive visualizations?

**Answer:**
Yes. Cypher is well suited for retrieving graph data in the structure required by visualization tools.

**Explored Alternatives:**

1. **Neovis.js (Cypher + Visualization Integration)**

   * *Description:* A Neo4j-contrib project combining the Neo4j JavaScript driver and vis.js. You configure it with Cypher queries, and it automatically populates the visualization.
   * *Rationale:* Minimal boilerplate—great for query-driven UIs.

2. **Custom Pipeline (Neo4j Driver + Any Graph Library)**

   * *Description:* Run Cypher queries with the Neo4j driver, transform results into `{nodes, links}` format, and feed them to Cytoscape.js, Sigma.js, or D3.
   * *Rationale:* Maximum flexibility. Lets you control styling, layouts, and advanced interactions like expansion on click.

3. **Neo4j Browser / Bloom (Reference, not Embeddable)**

   * *Description:* Neo4j Browser is open source; Bloom is a commercial exploration UI.
   * *Rationale:* Useful for inspiration and ad-hoc exploration, but not suited for embedding into a HITL workflow.

**Recommendation:**
If rapid prototyping is needed, use **Neovis.js**. For production HITL UIs, use the **Neo4j driver + Cytoscape.js/Sigma.js** approach for full control.

---

## Q4: What UX patterns make sense for HITL validation?

**Answer:**

* **Two-pane workflow:** Left pane = table of proposed changes (adds/updates/deletes). Right pane = live graph visualization.
* **Overlay design:** Show canonical graph as base, overlay draft nodes/edges with distinct styles (e.g., dashed edges, muted colors).
* **Change-level controls:** Allow users to accept/reject changes individually or in bulk.
* **Validation checks:** Run guard queries (uniqueness, orphan detection, schema compliance) before promoting to canonical KG.
* **Audit trail:** Track who approved what and when, by storing approval metadata as part of the graph (e.g., `(:Approval)-[:APPROVES]->(:ChangeSet)`).

---

## Q5: How do we promote data from draft to canonical?

**Answer:**

* Generate a **diff** of changes (new nodes, updated properties, new relationships).
* Apply these changes with idempotent Cypher (`MERGE`) into the canonical KG.
* Mark the draft as published (or delete it).
* Optionally, log the change set and approval metadata for traceability.

---

✅ **Summary:**

* Prefer **multi-DB** separation (clean, no prod pollution).
* For visualization, stick to `{nodes, links}`; Cypher queries naturally produce this.
* **Neovis.js** is good for fast integration; **Cytoscape.js/Sigma.js** offer more control.
* HITL workflows benefit from overlaying drafts onto canonical data, with granular approval and validation checks.
