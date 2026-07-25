<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# Entity Index

A denormalized, entity-level search index for CARDS. Each *entity* — a root `cards:Resource`, by
default a `cards:Form`, together with all its descendant items, by default its `cards:Answer`s —
is stored as a **single flattened Lucene document**. A query filtering on any number of answers of
the same form is then a single index lookup returning in milliseconds, instead of a JCR JOIN query
whose cost grows polynomially with the number of JOINed answer constraints.

## Why not the standard Oak indexes?

The JCR/Oak query stack fundamentally maps **one node to one index document**, and this cannot be
worked around from within it:

- **Oak's JOIN execution is a nested loop** (`org.apache.jackrabbit.oak.query.ast.JoinImpl`): for
  every row produced by the left selector, the right selector's index lookup is re-executed. Each
  additional JOIN multiplies the cost. The `QueryIndex` SPI only ever receives a single-selector
  `Filter`, so *no* index implementation — property, Lucene, Elastic, or custom — can ever service
  a whole JOIN query in one lookup. A custom Oak index plugin therefore cannot fix JOIN
  performance; only changing the shape of the query (single selector, flattened fields) can.
- **Lucene/Elastic index aggregation is fulltext-only.** The `aggregates` section of an index
  definition (already used by the `forms.json` index in CARDS) folds descendant content into the
  parent's *fulltext*, usable only through `contains()`; aggregated properties cannot be used for
  structured (equality/range) constraints. Relative property support is limited to fixed paths or
  a single-level wildcard (`*/color`); arbitrary-depth descendant matching is explicitly
  unsupported ([OAK-5187](https://issues.apache.org/jira/browse/OAK-5187)).
- **The Elastic index** (`oak-search-elastic`) shares the same one-node-per-document model and the
  same aggregation semantics, requires an external Elasticsearch cluster to operate, and only
  supports the `elastic-async` indexing lane. It brings operational cost without solving the JOIN
  problem.
- **A JOIN filter can only see forms that have an answer node for the question.** A question in a
  conditional section that is not shown never gets an answer node, so those forms are simply absent
  from the JOIN and cannot be matched at all. This silently breaks any filter whose intended meaning
  includes *absence*: `not-equals` (a missing answer is not the forbidden value, yet the form is
  dropped), `is empty`, and the very notion of an unanswered question. A one-document-per-form index
  makes "this form has no value for question X" a first-class, queryable fact, so those filters
  finally behave the way someone reading the form would expect, conditional sections included.
- The existing schema already carries scars from this limitation: the denormalized `form` string
  property on every `cards:Answer` and the `relatedSubjects` list on every `cards:Form` exist only
  to make the JOINs *possible* to index at all.

Hence this module: a **separate, embedded Lucene index** living outside the Oak query engine,
integrated with the repository through standard Sling/JCR hooks (a `ResourceChangeListener` for
near-real-time maintenance, repoinit + service users for read access, Sling servlets for
querying), and queried through its own endpoint using flattened field names instead of JOINs.
Lucene 9 is embedded privately inside the bundle, so it does not conflict with the (much older)
Lucene embedded in `oak-lucene`, and no external service is needed.

## The flattened document

For a Form, the document contains (see `IndexFields` for the full convention):

| Field | Content |
|---|---|
| `@path`, `@uuid`, `@type` | the Form's path, uuid, primary node type |
| `@questionnaire`, `@questionnairePath` | the answered questionnaire, as uuid and path |
| `@subject`, `@subjectIdentifier`, `@subjectFullIdentifier`, `@relatedSubjects` | the subject hierarchy |
| `@statusFlags`, `@created`, `@createdBy`, `@lastModified`, `@lastModifiedBy` | form metadata |
| `@questions` / `@answeredQuestions` | uuids of questions with an answer node / with an actual value |
| `@fulltext` | catch-all analyzed text: all textual values, notes, subject identifiers |
| *`<key>`* | each answer's value(s), indexed under **two** keys: the question's uuid, and the question's path relative to `/Questionnaires` (e.g. `Patient information/date_of_birth`) |

Each `<key>` is typed: `<key>` holds the exact string form, `<key>.text` analyzed text,
`<key>.long` / `<key>.double` numeric points (booleans as 0/1, dates as epoch milliseconds),
`<key>.note` the answer's note, `<key>.sort` / `<key>.nsort` sort keys. Answer sections are
transparent: answers are indexed the same no matter how deeply nested in (recurrent) sections.

## Querying

`GET /Forms.entitysearch.json` accepts the same parameters as the `.paginate` servlet
(`filternames`/`filtercomparators`/`filtervalues`/`filtertypes`,
`fieldnames`/`fieldcomparators`/`fieldvalues` for the fixed filters baked into the table URLs — the
questionnaire, subject, subject type and status flag filters, which carry no explicit type —
`filterempty`, `filternotempty`, `filter` for fulltext, `offset`, `limit`, `descending`,
`includeallstatus`, `req`, `resourceSelectors`), plus:

- `lucene`: a native Lucene query over the flattened fields, e.g.
  `lucene=Patient\ information\/date_of_birth.long:[0 TO 1262304000000] AND \@statusFlags:SUBMITTED`
  (special characters in field names — spaces, slashes, `@`, the dashes of uuids — must be
  backslash-escaped, as usual in the Lucene query syntax; keyword fields match exact values,
  case included)
- `sortby`: a question (uuid or `/Questionnaires`-relative path) to order by instead of the
  creation date.
- `joinnames`/`joincomparators`/`joinvalues`/`jointypes`: a **cross-entity join** — conditions
  that another form, sharing a related subject with the returned results, must match all
  together. For example, Visit information forms can be restricted to patients whose Patient
  information form has a specific answer. The join is evaluated as one extra index lookup,
  independent of the number of results, not per-row like JCR JOINs.

Filter names may be question uuids (as sent by the existing frontend), question paths relative to
`/Questionnaires`, or the special `cards:Questionnaire`, `cards:Subject`, `cards:Created`,
`cards:CreatedBy`, `cards:LastModified`, `cards:LastModifiedBy`, `statusFlags` names. The response
has the same shape as `.paginate` (`rows`, `returnedrows`, `totalrows`, …) plus `searchtimems`.

Comparator semantics: `<>` matches every form that does not have the given value, including forms
where the answer *has no value at all* (mirroring `not answer = value`); range comparators, in
contrast, only match forms where the answer *has* a value, since an absent value is neither above nor
below the bound; dates compare with whole-day precision; `contains` matches substrings of analyzed
words; `notes contain` searches the answer notes. Since everything is one document, results need no
in-memory deduplication (unlike JOIN queries, which repeat a form once per matched answer), and
free fulltext ranking, fuzzy (`~`), wildcard and phrase queries are available through the
`lucene`/`filter` parameters. Result totals are not taken from the index; they are counted while
resolving the results against the requesting user's access (see the access control caveat below).

`POST /Forms.reindexEntities.json` (administrators only) triggers a full rebuild in the
background.

## Backend API

The `EntityIndexer` OSGi service is the Java entry point for scheduled jobs and other backend
code. Conditions are built from question paths with `SearchCondition.forQuestion`, which resolves
the question's identity and data type from its definition; `SearchQuery.withAnyOf` adds an OR
group, and `SearchQuery.withSubjectJoin` a cross-entity join:

```java
@Reference
private EntityIndexer entityIndex;
...
SearchQuery query = new SearchQuery()
    .withCondition(SearchCondition.forQuestion(session, "/Questionnaires/Visit information/time", "<", "2026-08-01"))
    .withCondition(SearchCondition.forQuestion(session, "Visit information/status", "<>", "cancelled"))
    .withAnyOf(List.of(
        new SearchCondition(IndexFields.CREATED, Operator.GTE, "2026-07-24", Type.DATE),
        SearchCondition.forQuestion(session, "Visit information/time", ">=", "2026-07-24")))
    .withSubjectJoin(List.of(
        SearchCondition.forQuestion(session, "Patient information/email_ok", "=", "1")))
    .withMaxHits(Integer.MAX_VALUE);
for (String formPath : this.entityIndex.search(query).getPaths()) {
    // read the matching forms through your own session, unreadable results must be skipped
}
```

`AppointmentUtils.getAppointmentsForDay` in the patient-portal module is a complete example: it
replaced a four-JOIN JCR query used by the scheduled email notification jobs.

## Maintenance lifecycle

- On activation, if the index is empty but `/Forms` has children, a full walk indexes everything
  (retried while the repository is still initializing).
- A `ResourceChangeListener` on `/Forms` (local + external changes) maps every change to the
  containing form and re-flattens that form; removals delete the document. Changes are batched by
  Sling; a changed form is usually searchable within `refresh_seconds` (default 1s).
- Commits are made durable every `commit_seconds` (default 30s) and on shutdown; the index
  directory (`<sling.home>/entity-index_Forms` by default) survives restarts.
- Reading is done with the `entity-index` service user, which has read-only access to `/Forms`,
  `/Questionnaires` and `/Subjects`.

## Access control caveat

The index itself contains everything readable by the service user, but nothing derived from it is
returned without a per-user access check. Every result counted or returned is first resolved
through the *requesting user's* session; entities the user cannot read do not resolve and are
neither returned nor counted. The reported `totalrows` is therefore the number of results the
requesting user can actually access — the index is never asked for a raw match count, so no
aggregate can leak the existence or values of unreadable forms. `totalIsApproximate` reflects only
whether the scan stopped at its look-ahead/`showTotalRows` limit before reaching the end, not
access control. The cost of this honesty is that computing an exact total (`showTotalRows=true`)
resolves every matching result up to the 10,000 hard cap.

## Configuration

The schema is defined by the `io.uhndata.cards.entityindex.internal.EntityIndexManager` OSGi
configuration (see `EntityIndexConfig`): the entity root path and node type, the container node
types, the alias prefix, the refresh/commit cadence, and one **item rule** per indexable
descendant node type, in the format `nodeType;key=referenceProperty;values=prop1,prop2;note=noteProperty`:

- the default rule for Forms is `cards:Answer;key=question;values=value;note=note`;
- `key` may be omitted, in which case fields are named after the item's own path inside the
  entity — suitable for entities with meaningfully-named children instead of question references,
  e.g. `iap:Reviewer;values=assignee,status,decision`;
- the first `values` property is the item's primary value, indexed directly under the field name;
  every listed property `p` is also addressable as `<field>@<p>` when it is not the primary one.

Changing the rules (or upgrading to a version with a different document format) is detected
through a schema version stored in the index, and triggers an automatic rebuild on startup.

The configuration is a factory: each instance maintains one index for one entity type. Instances
live with their data model: the Forms index is configured in the `cards-data-model-forms-api`
feature, the Subjects index in `cards-data-model-subjects-api`, and each is aggregated into the
distribution through its own `core/*.json` prototype. An IAP deployment would ship its own
instance for `iap:Entity` in the corresponding module.

## Searching subjects

`GET /Subjects.entitysearch.json` covers the dashboard subjects table: filters on the subject's
own fields (`cards:Created`, `identifier`, `statusFlags`, …) apply directly to the subject
documents, while question filters are grouped by questionnaire and evaluated as joins against the
forms index — each group must be matched by a single form of the subject, mirroring the
`.paginate` JOIN semantics. On a 5,200-form test instance, "subjects with a date of birth before
2000-01-01" returns in ~130ms through the index versus ~143 seconds for the equivalent JCR JOIN
query, with identical results.

Current limitations, intended as future work:

- Sorting subjects by answer values is not supported (answers live in separate form documents);
  subject results sort by their own fields only.
- The existing frontend still queries `.paginate`; switching `LiveTable` to `.entitysearch` is a
  small change kept out of scope until the endpoint is validated in production.
