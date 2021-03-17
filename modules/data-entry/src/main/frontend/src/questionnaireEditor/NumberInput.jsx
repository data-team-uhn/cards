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
import {
  Grid,
  TextField,
  Typography,
  withStyles
} from "@material-ui/core";

import EditorInput from "./EditorInput";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle";
import QuestionComponentManager from "./QuestionComponentManager";

// Number Input field used by Edit dialog component

let NumberInput = (props) => {
  let { objectKey, data } = props;
  const defaultValue = props.value === "long" ? 0 : '';
  let [ value, setValue ] = useState(data[objectKey] || defaultValue);

  return (
    <EditorInput name={objectKey}>
      <TextField
        fullWidth
        name={objectKey || ''}
        id={objectKey || ''}
        type='number'
        placeholder={objectKey.includes('maxPerSubject') ? 'Unlimited' : ''}
        value={value}
        onChange={(event) => { setValue(event.target.value); }}
        onBlur={(event) => { setValue(event.target.value || defaultValue); }}
        InputProps={{
          inputProps: { 
            min: defaultValue 
          }
        }}
      />
    </EditorInput>
  )
}

NumberInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

const StyledNumberInput = withStyles(QuestionnaireStyle)(NumberInput);
export default NumberInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (["long", "double", "decimal"].includes(definition)) {
    return [StyledNumberInput, 50];
  }
});

