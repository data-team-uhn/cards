# Resource pagination and filtering

The pagination servlet is a quick way to list resources. It works on any type of resource, but it has support for more advanced filtering for Forms.

## Overview of the main data types

As a reminder, these are the main data (resource) types:

- **Subject Type**. A definition of a type of entity about which data can be collected. Subject Types can be either independent of each other or have hierarchical relationships. Typically, there are two defined Subject Types:
    - **Patient** - An individual who has visited the hospital.
    - **Visit** - An encounter of a Patient at a hospital. Any Visit must belong to a Patient.
- **Subject**. An entity that can have child subjects (entities) and forms associated with it. For example, a Patient subject can have one or multiple child Visits.
- **Questionnaire**. A set of Questions, potentially organized in Sections, that users are presented with. Hierarchical organization: A Questionnaire contains Questions and/or Sections, a Section contains Questions and/or other Sections.
- **Form**. A set of Answers and Answer Sections that store the responses of a user to the Questionnaire. Answers can be empty if the user did not provide one. When a user should be able to complete a given Questionnaire, a blank Form is generated for that user and Questionnaire.
    - Forms link back to a particular Questionnaire to be able to display the Questions and Sections that are associated with a given Answer or Answer Section.
    - Forms link to a particular Subject as the owner of this Form. For example, each Patient Subject will always have a single Patient Information Form, each Visit Subject will always have a Visit Information Form, Visits will have one or more Survey Events forms, and visits will have one or more survey forms (such as YVM, PMOO, etc) depending on the location and type of the patient's visit to the hospital that triggered the survey.

## Basic usage

To use the pagination servlet, simply add `.paginate` at the end of the URL when accessing the homepage for a specific resource type, e.g. `<base url>/Forms.paginate`. This will return a JSON with the results of the query, with the following structure:

```
{
  "rows": [
    {...},
    {...}
  ],
  "req": "",
  "offset": 0,
  "limit": 10,
  "returnedrows": 2,
  "totalrows": 2,
  "totalIsApproximate": false
}
```

- `rows` contains the actual data, serialized as JSON
- `req` is a reflected query parameter, it will copy whatever value you send as the `req` query paramter, and can be used to differentiate between multiple requests sent in parallel
- `offset` and `limit` are reflected query parameter, they can be used to specify the "page" that is requested, the 0-based `offset` of the first item to return, and the `limit` number of items to return
- `returnedrows` is the number of returned items, matching the size of the `rows` array
- `totalrows` is the total number of items matching the query, but for performance it can be just an estimate if the total number is larger than 10x the page size
- `totalIsApproximate` indicates if the value in `totalrows` is an approximation, with more results matching the query beyond that count

## Basic parameters

- `offset`: A 0-based number indicating the offset of the first item to include in the result, skipping over all the previous resources matching the query. Defaults to `0` if not specified.
- `limit`: The number of items to include in the result, starting with the `offset`'th one. Defaults to `10` if not specified.
- `descending`: By default, results are ordered by their creation date, with the oldest first. To instead get the newest first, use `descending=true`.
- `req`: A reflected query parameter, it will be copied in the response, and can be used to differentiate between multiple requests sent in parallel.
- `resourceSelectors`: A list of selectors to use when serializing the matching resources as JSON. See the description in [`Serialization.md`](Serialization.md) for more details about selectors.
- `fieldname`, `fieldcomparator`, `fieldvalue`: basic filters on the resources being listed. Use all three to put a single restriction on one of the resource properties, for example `fieldname=statusFlags&fieldcomparator=<>&fieldvalue=INCOMPLETE` or `fieldname=jcr:createdBy&fieldcomparator==&fieldvalue=admin` or `fieldname=jcr:created&fieldcomparator=>=&fieldvalue=2025-01-01T00:00:00.000-04:00`

## Advanced form filtering

When listing Forms, it is possible to filter them based on their subject, questionnaire, and even answer values.

- `includeallstatus`: By default only non-incomplete forms are returned. Use `includeallstatus=true` to return all forms regardless of their status.
- `filterempty` and `filtnoterempty`: Specify questions that must or must not be empty. Use the parameters multiple times to request multiple questions to be/not be answered. The accepted values are internal question identifiers, which can be obtained by inspecting the JSON of the questionnaire.
- `filternames`, `filtercomparators`, `filtervalues`, `filtertypes`: Specify restrictions on the answers to specific questions. All four paramters must be specified, and if more than one filter is used, all four parameters must have the same number of appearances in the request, or else the query will be rejected with an error. `filternames` specifies the internal question identifier. `filtercomparators` must be one of the accepted values listed below. `filtervalues` is the value to compare against. `filtertypes` must match the type of the answer, listed in the question under the `dataType` property. These can also be used for filtering on a few Form metadata fields:
    - `cards:Subject` restricts the results to forms related to the specified subject internal identifier
    - `cards:Questionnaire` restricts to forms for a specific questionnaire
    - `cards:Created` for limiting the date when the form was created (use full ISO datetime)
    - `cards:CreatedBy` for specifying the user who created the form
    - `cards:LastModified` for limiting the date when the form was last modified (use full ISO datetime)
    - `cards:LastModifiedBy` for specifying the user who last modified the form

Supported comparators: `=`, `<>`, `<`, `<=`, `>`, `>=`, `LIKE`, `notes contain`, `contains`, ` IS NULL`, ` IS NOT NULL`.

Form filtering can also be used when listing Subjects, with the answer filters applied to any of the forms belonging to the subject being considered for listing.

## Examples:

`/Questionnaires.paginate` returns the first 10 questionnaire definitions.

`/Questionnaires.paginate?resourceSelectors=deep` returns the first ten questionnaires, with the results formatted with the specified processing, i.e. default deep serialization of the questionnaires.

`/Questionnaires.paginate?resourceSelectors=-properties.-dereference.nolinks&limit=1000` returns up to 1000 questionnaires, with the results formatted with the specified processing. This specific combination of selectors will only include the name and path to the resource.

`/Forms.paginate?fieldname=subject&fieldvalue=4e4dc67f-68e2-4e11-a3cf-b447a9341d88&includeallstatus=true&limit=1000&resourceSelectors=deep` returns up to 1000 forms for the given subject, regardless of their completion status.
- `fieldname=subject`, `fieldvalue=4e4dc67f-68e2-4e11-a3cf-b447a9341d88`: Filter on the Form's `subject` property, which links to a specific subject. `fieldvalue` uses the internal identifier of the subject, which must be obtained separately by inspecting the Subject's data.
- `includeallstatus=true`: Also include incomplete forms.
- `limit=1000`: Request up to 1000 matching resources.
- `resourceSelectors=deep`: Format the results with the `deep` processor activated.

`/Forms.paginate?includeallstatus=true&offset=100&limit=50&req=13&filternames=23eafcc1-727f-41a5-85e0-276e2e5e8456&filternames=1fd78393-def4-4d19-a342-8f24e528d23e&filternames=cards%3AQuestionnaire&filtercomparators=%3D&filtercomparators=%3C%3E&filtercomparators=%3D&filtervalues=Asthma&filtervalues=Yes%2C+current+or+recent+%28%3C1y%29+smoking&filtervalues=802878cd-89c4-4683-aed7-1942953eea9f&filtertypes=text&filtertypes=text&filtertypes=questionnaire&descending=true` requests rows 100-149 matching two answer filters and a questionnaire filter.
- `includeallstatus=true`: Also include incomplete forms.
- `offset=100`, `limit=50`: Request up to 50 matching resources, starting with the 100th.
- `req=13`: This is the 13th request, please include this field in the response for identification.
- `filternames=23eafcc1-727f-41a5-85e0-276e2e5e8456`, `filtercomparators==`, `filtervalues=Asthma`, `filtertypes=text`: The answer for the `Risk Factors` `text` question includes `Asthma`. `filternames` uses the internal identifier of the question, which is specific to each instance.
- `filternames=1fd78393-def4-4d19-a342-8f24e528d23e`, `filtercomparators=<>`, `filtervalues=Yes, current or recent (<1y) smoking`, `filtertypes=text`: The answer for the `Smoking` question is `Yes`.
- `filternames=cards:Questionnaire`, `filtercomparators==`, `filtervalues=802878cd-89c4-4683-aed7-1942953eea9f`, `filtertypes=questionnaire`: The form answer the questionnaire `Baseline Health Information`. Technically this filter is redundant, since the question filters above already limit the results to a specific questionnaire. `filtervalues` uses the internal identifier, which is specific to each instance.
- `descending=true`: Sort newest first.

`/Subjects.paginate?fieldname=type&fieldvalue=a2c7e6d3-ebdc-4588-828e-23d39ef60118&filternames=cards%3ACreated&filtercomparators=%3E%3D&filtervalues=2025-01-01T00%3A00%3A00.000-05%3A00&filtertypes=datetime&descending=true` returns subjects of type Patient, created after 2025-01-01, sorted by newest first.
- `fieldname=type`, `fieldvalue=a2c7e6d3-ebdc-4588-828e-23d39ef60118`: Filter on the Subject's `type` property, which links to a SubjectType. `fieldvalue` uses the internal identifier, which is specific to each instance.
- `filternames=cards:Created`, `filtercomparators=>=`, `filtervalues=2025-01-01T00:00:00.000-05:00`, `filtertypes=datetime`: Special filter name for the resource's created date.
- `descending=true`: Sort newest first.
