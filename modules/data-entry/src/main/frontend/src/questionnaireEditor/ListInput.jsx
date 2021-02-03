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

let ListInput = (props) => {
  let { objectKey, data, value: type } = props;
  let [ value, setValue ] = React.useState(Array.isArray(data[objectKey]) ? data[objectKey] : data[objectKey] ? [data[objectKey]] : []);
  const [ options, setOptions ] = React.useState([]);

  if (options.length === 0) {
    fetch('/query?query=' + encodeURIComponent(`select * from [${type.primaryType}] as n order by n.'${type.orderVariable}'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let optionTypes = Array.from(json["rows"]); setOptions(optionTypes);
        let updatedValues = [];
        for (let option of optionTypes) {
          for (let val of value) {
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
        value.map((typeObject) => <input type="hidden" name={objectKey} value={typeObject[type.saveVariable]} key={typeObject["jcr:uuid"]} />)
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
        input={<Input id={objectKey} />}
        renderValue={(value) => (
          <div>
            {value.map((val) => (
              <Chip key={val["jcr:uuid"]} label={val[type.displayVariable]}/>
            ))}
          </div>
        )}
      >
      {options.map((name) => (
        <MenuItem key={name["jcr:uuid"]} value={name}>
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
  if (definition.type && definition.type === "list") {
    // typeof(definition) === "string" && definition.startsWith("list")) {
    return [StyledListInput, 50];
  }
});

