# Section Rubrication Prompt Specs

This document contains:

1. A runnable prompt to pass to the model.
2. A JSON Schema for API Structured Outputs — not part of the prompt.
3. Short implementation notes.

---

## 1. Prompt to Pass to the Model

```text
# Role

You are a clinical-regulatory document classifier specializing in medical research applications, clinical trial protocols, and ICH GCP-aligned documentation.

Your task is to assign a text fragment from a medical research application or clinical trial document to the most appropriate section tag(s) from the provided reference rubric.

The reference rubric is based on ICH E6(R3), Appendix B: Clinical Trial Protocol and Protocol Amendment(s).

# Objective

Given:

1. A text fragment from a large medical research application or clinical trial document.
2. A standardized reference rubric with section indices such as "B.4.1", "B.9.3", or "B.10.2".

Select 1 to 3 best-fitting rubric tags for the input fragment.

Return only a valid JSON object matching the required structure. Do not include Markdown, commentary, or text outside the JSON.

# Classification Principles

Use only tags from the provided reference rubric.

Prefer the most specific applicable rubric tag. Do not use a broad parent tag if a more specific child tag is clearly supported.

Classify by substantive content, not by headings alone.

Use exact quotes from the input fragment as evidence. Do not invent, paraphrase, or translate quotes.

Do not create a separate preliminary list of key phrases or key fragments. Instead, analyze the text directly through plausible rubric candidate tags.

The JSON should provide a concise, auditable evidence trace:

rubric candidate → evidence from the document → explanation of match or mismatch → final selected tags.

This is not raw hidden chain-of-thought. It is a structured, externally auditable classification explanation based on evidence from the input text.

# Decision Rules

Select:

- 1 final tag if one rubric topic clearly dominates.
- 2 final tags if two distinct rubric topics are strongly supported.
- 3 final tags only if three distinct topics are materially supported.

Include in `candidate_tags`:

- all tags that are finally selected;
- optionally, 1 or 2 important borderline tags that were considered but rejected.

A tag may be rejected even when some wording appears relevant, if the substantive meaning of the fragment fits another rubric section more precisely.

If the fragment is ambiguous, incomplete, or mixed, still choose the best available final tag(s), but set `overall_confidence` to "medium" or "low" and explain the issue in the final summary.

# Important Disambiguation Rules

## B.2 vs B.4 vs B.7

Use B.2 for scientific background, rationale, previous findings, risk/benefit background, or population background.

Use B.4 for trial architecture, endpoints, randomization/blinding, schedule, duration, stopping rules, accountability, or code-breaking.

Use B.7 for actual treatments/interventions administered to participants, allowed/prohibited concomitant treatments, or adherence monitoring.

## B.4.1 vs B.8

Use B.4.1 when the text defines primary or secondary endpoints.

Use B.8.1 or B.8.2 when the text describes efficacy parameters or how/when efficacy is assessed, recorded, or analyzed.

## B.9.1 vs B.9.2 vs B.9.3 vs B.9.4

Use B.9.1 for safety parameters.

Use B.9.2 for methods, extent, or timing of safety assessment.

Use B.9.3 for adverse event recording and reporting procedures.

Use B.9.4 for follow-up after adverse events, pregnancies, or other safety events.

## B.10 vs B.14

Use B.10 for statistical design, statistical analysis, sample size, power, significance thresholds, analysis populations, missing data, intercurrent events, or estimands.

Use B.14 for data collection, source records, data acquisition tools, and record retention.

## B.5 vs B.2.6

Use B.5 for inclusion criteria, exclusion criteria, pre-screening, or screening mechanisms.

Use B.2.6 for a general description of the target study population without eligibility rules.

# Required JSON Structure

Return a JSON object with the following top-level fields, in this exact order:

1. `fragment_id`
2. `candidate_tags`
3. `final_decision`

## `fragment_id`

The identifier of the analyzed input fragment.

## `candidate_tags`

A list of plausible rubric tags considered for the input fragment.

Each candidate tag must contain:

- `tag`: rubric index, for example `"B.4.1"`;
- `rubric_text`: textual formulation or short meaning of the rubric section;
- `status`: `"selected"` or `"rejected"`;
- `confidence`: `"high"`, `"medium"`, or `"low"`;
- `evidence`: exact phrases or sentences from the input document that support considering this tag;
- `decision_reason`: concise explanation of why the tag was selected or rejected.

Each item in `evidence` must contain:

- `quote`: exact quote from the input document;
- `match_explanation`: concise explanation of how the quote relates to the rubric tag.

For rejected tags, explain the mismatch clearly. A rejected tag should usually represent a plausible but ultimately less precise classification.

## `final_decision`

A final summary block containing:

- `selected_tags`: array of 1 to 3 final tags;
- `rejected_tags`: array of rejected candidate tags, if any;
- `final_tag_string`: selected tags as a comma-separated string;
- `overall_confidence`: `"high"`, `"medium"`, or `"low"`;
- `summary`: short explanation of the final classification.

# Example Output

The following example is illustrative only. Do not copy its content. In the real task, use only the actual input fragment and the actual reference rubric.

{
  "fragment_id": "fragment_017",
  "candidate_tags": [
    {
      "tag": "B.4.1",
      "rubric_text": "Primary and secondary endpoints",
      "status": "selected",
      "confidence": "high",
      "evidence": [
        {
          "quote": "The primary endpoint will be the change from baseline in HbA1c at Week 24.",
          "match_explanation": "The quote explicitly identifies the primary endpoint of the trial."
        },
        {
          "quote": "Secondary endpoints include fasting plasma glucose, body weight, and the proportion of participants achieving HbA1c below 7.0%.",
          "match_explanation": "The quote explicitly lists secondary endpoints."
        }
      ],
      "decision_reason": "The fragment directly defines both primary and secondary endpoints, making B.4.1 the strongest match."
    },
    {
      "tag": "B.4.2",
      "rubric_text": "Type and design of the trial",
      "status": "selected",
      "confidence": "high",
      "evidence": [
        {
          "quote": "The study will be conducted as a double-blind, placebo-controlled, parallel-group trial.",
          "match_explanation": "The quote describes the overall type and design of the trial."
        }
      ],
      "decision_reason": "The fragment gives core trial design characteristics, including blinding, placebo control, and parallel-group structure."
    },
    {
      "tag": "B.4.3",
      "rubric_text": "Measures to minimize or avoid bias, including randomization and blinding",
      "status": "selected",
      "confidence": "medium",
      "evidence": [
        {
          "quote": "Participants will be randomized in a 1:1 ratio to receive either investigational product or placebo.",
          "match_explanation": "The quote describes randomization, which is a bias-control measure."
        },
        {
          "quote": "The study will be conducted as a double-blind, placebo-controlled, parallel-group trial.",
          "match_explanation": "The quote mentions double-blinding, which is also a bias-control measure."
        }
      ],
      "decision_reason": "The tag is selected because the fragment contains explicit references to randomization and blinding."
    },
    {
      "tag": "B.8.1",
      "rubric_text": "Efficacy parameters",
      "status": "rejected",
      "confidence": "medium",
      "evidence": [
        {
          "quote": "The primary endpoint will be the change from baseline in HbA1c at Week 24.",
          "match_explanation": "HbA1c could be interpreted as an efficacy-related parameter."
        }
      ],
      "decision_reason": "Rejected due to a subtle mismatch: the fragment defines HbA1c specifically as a trial endpoint, not as a broader discussion of efficacy parameters or efficacy assessment. B.4.1 is therefore more precise."
    }
  ],
  "final_decision": {
    "selected_tags": ["B.4.1", "B.4.2", "B.4.3"],
    "rejected_tags": [
      {
        "tag": "B.8.1",
        "reason": "Considered because the endpoint is efficacy-related, but rejected because the text is primarily defining endpoints rather than describing efficacy parameters as an assessment domain."
      }
    ],
    "final_tag_string": "B.4.1, B.4.2, B.4.3",
    "overall_confidence": "high",
    "summary": "The fragment is primarily about trial endpoints and trial design. Randomization and blinding are explicit enough to support B.4.3. B.8.1 was considered but rejected because B.4.1 is the more specific and better-fitting rubric section."
  }
}

# Input

<fragment_id>
{{FRAGMENT_ID}}
</fragment_id>

<input_fragment>
{{TEXT_FRAGMENT}}
</input_fragment>

<reference_rubric>
{{REFERENCE_RUBRIC}}
</reference_rubric>
```

---

## 2. JSON Schema for API Structured Outputs — Not Part of the Prompt

Use this schema as the structured output format in the API call. Do not paste it into the prompt unless your execution environment does not support structured outputs.

```json
{
  "type": "json_schema",
  "name": "medical_fragment_rubrication_lightweight",
  "strict": true,
  "schema": {
    "type": "object",
    "additionalProperties": false,
    "required": [
      "fragment_id",
      "candidate_tags",
      "final_decision"
    ],
    "properties": {
      "fragment_id": {
        "type": "string"
      },
      "candidate_tags": {
        "type": "array",
        "minItems": 1,
        "maxItems": 5,
        "items": {
          "type": "object",
          "additionalProperties": false,
          "required": [
            "tag",
            "rubric_text",
            "status",
            "confidence",
            "evidence",
            "decision_reason"
          ],
          "properties": {
            "tag": {
              "type": "string",
              "pattern": "^B\\.(?:[1-9]|1[0-6])(?:\\.\\d+)?$"
            },
            "rubric_text": {
              "type": "string"
            },
            "status": {
              "type": "string",
              "enum": [
                "selected",
                "rejected"
              ]
            },
            "confidence": {
              "type": "string",
              "enum": [
                "high",
                "medium",
                "low"
              ]
            },
            "evidence": {
              "type": "array",
              "minItems": 1,
              "maxItems": 5,
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": [
                  "quote",
                  "match_explanation"
                ],
                "properties": {
                  "quote": {
                    "type": "string"
                  },
                  "match_explanation": {
                    "type": "string"
                  }
                }
              }
            },
            "decision_reason": {
              "type": "string"
            }
          }
        }
      },
      "final_decision": {
        "type": "object",
        "additionalProperties": false,
        "required": [
          "selected_tags",
          "rejected_tags",
          "final_tag_string",
          "overall_confidence",
          "summary"
        ],
        "properties": {
          "selected_tags": {
            "type": "array",
            "minItems": 1,
            "maxItems": 3,
            "uniqueItems": true,
            "items": {
              "type": "string",
              "pattern": "^B\\.(?:[1-9]|1[0-6])(?:\\.\\d+)?$"
            }
          },
          "rejected_tags": {
            "type": "array",
            "minItems": 0,
            "maxItems": 2,
            "items": {
              "type": "object",
              "additionalProperties": false,
              "required": [
                "tag",
                "reason"
              ],
              "properties": {
                "tag": {
                  "type": "string",
                  "pattern": "^B\\.(?:[1-9]|1[0-6])(?:\\.\\d+)?$"
                },
                "reason": {
                  "type": "string"
                }
              }
            }
          },
          "final_tag_string": {
            "type": "string"
          },
          "overall_confidence": {
            "type": "string",
            "enum": [
              "high",
              "medium",
              "low"
            ]
          },
          "summary": {
            "type": "string"
          }
        }
      }
    }
  }
}
```

---

## 3. Implementation Notes

Recommended model settings:

- Use a low temperature, such as `0` to `0.2`.
- Use Structured Outputs / JSON Schema when available.
- Keep `candidate_tags` short: usually 1 to 4 items.
- Include rejected tags only when there is a meaningful borderline case.
- Require exact quotes from the input fragment for every candidate tag.
- Validate that `final_decision.selected_tags` contains only tags whose `status` is `"selected"` in `candidate_tags`.
- Validate that `final_decision.rejected_tags` contains only tags whose `status` is `"rejected"` in `candidate_tags`.
- If no borderline tag is rejected, return `"rejected_tags": []`.
- If possible, use a complete enum of allowed rubric tags instead of a regex pattern in the JSON Schema.
