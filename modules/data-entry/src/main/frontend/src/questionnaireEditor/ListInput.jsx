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
import { Chip, Input, MenuItem, Select, Typography } from "@mui/material";
import withStyles from '@mui/styles/withStyles';

import EditorInput from "./EditorInput";
import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import QuestionComponentManager from "./QuestionComponentManager";
import { useFieldsWriterContext } from "./FieldsContext";

let ListInput = (props) => {
  let { objectKey, data, value: type } = props;
  let [ value, setValue ] = React.useState(Array.isArray(data[objectKey]) ? data[objectKey] : data[objectKey] ? [data[objectKey]] : []);
  const [ options, setOptions ] = React.useState([]);
  const changeFieldsContext = useFieldsWriterContext();

  let changeValue = (val) => {
    changeFieldsContext((oldContext) => ({...oldContext, [objectKey]: val}));
    setValue(val);
  }

  if (options.length === 0) {
    fetch('/query?query=' + encodeURIComponent(`select * from [${type.primaryType}] as n order by n.'${type.orderProperty}'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let optionTypes = Array.from(json["rows"]);
        if (optionTypes.length == 0 && value.length == 0) {
          return;
        }
        let updatedValues = [];
        for (let val of value) {
          let found = false;
          for (let option of optionTypes) {
            let compareVal = typeof(val) === "string" ? val : val[type.identifierProperty];
            if (compareVal === option[type.identifierProperty]) {
              found = true;
              updatedValues.push(option);
            }
          }
          if (!found) {
            // Add the pre-existing value as an option
            let newOption = {};
            newOption[type.identifierProperty] = val;
            newOption["jcr:uuid"] = val;
            newOption[type.displayProperty] = val;
            optionTypes.push(newOption);
            updatedValues.push(newOption);
          }
        }
        setOptions(optionTypes);
        changeValue(updatedValues);
      })
      .catch(handleError);
  }

  let handleError = () => {
    console.log('error');
  }

  const handleChange = (event) => {
    changeValue(event.target.value);
  };

  return (
    <EditorInput name={objectKey}>
      <input type="hidden" name={objectKey + "@TypeHint"} value={type.saveType + '[]'} />
      {
        // Maps each selected object to a reference type for submitting
        value.map((typeObject, index) => <input type="hidden" name={objectKey} value={typeObject[type.identifierProperty]} key={typeObject + index} />)
      }
      {
        // Delete the current values within this list if nothing is selected
        value.length == 0 && <input type="hidden" name={objectKey + "@Delete"} value="" />
      }
      <Select
        variant="standard"
        id={objectKey}
        multiple
        value={value}
        onChange={handleChange}
        input={<Input id={objectKey} />}
        renderValue={(value) => (
          <div>
            {value.map((val, index) => (
              <Chip key={val+index} label={val[type.displayProperty]}/>
            ))}
          </div>
        )}
      >
      {options.map((name, index) => (
        <MenuItem key={name + index} value={name}>
          <Typography>{name[type.displayProperty]}</Typography>
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
    return [StyledListInput, 50];
  }
});