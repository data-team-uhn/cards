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
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle";
import QuestionComponentManager from "./QuestionComponentManager";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

// Use the filter code to get what sorts of variables can be used as references
let FILTER_URL = "/Questionnaires.filters";
// TODO: Figure out what we should do with the limit in the URL below
let SUBJECT_TYPE_URL = "/SubjectTypes.paginate?offset=0&limit=100&req=0";

// Reference Input field used by Edit dialog component
let ReferenceInput = (props) => {
  const { objectKey, data, value } = props;

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
    subjectTypeJson["Subject"] = {
      dataType: "subject"
    };
    subjectTypeJson["Questionnaire"] = {
      dataType: "questionnaire"
    };
    setOptions(fields);
    setTitleMap(titles);
  }

  if (!initialized) {
    if (value["primaryType"] == "cards:SubjectType") {
      grabData(SUBJECT_TYPE_URL, parseSubjectTypeData);
    } else if (value["primaryType"] == "cards:Question") {
      grabData(FILTER_URL, parseFilterData);
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
        {options.map((uuid, index) => (
          <MenuItem key={uuid + index} value={uuid}>
            <Typography>{titleMap[uuid]}</Typography>
          </MenuItem>
        ))}
      </Select>
    </EditorInput>
  )
}

ReferenceInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

const StyledReferenceInput = withStyles(QuestionnaireStyle)(ReferenceInput);
export default StyledReferenceInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (definition.type && definition.type === "reference") {
    return [StyledReferenceInput, 100];
  }
});

