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

import React, { useContext, useState } from "react";
import PropTypes, { object } from 'prop-types';
import { Input, MenuItem, Select, Typography, withStyles } from "@material-ui/core";

import EditorInput from "./EditorInput";
import LiveTableStyle from "../dataHomepage/tableStyle.jsx";
import QuestionComponentManager from "./QuestionComponentManager";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

// Use the filter code to get what sorts of variables can be used as references
let FILTER_URL = "/Questionnaires.deep.json";
// TODO: Figure out what we should do with the limit in the URL below
let SUBJECT_TYPE_URL = "/SubjectTypes.paginate?offset=0&limit=100&req=0";

// Reference Input field used by Edit dialog component
let ReferenceInput = (props) => {
  const { classes, objectKey, data, value } = props;

  let [ curValue, setCurValue ] = useState(data[objectKey] || []);
  let [ titleMap, setTitleMap ] = useState({});
  const [ options, setOptions ] = React.useState([]);
  const [ initialized, setInitialized ] = React.useState(false);

  const isNumeric = value.filter == "numeric";
  const globalLoginDisplay = useContext(GlobalLoginContext);

  // Obtain information about the questions that can be used as a reference
  let grabData = (urlBase, parser) => {
    setInitialized(true);
    let url = new URL(urlBase, window.location.origin);

    fetchWithReLogin(globalLoginDisplay, url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(parser)
      .catch(console.log)
      .finally(() => {setInitialized(true);});
  };

  // Parse the response from examining every questionnaire
  let parseQuestionnaireData = (questionnaireJson) => {
    // newFilterableFields is a list of fields that can be filtered on
    // It is a list of either strings (for options) or recursive lists
    // Each recursive list must have a string for its 0th option, which
    // is taken to be its title
    let newFilterableFields = [""];
    // newFilterableTitles is a mapping from a string in newFilterableFields to a human-readable title
    let newFilterableTitles = {"": ""};

    // We'll need a helper recursive function to copy over data from sections/questions
    let parseSectionOrQuestionnaire = (sectionJson, path="") => {
      let retFields = [];
      for (let [title, object] of Object.entries(sectionJson)) {
        // We only care about children that are cards:Questions or cards:Sections
        if (object["jcr:primaryType"] == "cards:Question") {
          // If this is an cards:Question, copy the entire thing over to our Json value
          retFields.push(object["jcr:uuid"]);
          newFilterableTitles[object["jcr:uuid"]] = object["text"];
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
  }

  let parseFilterData = (filterJson) => {
    // Parse through, but keep a custom field for the subject
    let fields = [""];
    let titles = {"": ""};

    for (let [_, question] of Object.entries(filterJson)) {
      // If we only accept numeric references, exclude entries that are not numeric
      if (isNumeric && !["long", "double", "decimal"].includes(question["dataType"])) {
        continue;
      }

      // For each reference, store its UUID and title
      fields.push(question["jcr:uuid"]);
      titles[question["jcr:uuid"]] = question["text"];
    }

    setOptions(fields);
    setTitleMap(titles);
  }

  let parseSubjectTypeData = (subjectTypeJson) => {
    // Parse through, but keep a custom field for the subject
    let fields = [""];
    let titles = {"": ""};

    for (let subjectType of subjectTypeJson["rows"]) {
      // For each reference, store its UUID and title
      fields.push(subjectType["jcr:uuid"]);
      titles[subjectType["jcr:uuid"]] = subjectType["label"];
    }
    setOptions(fields);
    setTitleMap(titles);
  }

  // From an array of fields, turn it into a react component
  let GetReactComponentFromFields = (fields, nested=false) => {
    return fields.map((path) => {
      if (typeof path == "string") {
        // Straight strings are MenuItems
        return <MenuItem value={path} key={path} className={classes.categoryOption + (nested ? " " + classes.nestedSelectOption : "")}>{titleMap[path]}</MenuItem>
      } else if (Array.isArray(path)) {
        // Arrays represent Questionnaires of Sections
        // which we'll need to turn into opt groups
        return [<MenuItem className={classes.categoryHeader} disabled>{path[0]}</MenuItem>,
          GetReactComponentFromFields(path.slice(1), true)];
      }
    })
  }

  if (!initialized) {
    if (value["primaryType"] == "cards:SubjectType") {
      grabData(SUBJECT_TYPE_URL, parseSubjectTypeData);
    } else if (value["primaryType"] == "cards:Question") {
      grabData(FILTER_URL, parseQuestionnaireData);
    }
  }

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
        id={objectKey}
        value={curValue || []}
        onChange={(event) => {setCurValue(event.target.value);}}
        input={<Input id={objectKey} />}
        renderValue={(value) => titleMap[value]}
      >
        {GetReactComponentFromFields(options)}
      </Select>
    </EditorInput>
  )
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

