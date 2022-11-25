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

import { TextField } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import EditorInput from "./EditorInput";
import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import QuestionComponentManager from "../questionnaireEditor/QuestionComponentManager";
import ValueComponentManager from "../questionnaireEditor/ValueComponentManager";

// Text Input field used by Edit dialog component
let TextInput = (props) => {
  let { objectKey, data, multiline, variant, hint } = props;

  return (
    <EditorInput name={objectKey} hint={hint}>
      <TextField
        name={objectKey}
        id={objectKey}
        defaultValue={typeof(data[objectKey]) != 'undefined' ? data[objectKey] : ''}
        required={objectKey.includes('text')}
        fullWidth
        multiline={multiline}
        variant={variant || "standard"}
      />
    </EditorInput>
  )
}

TextInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired,
  hint: PropTypes.string,
};

const StyledTextInput = withStyles(QuestionnaireStyle)(TextInput);
export default StyledTextInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  return [StyledTextInput, 0];
});


// Generic value displayer
let TextValue = (props) => {
  let { objectKey, data } = props;

  return (
    Array.isArray(data[objectKey]) ?
      data[objectKey].map(item => <div>{item}</div>)
    : <div>{data[objectKey]}</div>
  );
};

ValueComponentManager.registerValueComponent((definition) => {
  return [TextValue, 0];
});
