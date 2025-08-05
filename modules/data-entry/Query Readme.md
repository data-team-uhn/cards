# Querying CARDS

## Hierarchical Data Storage
CARDS relies on the hierarchical content repository JCR Oak for data storage, documentation for which can be found here: https://jackrabbit.apache.org/oak/docs/. JCR uses a nested data structure with folders called `Nodes`. Each `Node` can contain any number of child `Nodes`, as well as any number of `Properties`. `Properties` contain either a single piece of data or an array of data.

## Node Types
Each `Node` has a `Node Type` which defines what child nodes and properties should be expected on a given `Node`. Some defined `properties` or child `nodes` are optional and may or may not be present. Also, there may be `properties` or child `nodes` that are not listed in the `node type` definition.

The node types present within a CARDS implementation can be found by navigating to `/system/console/status-JCR%20CND`. The specifications for the `node type` format can be found here: https://jackrabbit.apache.org/jcr/node-type-notation.html.

Commented version of the cards specific node type can also be found through the CARDS github repository: https://github.com/search?q=repo%3Adata-team-uhn%2Fcards+path%3A*.cnd&type=code

## Queries
Data queries use the JCR-SQL2 query syntax: https://jackrabbit.apache.org/oak/docs/query/grammar-sql2.html
The basic query structure is as follows:
```
/query?query=select n.* from [<node type>] as n where <conditions> order by n.'<property name>' &limit=<limit>
```

### Parameters

- `offset`: A 0-based number indicating the offset of the first item to include in the result, skipping over all the previous resources matching the query. Defaults to `0` if not specified.
- `limit`: The number of items to include in the result, starting with the `offset`'th one. Defaults to `10` if not specified.
- `req`: A reflected query parameter, it will be copied in the response, and can be used to differentiate between multiple requests sent in parallel.
- `resourceSelectors`: A list of selectors to use when serializing the matching resources as JSON. See the description in `modules/utils/Serialization Readme.md` for more details about selectors.
- `rawResults=true`: Returns just the selected columns in a map instead of the JSON serialization of the matching resources.
- `showTotalRows=true`: Requests to return a complete count of the matching items; for performance reasons the default is to only count up to 10 times the number of requested items and indicate in the response that more items are available.

### Response format

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

- `rows` contains the actual data, serialized as JSON, or if `rawResults` were requested, as a simple map
- `req` is a reflected query parameter, it will copy whatever value you send as the `req` query paramter, and can be used to differentiate between multiple requests sent in parallel
- `offset` and `limit` are reflected query parameter, they can be used to specify the "page" that is requested, the 0-based `offset` of the first item to return, and the `limit` number of items to return
- `returnedrows` is the number of returned items, matching the size of the `rows` array
- `totalrows` is the total number of items matching the query, but for performance it can be just an estimate if the total number is larger than 10x the page size
- `totalIsApproximate` indicates if the value in `totalrows` is an approximation, with more results matching the query beyond that count

### Examples

Basic query, looking for questionnaires with `Information` in the title:
```
/query?query=select * from [cards:Questionnaire] as n where contains(n.'title', 'Information')&limit=5
```

Get the answer for a specific `<time question>` for a specific `<subject>`:
```
/query?query=select t.* from [cards:Form] as f inner join [cards:DateAnswer] as t on t.form = f.[jcr:uuid] where f.subject = '<subject uuid>' and t.question = '<time question uuid>' OPTION (index tag cards)

/query?query=select t.* from [cards:Form] as f inner join [cards:DateAnswer] as t on t.form = f.[jcr:uuid] where f.subject = '0d810aba-1aed-4b74-89cf-97e0ede32f13' and t.question = 'd7e6dacc-a8a4-4654-a343-c3e01579f0a9' OPTION (index tag cards)
```

Get the 50 most recently updated forms:

```
/query?query=select f.[jcr:path] from [cards:Form] as f order by f.[jcr:lastModified] desc option (index tag cards)&rawResults=true&limit=50
```

Get all (at most 1000) forms modified after 2025-01-01:

```
/query?query=select f.* from [cards:Form] as f where f.[jcr:lastModified] >= '2025-01-01T00:00:00.000-05:00' option (index tag cards)&limit=1000
```

Get all the patient who unsubscribed from receiving emails:

```
/query?query=select patient.identifier from [cards:Subject] as patient inner join [cards:Form] as patientInfo on patientInfo.subject = patient.[jcr:uuid] inner join [cards:BooleanAnswer] as unsub on unsub.form = patientInfo.[jcr:uuid] where unsub.value = 1 and unsub.question = 'uuid of the unsubscribed question' option (index tag cards)&rawResults=true&limit=1000
```

The above query is slow, it is a lot faster to query the UUIDs of the subjects instead:

```
/query?query=select patientInfo.subject from [cards:Form] as patientInfo inner join [cards:BooleanAnswer] as unsub on unsub.form = patientInfo.[jcr:uuid] where unsub.value = 1 and unsub.question = 'uuid of the unsubscribed question' option (index tag cards)&rawResults=true&limit=1000
```

### A note about `option (index tag)`

For faster queries, there are several indexes built on top of the data.
There are two types of indexes, simple property indexes that only map the values of a specific property to the nodes matching that value, and Lucene indexes that allow more complex queries.
Property indexes are very fast when only one property is being queried, and reflect data in realtime,
but when there's more than one condition imposed on a node, only one of those conditions will use an index while the rest are checked one by one, which may be very slow.
Lucene indexes are more versatile and usually just as fast as property indexes, although it takes a few seconds to reflect the newest data.
Not specifying which type of index to use may result in the wrong index being used, or no index at all, which may lead to very slow queries.
It is recommended to always append `OPTION (index tag cards)` at the end of the query to force the use of the Lucene indexes, unless a realtime count of the number of matches for a very simple query is needed.
It is also possible to request a property index to be used with `OPTION (index tag property)`, if a property index is the right one to use for the query.

## UUIDs Within CARDS Data
Within CARDS, UUIDs are used in two places:
- Within the hierarchical path to dynamically generated nodes. These path name UUIDs show up in a node's `@name` and `@path` property and should be used when locating or accessing a node by path. For example, the path to a specific form will be `/Forms/<uuid>`, as explained below in the **Data Hierarchy** subsection.
- As a `jcr:uuid` property on a node that is designed to be referenced by other nodes. Whenever a node belongs to or relates to another node in a way that is not captured by the hierarchical structure, the owned node will contain a property with the owning node's `jcr:uuid`. For example, every form node contains a `questionnaire` and a `subject` property that contains the `jcr:uuid` of the questionnaire and subject that the form is for. Similarly, answers and answer sections contain `question` and `section` properties with the relevant `jcr:uuid`.

In general, when accessing data by path, such as through the UI, the path name UUID should be used. When nodes are referenced internally from other nodes, then the `jcr:uuid` is used.

## Data Structure Notation
The is organized as outlined below in the **Data Hierarchy** subsection. The representation of each node should be interpreted as follows:
- General format: `<Node Path relative to parent>: <Node type>`
- The prefix `*` means the parent node may contain any number of child nodes of this type
- `...` means there is a recursive structure. This node type may contain nodes of the same node type, leading to a potentially recursive hierarchy. In these cases, one level has been included to show what types of child nodes may be present. In practice many levels may be present, following the same structure as the example


### Data Hierarchy
* **`/`: `jcr:root`**
  - `/Metrics`: `sling:Folder`
    - \* `/<metric name>`: `sling:Folder`
      - `/name`: `nt:unstructured`
      - `/prevTotal`: `nt:unstructured`
      - `/total`: `nt:unstructured`
  - `/SubjectTypes`: `cards:SubjectTypesHomepage`
    - \* `/<subject type name>`: `cards:SubjectType`
      - `/cards:links`: `cards:Links`
        - \* `/<uuid>`: `cards:WeakLink`
        - \* `/<uuid>`: `cards:Link`
      - \* `/<subject type name>`: `cards:SubjectType`
        - ...
  - `/Survey`: `cards:PatientHomepage`
    - `/ClinicMapping`: `cards:ClinicMappingFolder`
      - \* `/<clinic mapping name or ID>`: `cards:ClinicMapping`
    - \* `/<clinic name>`: `cards:QuestionnaireSet`
      - `/cards:links`: `cards:Links`
        - \* `/<uuid>`: `cards:WeakLink`
        - \* `/<uuid>`: `cards:Link`
      - \* `/<questionnaire reference name>`: `cards:QuestionnaireRef`
    - `/TermsOfUse`: `sling:Folder`
    - `/PatientAccess`: `sling:Folder`
    - `/DashboardSettings`: `sling:Folder`
    - `/SurveyInstructions`: `sling:Folder`

  - `/Forms`: `cards:FormsHomepage`
    - \* `/<uuid>`: `cards:Form`
      - `/cards:links`: `cards:Links`
        - \* `/<uuid>`: `cards:WeakLink`
        - \* `/<uuid>`: `cards:Link`
      - \* `/<uuid>`: `cards:Answer`
      - \* `/<uuid>`: `cards:AnswerSection`
        - \* `/<uuid>`: `cards:Answer`
        - \* `/<uuid>`: `cards:AnswerSection`
          - ...
  - `/Subjects`: `cards:SubjectsHomepage`
    - \* `/<uuid>`: `cards:Subject`
      - `/cards:links`: `cards:Links`
        - \* `/<uuid>`: `cards:WeakLink`
        - \* `/<uuid>`: `cards:Link`
      - \* `/<uuid>`: `cards:Subject`
        - ...
  - `/Questionnaires`: `cards:QuestionnaireHomepage`
    - \* `/<questionnaire name>`: `cards:Questionnaire`
      - `/cards:links`: `cards:Links`
        - \* `/<uuid>`: `cards:WeakLink`
        - \* `/<uuid>`: `cards:Link`
      - \* `/<question name>`: `cards:Question`
      - \* `/<section name>`: `cards:Section`
        - \* `/<question name>`: `cards:Question`
        - \* `/<section name>`: `cards:Section`
          - ...
