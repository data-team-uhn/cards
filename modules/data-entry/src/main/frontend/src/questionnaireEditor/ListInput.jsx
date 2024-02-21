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

import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Chip, Input, MenuItem, Select, Typography } from "@mui/material";
import withStyles from '@mui/styles/withStyles';

import EditorInput from "./EditorInput";
import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import QuestionComponentManager from "./QuestionComponentManager";
import ValueComponentManager from "./ValueComponentManager";
import { useFieldsWriterContext } from "./FieldsContext";

let ListInput = (props) => {
  let { objectKey, data, value: type, hint } = props;
  let [ selection, setSelection ] = useState(Array.of(data[objectKey] ?? []).flat());
  const [ options, setOptions ] = useState([]);
  const changeFieldsContext = useFieldsWriterContext();

  let changeValue = (val) => {
    changeFieldsContext((oldContext) => ({...oldContext, [objectKey]: val}));
    setSelection(Array.of(val ?? []).flat().filter(v => v?.[type.identifierProperty] != ''));
  }

  useEffect(() => {
    fetch('/query?query=' + encodeURIComponent(`select * from [${type.primaryType}] as n order by n.'${type.orderProperty}'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let listOptions = Array.from(json?.rows ?? []);
        if (listOptions.length == 0 && selection.length == 0) {
          return;
        }
        let updatedValues = [];
        for (let val of selection) {
          let found = false;
          for (let option of listOptions) {
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
            listOptions.push(newOption);
            updatedValues.push(newOption);
          }
        }
        // Include an empty option to allow unselecting a single value
        if (!type.multiple) {
          listOptions.splice(0, 0, {
            [type.identifierProperty]: '',
            [type.displayProperty]: ' '
          });
        }
        setOptions(listOptions);
        changeValue(updatedValues);
      })
      .catch(handleError);
  }, []);

  let handleError = () => {
    console.log('error');
  }

  const handleChange = (event) => {
    changeValue(event.target.value);
  };

  return (
    <EditorInput name={objectKey} hint={hint}>
      <input type="hidden" name={objectKey + "@TypeHint"} value={type.saveType + (type.multiple ? '[]' : '') } />
      {
        // Maps each selected object to a reference type for submitting
        selection.map((val, index) => <input type="hidden" name={objectKey} value={val[type.identifierProperty]} key={val[type.identifierProperty] + index} />)
      }
      {
        // Delete the current values within this list if nothing is selected
        selection.length == 0 && <input type="hidden" name={objectKey + "@Delete"} value="" />
      }
      <Select
        variant="standard"
        id={objectKey}
        multiple={type.multiple}
        value={type.multiple ? selection : (selection?.[0] ?? '')}
        onChange={handleChange}
        input={<Input id={objectKey} />}
        renderValue={type.multiple ? () => (
          <div>
            {selection.map((val, index) => (
              <Chip key={val[type.identifierProperty] + index} label={val[type.displayProperty]}/>
            ))}
          </div>
        ) : undefined}
      >
      {options.map((option, index) => (
        <MenuItem key={option[type.identifierProperty] + index} value={option}>
          <Typography>{option[type.displayProperty]}</Typography>
        </MenuItem>
      ))}
    </Select>
  </EditorInput>
  )
}

ListInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired,
  hint: PropTypes.string,
};

var StyledListInput = withStyles(QuestionnaireStyle)(ListInput);
export default StyledListInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (definition.type && definition.type === "list") {
    return [StyledListInput, 50];
  }
});

// List value displayer: display "Any" for an empty value array
let ListValue = (props) => {
  let { objectKey, value, data } = props;
  return (Array.of(data[objectKey] ?? []).flat().map(e => (e[value.displayProperty] ?? e)).join(', ') || 'Any');
};

ValueComponentManager.registerValueComponent((definition) => {
  if (definition.type && definition.type === "list") {
    return [ListValue, 50];
  }
});
