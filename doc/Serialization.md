# Data Serialization

## Overview of the main data types
- **Subject Type**. A definition of a type of entity about which data can be collected. Subject Types can be either independent of each other or have hierarchical relationships. Typically, there are two defined Subject Types:
    - **Patient** - An individual who has visited the hospital.
    - **Visit** - An encounter of a Patient at a hospital. Any Visit must belong to a Patient.
- **Subject**. An entity that can have child subjects (entities) and forms associated with it. For example, a Patient subject can have one or multiple child Visits.
- **Questionnaire**. A set of Questions, potentially organized in Sections, that users are presented with. Hierarchical organization: A Questionnaire contains Questions and/or Sections, a Section contains Questions and/or other Sections.
- **Form**. A set of Answers and Answer Sections that store the responses of a user to the Questionnaire. Answers can be empty if the user did not provide one. When a user should be able to complete a given Questionnaire, a blank Form is generated for that user and Questionnaire.
    - Forms link back to a particular Questionnaire to be able to display the Questions and Sections that are associated with a given Answer or Answer Section.
    - Forms link to a particular Subject as the owner of this Form. For example, each Patient Subject will always have a single Patient Information Form, each Visit Subject will always have a Visit Information Form, Visits will have one or more Survey Events forms, and visits will have one or more survey forms (such as YVM, PMOO, etc) depending on the location and type of the patient's visit to the hospital that triggered the survey.

### Status flags

Forms (as well as their Answer and AnswerSection descendants) and Subjects can have `statusFlags`. Notable flags:
- `INCOMPLETE` - the Form is missing at least one Answer value to a mandatory Question. The causative Answer entries and all of their parent items all the way to the Form will also have the `INCOMPLETE` status flag
- `INVALID` - the Form has at least one Answer with an invalid value (for example, value 5 for a Question that expects values between 0 and 4). The causative Answer entries and all of their parent items all the way to the Form will also have the `INVALID` status flag
- `DRAFT` - the form is INCOMPLETE, INVALID, or both. DRAFT is not applied to the Answers and Answer Sections.
- `SUBMITTED` - a form associated with a Visit subject containing answers to a survey questionnaire that has been submitted via the patient portal. Unless the data has been submitted, it can not be considered for statistics as the patient did not "sign off" on their answers.

## Running exports
To run an export, navigate to a URL in the following format:
`<base url>/Path/To/Exported/Data.<Any processors>.dataFilter:<Any filters>.<extension>`.

### Export formats
Multiple different export formats are supported. These include:
- **.json**: This format most closely matches the way that data is stored internally. It is also highly customizable, from verbose to the bare minimum.
- **.csv** and **.tsv**: This format includes additional processing options to help match data into a row/column format. These options are explained in the CSV Adapter Options section below, alongside the standard Processor and Filter options.
- **.txt**: minimalistic human-readable serialization, suitable for copy/pasting in a note
- **.md**: Markdown-formatted version of the text format, suitable for printing or archiving

### Notable export paths
- `/Questionnaires/<QuestionnaireId>.json` exports the Questionnaire metadata definition
- `/Questionnaires/<QuestionnaireId>.deep.json` exports the full Questionnaire definition, including questions and sections
- `/Questionnaires/<QuestionnaireId>.data.json` exports the forms containing answers to the specified Questionnaire. Filters and processors can be added, as shown in the examples below.
- `/Subjects/<MRN>/<Encounter ID>.data.deep.json` exports a visit and all its associated forms. Filters and processors can be added, as shown in the examples below.
- `/Forms/<Form ID>.deep.json` exports a Form with all its answers. Filters and processors can be added, as shown in the examples below.
- `/Forms/<Form ID>.md` exports a Markdown-formatted view of a Form. Use `.txt` to export plain text instead.

#### Example: Weekly OAIP Form Export
`/Questionnaires/OAIP.data.dataFilter:modifiedAfter=2025-07-19T02:00:00%5C.000-05:00.dataFilter:modifiedBefore=2025-07-26T02:00:00%5C.000-05:00.dataFilter:status=SUBMITTED.labels.formToSurveyLinks.csvIncludeFields:@survey=Survey.csvHeader:raw.questionnaireFilter:exclude=%252FQuestionnaires%252FOAIP%252Foaip_module1%252Foaip_visit_month.csv`
- `/Questionnaires/OAIP`: Export the OAIP questionnaire
- `.data`: Include the forms that answer this questionnaire
- `.dataFilter:modifiedAfter=2025-07-19T02:00:00%5C.000-05:00`: Files modified after 2 AM on July 19th, UTC -5. Since periods are used to seperate selectors, the period in the timestamp must be escaped with a `\`. This slesh needs to be URL-encoded to `%5C`. The shorter format `2025-07-19` is also supported, and interpreted as Midnight (time `T00:00:00.000`) in the server's timezone.
- `.dataFilter:modifiedBefore=2025-07-26T02:00:00%5C.000-05:00`: Files modified before 2 AM on July 26th, UTC -5
- `.dataFilter:status=SUBMITTED`: Only include forms with the `SUBMITTED` status flag
- `.labels`: Use the human readable version of answers, instead of the raw data (eg. `Never` instead of `0`)
- `.formToSurveyLinks`: Include the path to the relevant Survey Events form in the form data. This adds an `@survey` property, which is included in the export later
- `.csvIncludeFields:@survey=Survey`: Special instruction for the csv output format. Include the `@survey` property in a column with the label `Survey`
- `.csvHeader:raw`: Special instruction for the csv output format. Include the raw property names as a header in addition to the (default) labels
- `.questionnaireFilter:exclude=%252FQuestionnaires%252FOAIP%252Foaip_module1%252Foaip_visit_month`: Do not include the question `oaip_visit_month` or it's answers. The path to this question has been URL  encoded twice from `/Questionnaires/OAIP/oaip_module1/oaip_visit_month`, first to replace the `/` with `%2F` and second to replace `%` with `%25`
- `.csv`: Export the data as a csv file.

#### Example: Weekly Survey Event Form Export
`/Questionnaires/Survey events.data.dataFilter:modifiedAfter=2025-07-19T02:00:00%5C.000-05:00.dataFilter:modifiedBefore=2025-07-26T02:00:00%5C.000-05:00.dataFilter:statusNot=INCOMPLETE.labels.csv`

#### Example: Exporting all the data for a Visit as JSON
`/Subjects/<MRN>/<Encounter ID>.data.deep.json`

#### Example: Exporting a Patient and all the forms, from all their visits, that have been modified since the specified date, in a more compact JSON (`.bare`)
`/Subjects/<MRN>.deep.data.dataFilter:modifiedAfter=2025-07-19.dataOption:descendantData=true.dataOption:formSelectors=deep%5C.bare.json`
- `.deep`: Include the Patient subject descendants, i.e. the visits
- `.data`: Include the forms for these subjects
- `dataFilter:modifiedAfter=2025-07-19`: Only include forms modified after the given date
- `.dataOption:descendantData=true`: Also include forms for the descendant subjects
- `dataOption:formSelectors=deep%5C.bare`: Apply a different set of selectors for serializing the forms. The separator dots must be escaped in order to not be considered as separators between the outer list of selectors.

#### Example: Exports a Form with all its answers in a simplified form, with almost no metadata
`/Forms/<Form ID>.deep.bare.-identify.-dereference.nolinks.-answerCopy.json`

#### Notes on processors and filters
- Processors and filters that expect a date to be provided can accept that date in a variety of formats. For example:
    - `2025-01-01T02:00:00%5C.000-05:00`: A fully specified date, including date (Jan. 1), time (02:00:00), millisecond (.000), and timezone (-05:00)
    - `2025-01-01T02:00`: A simplified datetime, interpreted using the server's timezone
    - `2025-01-01`: A date, interpreted as midnight on that day in the server's timezone
- Some processors are enabled by default. These processors can be disabled by including their name prefixed with a `-`. For example, `.-identify` would disable the `identify` processor. These default processors are labeled below with `isEnabledByDefault`.
- Some processors or filters are set up to only run on a specific data type. These restrictions are noted in their description. For example, `Only runs on Forms`.
- Some processors have multiple implementations under the same name. These instances are generally designed to accomplish the same goal, each working on specific data types or in specific situations, and enabling one of them will enable all of them.

For the full list of available processors and filters, please refer to the last two sections of this document.

## Processors
### answerCopy
#### Implementations
1. Only runs on `Subjects`.  
Copy the values of certain answers from forms to the root subject JSON. The answers to copy are configured in `/apps/cards/config/CopyAnswers/Questionnaires/[questionnaire name]/` as properties with the desired output name as the key and references to a question as the value.
Questions can be copied either from a form belonging to this subject, one of it's ancestors or one of it's descendants.  
**isEnabledByDefault**: true
1. Only runs on `Forms`.  
Copy the values of certain answers to the form JSON. The answers to copy are configured in `/apps/cards/config/CopyAnswers/Questionnaires/[questionnaire name]/` as properties with the desired output name as the key and references to a question as the value.
Questions can be copied either from the current form itself, or from another form belonging to the same subject, one of the subject's ancesters, or one of the subject's descendants.  
**isEnabledByDefault**: true
### answerFilter
Only runs on `Forms`.  
Include or exclude answers or answer sections based on the question or section they refer to.
If only `include` options are provided, only those items and any descendants will be included.
If only `exclude` options are provided, all other items will be included.
If both `include` and `exclude` options are provided, then for an item to be included it must be a listed as an `include` item or descendent thereof and also not be listed as or a descendant of an `exclude` node.
It is not possible to include a descendant of an excluded item.  
#### Options
 - **include**: A path to an included question or section. e.g. `answerFilter:include=/Questionnaires/Path/To/Question`
 - **exclude**: A path to an excluded question or section. e.g. `answerFilter:exclude=/Questionnaires/Path/To/Question`
### bare
#### Implementations
1. Simplify serialization for all resource types by removing all technical properties, renaming `jcr:created` to `created` and storing file attachments in a `content` property.
1.  Only runs on `Forms`.
Simplify form serialization by only including the subject name, questionnaire and each sectionand question. Designed for use with `deep`, `-dereference` and `-identify`.
1. Only runs on `Subjects`.
Simplify subject serialization by only including simple labels for the name, type and parents of asubject. Should be used with `-dereference` and `-identify`.
### data
Serialize the forms associated with a Questionnaire or Subject.
### deep
Enable deep serialization, i.e. including the serialization of all descendant items. For example, serializing a Form without `deep` would only output the form's properties. With `deep`, all the answers are included, organized by sections.
### dereference
Dereference properties of type `REFERENCE`, `WEAKREFERENCE` and `PATH`: Instead of printing the internal UUID, serialize the referenced node.  
**isEnabledByDefault**: true
### excludeDefaultProperties
Exclude properties if their value is the default value or an obvious non-value:
 - If a property's value matches its defined default value
 - If it's a `false` boolean
 - If it's an empty string ''.
### excludeFiles
Exclude the contents of uploaded files from the serialization. By default, all uploaded files are excluded.  
**isEnabledByDefault**: true
#### Options
 - **exclude**: If this option is specified, only exclude the specified files. Use it multiple times to exclude more than one file: `.excludeFiles:exclude=/Questionnaires/Path/To/QuestionWithFileAnswer`.
### flatten
Flatten a form so all answers, regardless of sections and subsections, are listed in the top level of the json. This processor is incompatible with the `bare` processor.
### formToSurveyLinks
If a form has a `belongsToSurvey` link, include the name of the linked form in the root of the linking form's serialization JSON.
### identify
Identify a node by including its `@path` and `@name` properties.  
**isEnabledByDefault**: true
### importable
Export a file that is suitable for using as a source file to be imported. Removes any properties that are tied to a running instance such as unique ID and version control. This processor is intended to be run alongside the `deep` and `-identify` processor.
### instanceCount
Only runs on `SubjectTypes`.  
Include the number of subjects of that type in the subject type serialization.
### labels
#### Implementations
1. Get the human readable answer for resource questions with options by outputting the answer option's label instead of the stored value.  
**isEnabledByDefault**: true
1. Get the human readable answer for resource questions.  
**isEnabledByDefault**: true
1. Runs on Forms and Questionnaires. Adds a label to answer option nodes.  
**isEnabledByDefault**: true
1. Get the human readable answer for text and number questions with options by outputting the answer option's label instead of the stored value.  
**isEnabledByDefault**: true
1. Get the answers for pedigree questions as an svg picture.  
**isEnabledByDefault**: true
1. Get the human readable answer for file questions by outputting the file name instead of the path.  
**isEnabledByDefault**: true
1. Get the human readable answer for dicom questions by outputting the dicom file name instead of the path.  
**isEnabledByDefault**: true
1. Get the human readable answer for questions.  
**isEnabledByDefault**: true
1. Get the human readable answer for date questions. The human readable version is the date formatted with the date format configured in the date question definition, for example `01/07/2025` instead of the stored value `2025-01-07T00:00:00%5C.000-05:00`.  
**isEnabledByDefault**: true
1. Get the human readable answer for boolean questions by outputting the labels specified in the question definition, e.g. 'Yes' or 'True' instead of '1'.  
**isEnabledByDefault**: true
1. Get the human readable answer for text and number questions with options by outputting the answer option's label instead of the stored value.  
**isEnabledByDefault**: true
### links
Simplify the serialization of links to other resources (such as other forms) to only include the link type and path to the linked resource.  
**isEnabledByDefault**: true
### nolinks
Exclude the links to other resources from serialization.
### properties
Serialize node properties.  
**isEnabledByDefault**: true
### questionnaireFilter
Only runs on `Questionnaires`.  
Include or exclude questions or sections.
If only `include` options are provided, only those items and any descendants will be included.
If only `exclude` options are provided, all other items will be included.
If both `include` and `exclude` options are provided, then for an item to be included it must be a listed as an `include` item or descendent thereof and also not be listed as or a descendant of an `exclude` item.
It is not possible to include a descendant of an excluded item.
#### Options
 - **include**: A path to an included question or section. e.g. `questionnaireFilter:include=/Questionnaires/Path/To/Question`
 - **exclude**: A path to an excluded question or section. e.g. `questionnaireFilter:exclude=/Questionnaires/Path/To/Question`
### referenced
Report if a resource is referenced by adding a `@referenced=true|false` property.
### simple
#### Implementations
1. Simplify serialization for all resource types by removing unnecessary properties.
Remove the `form` property and all `sling:` and `jcr:` properties.
1. Only runs on `Questionnaires`.  
Simplify questionnaire serialization by removing child jcr properties.
1. Only runs on `Forms`.  
Simplify form serialization by removing unnecessary properties and children. Removes child jcr properties, removes extra properties from answers and cleans up subject types.
1. Only runs on `Subjects`.  
Simplify form serialization by removing the jcr properties.

## Filters
### clinic
Only show forms that belong to a user that has a 'Visit information' form for the specified clinic. If included multiple times, this includes forms belonging to any of the specified clinics. e.g. `.dataFilter:clinic=PMH-YVM`.
### clinicNot
Exclude forms that belong to a user that has a 'Visit information' form for the specified clinic. If included multiple times, this excludes forms belonging to any of the specified clinics. e.g. `.dataFilter:clinicNot=PMH-YVM`.
### createdAfter
Only show results that were created on or after the requested datetime. e.g. `.dataFilter:createdAfter=2025-01-01T06:00:00%5C.000-05:00` for forms created after January 1, 2025 at 6 AM in the time zone UTC-5.
### createdBefore
Only show results that were created before the requested datetime. e.g. `.dataFilter:createdBefore=2025-01-01T06:00:00%5C.000-05:00` for forms created before January 1, 2025 at 6 AM in the time zone UTC-5.
### createdBy
Only show results that were created by the specified user. e.g. `.dataFilter:createdBy=admin`
### modifiedAfter
Only show results that were modified after the requested datetime. e.g. `.dataFilter:modifiedAfter=2025-01-01T06:00:00%5C.000-05:00` for forms modified after January 1, 2025 at 6 AM in the time zone UTC-5.
### modifiedBefore
Only show results that were modified before the requested datetime. e.g. `.dataFilter:modifiedBefore=2025-01-01T06:00:00%5C.000-05:00` for forms modified before January 1, 2025 at 6 AM in the time zone UTC-5.
### modifiedBy
Only show results that were last modified by the specified user. e.g. `.dataFilter:modifiedBy=admin`.
### notCreatedBy
Only show results that were created by any user other than the specified user. e.g. `.dataFilter:notCreatedBy=admin`.
### notModifiedBy
Only show results that were last modified by anby other other than the specified user. e.g. `.dataFilter:notModifiedBy=admin`.
### status
Only show results that have the specified status flag. e.g. `.dataFilter:status=SUBMITTED`.
### statusNot
Only show results that do not have the specified status flag. e.g. `.dataFilter:statusNot=INCOMPLETE`.
### visitSubmitted
Only include forms based on their submission status.
#### Options
 - **`.dataFilter:visitSubmitted=true`**: Only show forms belonging to a submitted visit.
 - **`.dataFilter:visitSubmitted=false`**: Only show forms belonging to a visit that has not been submitted.

## CSV Adapter Options
### csvHeader:labels
Only runs on `Questionnaires`.  
Include the human readable label as a header row for all exported columns.  
**isEnabledByDefault**: true
### csvHeader:raw
Only runs on `Questionnaires`.  
Include the raw property name as a header row for all exported columns.
### csvIncludeFields
Only runs on `Questionnaires`.  
Include a specified property on data forms that belongs to this questionnaire that would normally be skipped in the csv export process. This option is intended to be run alongside the `.data` processor.  
For example, `.csvIncludeFields:@survey=Survey` will include the `@survey` property.
