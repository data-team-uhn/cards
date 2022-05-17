//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//

import React, { useContext, useEffect, useState } from "react";
import PropTypes, { object } from 'prop-types';
import { Input, MenuItem, Select, Typography } from "@material-ui/core";

import withStyles from '@material-ui/styles/withStyles';

import EditorInput from "./EditorInput";
import LiveTableStyle from "../dataHomepage/tableStyle.jsx";
import QuestionComponentManager from "./QuestionComponentManager";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import { useFieldsReaderContext, useFieldsWriterContext } from "./FieldsContext";

// Use the filter code to get what sorts of variables can be used as references
let FILTER_URL = "/Questionnaires.deep.json";
// TODO: Figure out what we should do with the limit in the URL below
let SUBJECT_TYPE_URL = "/SubjectTypes.paginate?offset=0&limit=100&req=0";

// Reference Input field used by Edit dialog component
let ReferenceInput = (props) => {
  const { classes, objectKey, data, value } = props;
  const fieldsReader = useFieldsReaderContext();
  const fieldsWriter = useFieldsWriterContext();

  let [ curValue, setCurValue ] = useState(data[objectKey] || []);
  let [ titleMap, setTitleMap ] = useState({});
  let [ pathMap, setPathMap ] = useState({});
  const [ options, setOptions ] = useState([]);
  const [ restrictions, setRestrictions ] = useState([]);

  const isNumeric = value.filter == "numeric";
  const allowOnlyApplicableFor = value.restriction;
  const globalLoginDisplay = useContext(GlobalLoginContext);

  let changeCurValue = (newVal) => {
    fieldsWriter((oldContext) => ({...oldContext, [objectKey]: pathMap[newVal]}));
    setCurValue(newVal);
  }

  useEffect(() => {
    getRestrictions(allowOnlyApplicableFor);
  },
  [fieldsReader[allowOnlyApplicableFor]]);

  // Obtain information about the questions that can be used as a reference
  let grabData = (urlBase, parser) => {
    let url = new URL(urlBase, window.location.origin);

    fetchWithReLogin(globalLoginDisplay, url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(parser)
      .catch(console.log);
  };

  // Parse the response from examining every questionnaire
  let parseQuestionnaireData = (questionnaireJson) => {
    // newFilterableFields is a list of fields that can be filtered on
    // It is a list of either strings (for options) or recursive lists
    // Each recursive list must have a string for its 0th option, which
    // is taken to be its title
    let newFilterableFields = [""];
    // newFilterableTitles is a mapping from a string in newFilterableFields to a human-readable title
    let newFilterableTitles = {"": "None"};
    // newFilterablePaths is a mapping from a string in newFilterableFields to a path in the JCR
    let newFilterablePaths = {"": ""};

    // We'll need a helper recursive function to copy over data from sections/questions
    let parseSectionOrQuestionnaire = (sectionJson, path="") => {
      let retFields = [];
      for (let [title, object] of Object.entries(sectionJson)) {
        // We only care about children that are cards:Questions or cards:Sections
        if (object["jcr:primaryType"] == "cards:Question") {
          // If we are only interested in numeric values, ignore this one unless it is numeric
          if (isNumeric && !(object["dataType"] == 'decimal' || object["dataType"] == 'long')) {
            continue;
          }

          // If this is an cards:Question, copy the entire thing over to our Json value
          retFields.push(object["jcr:uuid"]);
          newFilterableTitles[object["jcr:uuid"]] = object["text"];
          newFilterablePaths[object["jcr:uuid"]] = object["@path"];
        } else if (object["jcr:primaryType"] == "cards:Section") {
          // If this is an cards:Section, recurse deeper
          retFields.push(...parseSectionOrQuestionnaire(object, path+title+"/"));
        }
        // Otherwise, we don't care about this value
      }

      return retFields;
    }

    // From the questionnaire homepage, we're looking for children that are objects of type cards:Questionnaire
    for (let [title, thisQuestionnaire] of Object.entries(questionnaireJson)) {
      if (thisQuestionnaire["jcr:primaryType"] != "cards:Questionnaire") {
        continue;
      }

      newFilterableFields.push([title, ...parseSectionOrQuestionnaire(thisQuestionnaire)]);
    }

    // We also need a filter over the subject
    setOptions(newFilterableFields);
    setTitleMap(newFilterableTitles);
    setPathMap(newFilterablePaths);
  }

  let parseSubjectTypeData = (subjectTypeJson) => {
    // Parse through, but keep a custom field for the subject
    let fields = [""];
    let titles = {"": "None"};
    let paths = {"": ""};

    for (let subjectType of subjectTypeJson["rows"]) {
      // For each reference, store its UUID and title
      fields.push(subjectType["jcr:uuid"]);
      titles[subjectType["jcr:uuid"]] = subjectType["label"];
      paths[subjectType["jcr:uuid"]] = subjectType["@path"];
    }
    setOptions(fields);
    setTitleMap(titles);
    setPathMap(paths);
  }

  // From an array of fields, turn it into a react component
  let getReactComponentFromFields = (fields, nested=false) => {
    return fields.map((path) => {
      if (typeof path == "string") {
        // Straight strings are MenuItems
        // If we have a restriction, we might return nothing
        if (restrictions && restrictions.length > 0 && !restrictions.includes(path)) {
          return null;
        }
        return <MenuItem value={path} key={path} className={classes.categoryOption + (nested ? " " + classes.nestedSelectOption : "")}>{titleMap[path]}</MenuItem>
      } else if (Array.isArray(path)) {
        // Arrays represent Questionnaires of Sections
        // which we'll need to turn into opt groups
        return [<MenuItem className={classes.categoryHeader} disabled>{path[0]}</MenuItem>,
          getReactComponentFromFields(path.slice(1), true)];
      }
    })
  }

  let getRestrictions = (restrictingField) => {
    let field = fieldsReader[restrictingField];
    if (Array.isArray(field)) {
      field = field[0];
    }

    if (field == undefined) {
      setRestrictions(undefined);
      return;
    }

    // Parse out the questionnaire name from the question path
    // If this was selected from elsewhere in the form, we'll be given a path
    let questionnaireMatch = field.match(/(\/Questionnaires\/.+?)\//);
    let fetchRequest = null;
    if (questionnaireMatch) {
      let url = new URL(questionnaireMatch[1] + ".json", window.location.origin);
      fetchRequest = fetchWithReLogin(globalLoginDisplay, url);
    } else {
      // If this is an existing value, we will be given a jcr:uuid instead
      let url = new URL(`query?query=SELECT * FROM [nt:base] AS n WHERE n.'jcr:uuid'='${field}'`, window.location.origin);
      fetchRequest = fetchWithReLogin(globalLoginDisplay, url)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {
          let nodePath = json["rows"]?.[0]?.["@path"];
          nodePath || Promise.reject("Invalid reference: " + field);
          return fetch(new URL(nodePath.match(/(\/Questionnaires\/.+?)\//)[1] + ".json", window.location.origin));
        })
    }
    fetchRequest
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        // We can use both the required subject type, and any of its parents
        let allRootSubjectTypes = [];
        json["requiredSubjectTypes"].forEach((subjectType) => {
          let allParents = subjectType["@path"].match(/\/SubjectTypes\/(.+)/)[1].split('/');

          // Reconstruct the path for every parent of the current node
          let parentPath = "/SubjectTypes";
          for (let parentNodeName of allParents) {
            parentPath += "/" + parentNodeName;
            allRootSubjectTypes.push(parentPath);
          }
        });

        // Next, we need to switch from parent node paths -> jcr:uuid, which we can get from querying all subject types
        // and paring it down to the few that we need
        return fetch(new URL("/SubjectTypes.deep.json", window.location.origin))
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then((subjectTypeJson) => {
            let newRestrictions = [];
            // Subject Type will return a series of nested objects
            // We only want those whose paths show up in our allRootSubjectTypes
            let findRestrictingTypes = (subjectTypeObject) => {
              for (let subjectType of Object.values(subjectTypeObject)) {
                if (allRootSubjectTypes.includes(subjectType["@path"])) {
                  newRestrictions.push(subjectType["jcr:uuid"]);
                  findRestrictingTypes(subjectType);
                }
              }
            }
            findRestrictingTypes(subjectTypeJson);

            // Now that we have a list of jcr:uuids corresponding to each SubjectType
            // that is allowed to be selected, we set up our restrictions
            setRestrictions(newRestrictions);
            // If any currently selected value is outside of the allowable fields, we'll clear the selection
            if (Array.isArray(curValue)) {
              setCurValue((old) => old.filter((oldValue) => newRestrictions.includes(oldValue)));
            } else if (!newRestrictions.includes(curValue)) {
              // Reset to default
              setCurValue([]);
            }
          })
      })
      .catch(console.log);
  }

  useEffect(() => {
    fieldsWriter((oldContext) => ({...oldContext, [objectKey]: curValue}));
    if (value["primaryType"] == "cards:SubjectType") {
      grabData(SUBJECT_TYPE_URL, parseSubjectTypeData);
      if (allowOnlyApplicableFor) {
        getRestrictions(allowOnlyApplicableFor);
      }
    } else if (value["primaryType"] == "cards:Question") {
      grabData(FILTER_URL, parseQuestionnaireData);
    }
  }, [value["primaryType"]]);

  // The form of the hidden input depends on the value of curValue
  // The fallback is to just use its value as-is in a hidden input
  let hiddenInput = <input type="hidden" name={objectKey} value={curValue} />;
  if (Array.isArray(curValue)) {
    // Delete the current values within this list if nothing is selected
    hiddenInput = curValue.length == 0 ? <input type="hidden" name={objectKey + "@Delete"} value="" />
    // Otherwise it is a list of multiple inputs
      : curValue.map((thisUUID) => <input type="hidden" name={objectKey} value={thisUUID} key={thisUUID}/>);
  } else if (typeof curValue == "string") {
    // If a question shows up in multiple questionnaires, it may show up as a comma delimited list of UUIDs
    // If so, we need to map it to multiple inputs
    hiddenInput = curValue.split(",").map((thisUUID) => <input type="hidden" name={objectKey} value={thisUUID} key={thisUUID}/>);
  }

  return (
    <EditorInput name={objectKey}>
      <input type="hidden" name={objectKey + "@TypeHint"} value='Reference' />
      {hiddenInput}
      <Select
        variant="standard"
        id={objectKey}
        value={curValue || []}
        onChange={(event) => {changeCurValue(event.target.value);}}
        input={<Input id={objectKey} />}
        renderValue={(value) => titleMap[value]}>
        {getReactComponentFromFields(options)}
      </Select>
    </EditorInput>
  );
}

ReferenceInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

const StyledReferenceInput = withStyles(LiveTableStyle)(ReferenceInput);
export default StyledReferenceInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (definition.type && definition.type === "reference") {
    return [StyledReferenceInput, 100];
  }
});

