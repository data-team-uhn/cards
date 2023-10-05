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

import React, { useState } from "react";
import PropTypes from 'prop-types';
import { TextField } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import EditorInput from "./EditorInput";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle";
import QuestionComponentManager from "./QuestionComponentManager";
import ValueComponentManager from "./ValueComponentManager";

// Number Input field used by Edit dialog component

let NumberInput = (props) => {
  let { objectKey, data, hint } = props;
  const type = props.value?.charAt(0).toUpperCase() + props.value?.slice(1).toLowerCase();
  const defaultValue = type === "Long" ? (objectKey == "maxAnswers" ? 1 : 0) : '';
  const minValue = type === "Long" ? 0 : '';
  const isMax = type === "Long" && objectKey.startsWith('max');

  let [ value, setValue ] = useState(typeof data[objectKey] != 'undefined' ? data[objectKey] : defaultValue);

  return (
    <EditorInput name={objectKey} hint={hint}>
      <TextField
        variant="standard"
        fullWidth
        name={objectKey || ''}
        id={objectKey || ''}
        type='number'
        placeholder={isMax ? 'Unlimited' : ''}
        value={value}
        onChange={(event) => { setValue(event.target.value); }}
        onBlur={(event) => { setValue(event.target.value || defaultValue); }}
        helperText={isMax ? `0 means "Unlimited"` : ''}
        InputProps={{
          inputProps: { 
            min: minValue
          }
        }}
      />
      <input type="hidden" name={objectKey + "@TypeHint"} value={type} />
    </EditorInput>
  )
}

NumberInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired,
  hint: PropTypes.string,
};

const StyledNumberInput = withStyles(QuestionnaireStyle)(NumberInput);
export default NumberInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (["long", "double", "decimal"].includes(definition)) {
    return [StyledNumberInput, 50];
  }
});

// Long value displayer: display Unlimited for a "max" = 0
let LongValue = (props) => {
  let { objectKey, data } = props;
  return (objectKey.startsWith("max") && !(data?.[objectKey]) ? "Unlimited" : data?.[objectKey]);
};

ValueComponentManager.registerValueComponent((definition) => {
  if (definition == "long") {
    return [LongValue, 50];
  }
});
