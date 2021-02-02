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

import React from 'react';
import PropTypes from 'prop-types';
import { Chip, Input, MenuItem, Select, Typography, withStyles } from "@material-ui/core";

import EditorInput from "./EditorInput";
import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import QuestionComponentManager from "./QuestionComponentManager";

class VocabularyDefinition {
  static primaryType = "lfs:Vocabulary";
  static saveType = "String";
  static displayVariable = "identifier";
  static orderVariable = this.primaryType;
  static saveVariable = this.displayVariable;
  static uniqueIdentifier = "jcr:uuid";
}
class SubjectTypeDefinition {
  static primaryType = "lfs:SubjectType";
  static saveType = "Reference";
  static displayVariable = "label";
  static orderVariable = "lfs:defaultOrder";
  static saveVariable = "jcr:uuid";
  static uniqueIdentifier = "jcr:uuid";
}

let ListInput = (props) => {
  let { objectKey, data, value: fieldDefinition } = props;
  let [ value, setValue ] = React.useState(Array.isArray(data[objectKey]) ? data[objectKey] : data[objectKey] ? [data[objectKey]] : []);
  const [ options, setOptions ] = React.useState([]);
  const requiredSubjectTypes = React.useState(objectKey.includes('requiredSubjectTypes'));

  let type;

  switch (fieldDefinition) {
    case "list.vocabularies":
      type = VocabularyDefinition;
      break;
    default:
    case "list.subjectTypes":
      // Default to subject types
      type = SubjectTypeDefinition;
  }

  if (requiredSubjectTypes && options.length === 0) {
    fetch('/query?query=' + encodeURIComponent(`select * from [${type.primaryType}] as n order by n.'${type.orderVariable}'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let optionTypes = Array.from(json["rows"]); setOptions(optionTypes);
        let updatedValues = [];
        for (let option in optionTypes) {
          for (let val in value) {
            let compareVal = typeof(val) === "string" ? val : val[type.saveVariable];
            if (compareVal === option[type.saveVariable]) {
              updatedValues.push(option);
            }
          }
        }
        setValue(updatedValues);
      })
      .catch(handleError);
  }

  let handleError = () => {
    console.log('error');
  }

  const handleChange = (event) => {
    setValue(event.target.value);
  };

  return (
    <EditorInput name={objectKey}>
      <input type="hidden" name={objectKey + "@TypeHint"} value={type.saveType} />
      {
        // Maps each selected object to a reference type for submitting
        value.map((typeObject) => <input type="hidden" name={objectKey} value={typeObject[type.saveVariable]} key={typeObject[type.uniqueIdentifier]} />)
      }
      {
        // Delete the current values within this list if nothing is selected
        value.length == 0 && <input type="hidden" name={objectKey + "@Delete"} value="" />
      }
      <Select
        id={objectKey}
        multiple
        value={value}
        onChange={handleChange}
        input={requiredSubjectTypes ? <Input id={objectKey} /> : null}
        renderValue={(value) => (
          <div>
            {value.map((val) => (
              <Chip key={val[type.uniqueIdentifier]} label={val[type.displayVariable]}/>
            ))}
          </div>
        )}
      >
      {options.map((name) => (
        <MenuItem key={name[type.uniqueIdentifier]} value={name}>
          <Typography>{name[type.displayVariable]}</Typography>
        </MenuItem>
      ))}
    </Select>
  </EditorInput>
  )
}

ListInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

var StyledListInput = withStyles(QuestionnaireStyle)(ListInput);
export default StyledListInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (typeof(definition) === "string" && definition.startsWith("list")) {
    return [StyledListInput, 50];
  }
});

