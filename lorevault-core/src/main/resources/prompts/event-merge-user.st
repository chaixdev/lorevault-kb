Evaluate this ChapterEvent candidate pair for book-level semantic merge.

<pair>
  <annScore>{annScore}</annScore>
  <eventA id="{eventId1}">
    <displayName>{event1DisplayName}</displayName>
    <normalizedName>{event1NormalizedName}</normalizedName>
    <representativeEventType>{event1RepresentativeEventType}</representativeEventType>
    <aggregateCard>{event1AggregateCard}</aggregateCard>
    <supportedAliases>
{event1SupportedAliases}
    </supportedAliases>
    <supportedEventTypes>
{event1SupportedEventTypes}
    </supportedEventTypes>
    <evidenceSnippets>
{event1EvidenceSnippets}
    </evidenceSnippets>
  </eventA>
  <eventB id="{eventId2}">
    <displayName>{event2DisplayName}</displayName>
    <normalizedName>{event2NormalizedName}</normalizedName>
    <representativeEventType>{event2RepresentativeEventType}</representativeEventType>
    <aggregateCard>{event2AggregateCard}</aggregateCard>
    <supportedAliases>
{event2SupportedAliases}
    </supportedAliases>
    <supportedEventTypes>
{event2SupportedEventTypes}
    </supportedEventTypes>
    <evidenceSnippets>
{event2EvidenceSnippets}
    </evidenceSnippets>
  </eventB>
</pair>

Return valid JSON only with keys: decision, confidence, rationale.
