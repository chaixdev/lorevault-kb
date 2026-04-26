Evaluate co-reference for the following EventMention window from chapter {chapterId}.

<mentions>
{mentions}
</mentions>

For every pair (A, B) where A appears before B in the list above, output one judgment object.
If there are 3 mentions (A, B, C), output 3 pairs: (A,B), (A,C), (B,C).
If there are 2 mentions (A, B), output 1 pair: (A,B).

Return a JSON object matching this schema:
{
  "pairs": [
    {
      "mentionIdA": "<uuid of first mention>",
      "mentionIdB": "<uuid of second mention>",
      "sameEvent": true | false,
      "confidence": 0.0 to 1.0,
      "rationale": "<brief explanation>"
    }
  ]
}
