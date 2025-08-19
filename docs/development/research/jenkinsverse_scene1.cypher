    // LoreVault POC: Jenkinsverse Sample Scene Projection (no Version nodes)
    // - Entities labeled with :Entity plus specific type labels
    // - Ascriptions via :HAS_PROPERTY
    // - Generic relations via :REL with relTypeId
    // - Temporal relations (Allen subset) via :TEMPORAL

    // ---------- 1) Constraints ----------
    CREATE CONSTRAINT entity_id IF NOT EXISTS FOR (n:Entity) REQUIRE n.id IS UNIQUE;

    // ---------- 2) Core nodes ----------
    // Individuals
    MERGE (kevin:Entity:Individual {id:'E:individual/kevin_jenkins'})
    ON CREATE SET kevin.name = 'Kevin Jenkins';
    MERGE (officer:Entity:Individual {id:'E:individual/krrkktnkk'})
    ON CREATE SET officer.name = 'Krrkktnkk a’ktnnzzik’tk';

    // Species as Concept
    MERGE (human:Entity:Concept {id:'E:concept/human'})
    ON CREATE SET human.name = 'Human';

    // Collective (Corti) and alias Concept (Greys)
    MERGE (corti:Entity:Collective {id:'E:collective/corti'})
    ON CREATE SET corti.name = 'Corti';
    MERGE (greys:Entity:Concept {id:'E:concept/greys'})
    ON CREATE SET greys.name = 'Greys';

    // Object: Interspecies Communication Implant (ICI)
    MERGE (ici:Entity:Object {id:'E:object/interspecies_communication_implant'})
    ON CREATE SET ici.name = 'Interspecies Communication Implant';

    // Locations
    MERGE (station:Entity:Location {id:'E:location/outlook_on_forever_591'})
    ON CREATE SET station.name = "Station 591 'Outlook on Forever'";
    MERGE (homeworld:Entity:Location {id:'E:location/human_homeworld'})
    ON CREATE SET homeworld.name = 'Human Homeworld';

    // Events
    MERGE (interview:Entity:Event {id:'E:event/interview_kevin_at_outlook_591'})
    ON CREATE SET interview.name = 'Interview of Kevin at Outlook on Forever',
                    interview.modality = 'depicted',
                    interview.granularity = 'scene';
    MERGE (alarm:Entity:Event {id:'E:event/attack_alarm_outlook_591'})
    ON CREATE SET alarm.name = 'Attack Alarm at Outlook on Forever',
                    alarm.modality = 'depicted',
                    alarm.granularity = 'scene';

    // ---------- 3) Shared publication coordinates (flattened relationship properties) ----------
    WITH 'jenkinsverse' AS pub_universe,
        'the_deathworlders' AS pub_series,
        1 AS pub_bookNumber,
        1 AS pub_chapterNumber,
        1 AS pub_sceneIndex

    // ---------- 4) Ascriptions (HAS_PROPERTY) ----------
    // derive comparable coordinates
    WITH pub_universe, pub_series, pub_bookNumber, pub_chapterNumber, pub_sceneIndex,
        (pub_bookNumber * 1000000 + pub_chapterNumber * 1000 + pub_sceneIndex) AS pub_ordinal,
        pub_universe + '/' + pub_series + '/' +
        substring('0000'+toString(pub_bookNumber), size('0000'+toString(pub_bookNumber))-4, 4) + '/' +
        substring('0000'+toString(pub_chapterNumber), size('0000'+toString(pub_chapterNumber))-4, 4) + '/' +
        substring('0000'+toString(pub_sceneIndex), size('0000'+toString(pub_sceneIndex))-4, 4) AS pub_key
    CALL () { MATCH (k {id:'E:individual/kevin_jenkins'}) RETURN k AS kevin }
    CALL () { MATCH (h {id:'E:concept/human'}) RETURN h AS human }
    WITH kevin, human, pub_universe, pub_series, pub_bookNumber, pub_chapterNumber, pub_sceneIndex, pub_ordinal, pub_key
    MERGE (kevin)-[:HAS_PROPERTY {
    propertyId:'P:taxonomy.species',
    aggregateId:'agg-species-kevin-human',
    confidence:0.9,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(human);

    // Values for literals
    MERGE (cat12:Value {kind:'number', n:12}) ON CREATE SET cat12.label = '12';
    MERGE (deathworld:Value {kind:'text', s:'deathworld'}) ON CREATE SET deathworld.label = 'deathworld';
    MERGE (temperate:Value {kind:'text', s:'temperate'}) ON CREATE SET temperate.label = 'temperate';

    WITH 'jenkinsverse' AS pub_universe,
        'the_deathworlders' AS pub_series,
        1 AS pub_bookNumber,
        1 AS pub_chapterNumber,
        1 AS pub_sceneIndex
    WITH pub_universe, pub_series, pub_bookNumber, pub_chapterNumber, pub_sceneIndex,
        (pub_bookNumber * 1000000 + pub_chapterNumber * 1000 + pub_sceneIndex) AS pub_ordinal,
        pub_universe + '/' + pub_series + '/' +
        substring('0000'+toString(pub_bookNumber), size('0000'+toString(pub_bookNumber))-4, 4) + '/' +
        substring('0000'+toString(pub_chapterNumber), size('0000'+toString(pub_chapterNumber))-4, 4) + '/' +
        substring('0000'+toString(pub_sceneIndex), size('0000'+toString(pub_sceneIndex))-4, 4) AS pub_key
    CALL () { MATCH (h {id:'E:location/human_homeworld'}) RETURN h AS homeworld }
    CALL () { MATCH (v1:Value {n:12}) RETURN v1 AS cat12 }
    CALL () { MATCH (v2:Value {s:'deathworld'}) RETURN v2 AS deathworld }
    CALL () { MATCH (v3:Value {s:'temperate'}) RETURN v3 AS temperate }
    MERGE (homeworld)-[:HAS_PROPERTY {
    propertyId:'P:planet.category',
    aggregateId:'agg-planet-category',
    confidence:0.95,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(cat12)
    MERGE (homeworld)-[:HAS_PROPERTY {
    propertyId:'P:planet.classification',
    aggregateId:'agg-planet-classification',
    confidence:0.95,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(deathworld)
    MERGE (homeworld)-[:HAS_PROPERTY {
    propertyId:'P:planet.climate',
    aggregateId:'agg-planet-climate',
    confidence:0.8,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(temperate);

    // ---------- 5) Relations (REL) ----------
    WITH 'jenkinsverse' AS pub_universe,
        'the_deathworlders' AS pub_series,
        1 AS pub_bookNumber,
        1 AS pub_chapterNumber,
        1 AS pub_sceneIndex
    WITH pub_universe, pub_series, pub_bookNumber, pub_chapterNumber, pub_sceneIndex,
        (pub_bookNumber * 1000000 + pub_chapterNumber * 1000 + pub_sceneIndex) AS pub_ordinal,
        pub_universe + '/' + pub_series + '/' +
        substring('0000'+toString(pub_bookNumber), size('0000'+toString(pub_bookNumber))-4, 4) + '/' +
        substring('0000'+toString(pub_chapterNumber), size('0000'+toString(pub_chapterNumber))-4, 4) + '/' +
        substring('0000'+toString(pub_sceneIndex), size('0000'+toString(pub_sceneIndex))-4, 4) AS pub_key
    CALL () { MATCH (n {id:'E:individual/kevin_jenkins'}) RETURN n AS kevin }
    CALL () { MATCH (n {id:'E:object/interspecies_communication_implant'}) RETURN n AS ici }
    CALL () { MATCH (n {id:'E:collective/corti'}) RETURN n AS corti }
    CALL () { MATCH (n {id:'E:concept/greys'}) RETURN n AS greys }
    CALL () { MATCH (n {id:'E:location/outlook_on_forever_591'}) RETURN n AS station }
    CALL () { MATCH (n {id:'E:event/interview_kevin_at_outlook_591'}) RETURN n AS interview }
    CALL () { MATCH (n {id:'E:event/attack_alarm_outlook_591'}) RETURN n AS alarm }
    CALL () { MATCH (n {id:'E:individual/krrkktnkk'}) RETURN n AS officer }
    MERGE (kevin)-[:REL {
    relTypeId:'R:equipped_with',
    aggregateId:'agg-equipped-kevin-ici',
    confidence:0.8,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key,
    qualifierNotes:'registered by desk scanner'
    }]->(ici)
    MERGE (kevin)-[:REL {
    relTypeId:'R:abducted_by',
    aggregateId:'agg-abducted-kevin-corti',
    confidence:0.85,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(corti)
    MERGE (greys)-[:REL {
    relTypeId:'R:alias_of',
    aggregateId:'agg-alias-greys-corti',
    confidence:0.9,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(corti)
    MERGE (interview)-[:REL {
    relTypeId:'R:located_at',
    aggregateId:'agg-interview-at-station',
    confidence:0.95,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(station)
    MERGE (alarm)-[:REL {
    relTypeId:'R:located_at',
    aggregateId:'agg-alarm-at-station',
    confidence:0.95,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(station)
    MERGE (kevin)-[:REL {
    relTypeId:'R:participated_in',
    aggregateId:'agg-kevin-participated-interview',
    confidence:0.95,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(interview)
    MERGE (officer)-[:REL {
    relTypeId:'R:participated_in',
    aggregateId:'agg-officer-participated-interview',
    confidence:0.95,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(interview);

    // ---------- 6) Temporal (TEMPORAL) ----------
    WITH 'jenkinsverse' AS pub_universe,
        'the_deathworlders' AS pub_series,
        1 AS pub_bookNumber,
        1 AS pub_chapterNumber,
        1 AS pub_sceneIndex
    WITH pub_universe, pub_series, pub_bookNumber, pub_chapterNumber, pub_sceneIndex,
        (pub_bookNumber * 1000000 + pub_chapterNumber * 1000 + pub_sceneIndex) AS pub_ordinal,
        pub_universe + '/' + pub_series + '/' +
        substring('0000'+toString(pub_bookNumber), size('0000'+toString(pub_bookNumber))-4, 4) + '/' +
        substring('0000'+toString(pub_chapterNumber), size('0000'+toString(pub_chapterNumber))-4, 4) + '/' +
        substring('0000'+toString(pub_sceneIndex), size('0000'+toString(pub_sceneIndex))-4, 4) AS pub_key
    CALL () { MATCH (n {id:'E:event/interview_kevin_at_outlook_591'}) RETURN n AS interview }
    CALL () { MATCH (n {id:'E:event/attack_alarm_outlook_591'}) RETURN n AS alarm }
    MERGE (interview)-[:TEMPORAL {
    type:'before',
    aggregateId:'agg-temporal-interview-before-alarm',
    confidence:0.9,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_ordinal,
    pubKey: pub_key
    }]->(alarm);

    // ---------- 7) Optional legal status ascription ----------
    WITH 'jenkinsverse' AS pub_universe,
        'the_deathworlders' AS pub_series,
        1 AS pub_bookNumber,
        1 AS pub_chapterNumber,
        1 AS pub_sceneIndex
    MATCH (kevin {id:'E:individual/kevin_jenkins'})
    MERGE (nonsentient:Value {kind:'text', s:'non_sentient'}) ON CREATE SET nonsentient.label='non_sentient'
    MERGE (kevin)-[:HAS_PROPERTY {
    propertyId:'P:legal.status',
    aggregateId:'agg-legal-status-kevin',
    confidence:0.8,
    pubUniverse: pub_universe,
    pubSeries: pub_series,
    pubBookNumber: pub_bookNumber,
    pubChapterNumber: pub_chapterNumber,
    pubSceneIndex: pub_sceneIndex,
    pubOrdinal: pub_bookNumber * 1000000 + pub_chapterNumber * 1000 + pub_sceneIndex,
    pubKey: pub_universe + '/' + pub_series + '/' +
            substring('0000'+toString(pub_bookNumber), size('0000'+toString(pub_bookNumber))-4, 4) + '/' +
            substring('0000'+toString(pub_chapterNumber), size('0000'+toString(pub_chapterNumber))-4, 4) + '/' +
            substring('0000'+toString(pub_sceneIndex), size('0000'+toString(pub_sceneIndex))-4, 4),
    qualifierLaw:'Galactic Treaty 227 §16'
    }]->(nonsentient);

    // ---------- 8) Validation queries (optional; run selectively) ----------
    /*
    MATCH (k:Individual {id:'E:individual/kevin_jenkins'})-[hp:HAS_PROPERTY {propertyId:'P:taxonomy.species'}]->(sp:Concept)
    RETURN k.name, hp.propertyId, sp.name, hp.pubUniverse, hp.pubBookNumber, hp.pubChapterNumber, hp.pubSceneIndex, hp.pubOrdinal, hp.pubKey, hp.confidence;

    MATCH (p:Individual)-[r:REL {relTypeId:'R:participated_in'}]->(e:Event)
    RETURN e.name AS event, collect(p.name) AS participants;

    MATCH (home:Location {id:'E:location/human_homeworld'})-[hp:HAS_PROPERTY]->(v:Value)
    RETURN home.name, hp.propertyId, coalesce(v.s, v.n) AS value;

    MATCH (a:Event)-[t:TEMPORAL]->(b:Event)
        RETURN a.name, t.type, b.name;
    */

        // ---------- 9) POC-only raw Claim nodes (optional; for debugging) ----------
        // These are NOT part of the :Entity taxonomy and can be removed later.
        CREATE CONSTRAINT claim_id IF NOT EXISTS FOR (c:Claim) REQUIRE c.id IS UNIQUE;

        WITH 'jenkinsverse' AS pubUniverse, 'the_deathworlders' AS pubSeries, 1 AS pubBook, 1 AS pubChapter, 1 AS pubScene,
                 (1 * 1000000 + 1 * 1000 + 1) AS pubOrdinal,
                 'jenkinsverse/the_deathworlders/0001/0001/0001' AS pubKey
        MATCH (kevin:Individual {id:'E:individual/kevin_jenkins'})
        MATCH (human:Concept {id:'E:concept/human'})
        MATCH (interview:Event {id:'E:event/interview_kevin_at_outlook_591'})
        MERGE (c1:Claim {id:'C:001'})
            ON CREATE SET
                c1.kind = 'Ascription',
                c1.propertyId = 'P:taxonomy.species',
                c1.valueKind = 'entity',
                c1.certainty = 0.90,
                c1.source = 'narrator',
                c1.pubUniverse = pubUniverse,
                c1.pubSeries = pubSeries,
                c1.pubBookNumber = pubBook,
                c1.pubChapterNumber = pubChapter,
                c1.pubSceneIndex = pubScene,
                c1.pubOrdinal = pubOrdinal,
                c1.pubKey = pubKey
        MERGE (c1)-[:SUBJECT]->(kevin)
        MERGE (c1)-[:VALUE]->(human)
        MERGE (c1)-[:ANCHORS_EVENT]->(interview);

        // Reset pub variables to ensure this block runs standalone, and match needed nodes
        WITH 'jenkinsverse' AS pubUniverse, 'the_deathworlders' AS pubSeries, 1 AS pubBook, 1 AS pubChapter, 1 AS pubScene,
            (1 * 1000000 + 1 * 1000 + 1) AS pubOrdinal,
            'jenkinsverse/the_deathworlders/0001/0001/0001' AS pubKey
        MATCH (kevin:Individual {id:'E:individual/kevin_jenkins'})
        MATCH (interview:Event {id:'E:event/interview_kevin_at_outlook_591'})
        MATCH (ici:Object {id:'E:object/interspecies_communication_implant'})
        MERGE (c2:Claim {id:'C:002'})
            ON CREATE SET
                c2.kind = 'Relation',
                c2.relTypeId = 'R:equipped_with',
                c2.certainty = 0.80,
                c2.source = 'scanner',
                c2.pubUniverse = pubUniverse,
                c2.pubSeries = pubSeries,
                c2.pubBookNumber = pubBook,
                c2.pubChapterNumber = pubChapter,
                c2.pubSceneIndex = pubScene,
                c2.pubOrdinal = pubOrdinal,
                c2.pubKey = pubKey
        MERGE (c2)-[:SUBJECT]->(kevin)
        MERGE (c2)-[:OBJECT]->(ici)
        MERGE (c2)-[:ANCHORS_EVENT]->(interview);

        // Reset pub variables for standalone execution, and match needed nodes
        WITH 'jenkinsverse' AS pubUniverse, 'the_deathworlders' AS pubSeries, 1 AS pubBook, 1 AS pubChapter, 1 AS pubScene,
            (1 * 1000000 + 1 * 1000 + 1) AS pubOrdinal,
            'jenkinsverse/the_deathworlders/0001/0001/0001' AS pubKey
        MATCH (interview:Event {id:'E:event/interview_kevin_at_outlook_591'})
        MATCH (station:Location {id:'E:location/outlook_on_forever_591'})
        MERGE (c3:Claim {id:'C:003'})
            ON CREATE SET
                c3.kind = 'Relation',
                c3.relTypeId = 'R:located_at',
                c3.certainty = 0.95,
                c3.source = 'narrator',
                c3.pubUniverse = pubUniverse,
                c3.pubSeries = pubSeries,
                c3.pubBookNumber = pubBook,
                c3.pubChapterNumber = pubChapter,
                c3.pubSceneIndex = pubScene,
                c3.pubOrdinal = pubOrdinal,
                c3.pubKey = pubKey
        MERGE (c3)-[:SUBJECT]->(interview)
        MERGE (c3)-[:OBJECT]->(station);