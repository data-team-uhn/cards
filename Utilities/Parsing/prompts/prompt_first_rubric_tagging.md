# Extraction stage step 1 Rubrication Prompt

You are helping prepare a medical research proposal for structured extraction.

You will receive only the document outline:
- section_id
- heading
- optional parent heading
- section order
- optional first few words if available

You do NOT have the full section text.

Your task is to assign rough likely rubric tags to each section using the reference rubric.

Important:
- This is a rough outline-based classification, not final evidence-based rubrication.
- Use headings, parent headings, section order, and nearby section titles.
- Do not pretend you have read the section body.
- Do not quote evidence from the document body.
- If the heading is ambiguous, assign broader parent tags or set confidence to "low".
- Prefer 1 to 3 likely rubric tags.
- Use child tags only when the heading strongly supports them.
- If the section likely needs full text to classify reliably, set "needs_text_confirmation": true.
- Return valid JSON only.

Reference rubric:
{{COMPACT_RUBRIC_GLOSSARY}}

Input document outline:
{{catalog.json}}

Return JSON in this format:

{
  "sections": [
    {
      "section_id": "chunk001",
      "heading": "...",
      "likely_rubric_tags": ["B.1.1"],
      "confidence": "high",
      "basis": "Heading strongly suggests title page or protocol identification.",
      "needs_text_confirmation": false
    }
  ]
}