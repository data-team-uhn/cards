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

import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { Switch } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import EditorInput from "./EditorInput";
import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import QuestionComponentManager from "../questionnaireEditor/QuestionComponentManager";
import ValueComponentManager from "../questionnaireEditor/ValueComponentManager";

// Boolean Input field used by Edit dialog component

let BooleanInput = (props) => {
  let { objectKey, data, hint, onChange } = props;
  let [ checked, setChecked ] = useState(data?.[objectKey] == true);

  return (
    <EditorInput name={objectKey} hint={hint}>
      <Switch
        color="secondary"
        edge="start"
        id={objectKey}
        onChange={(event) => {setChecked(event.target.checked); onChange?.(event.target.checked)}}
        checked={checked}
        />
      <input type="hidden" name={objectKey} value={String(checked)} />
      <input type="hidden" name={objectKey + "@TypeHint"} value="Boolean" />
    </EditorInput>
  )
}

BooleanInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired,
  hint: PropTypes.string,
};

const StyledBooleanInput = withStyles(QuestionnaireStyle)(BooleanInput);
export default StyledBooleanInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (definition === 'boolean') {
    return [StyledBooleanInput, 50];
  }
});

// Boolean value displayer: yes/no
let BooleanValue = (props) => {
  let { objectKey, data } = props;
  return (data?.[objectKey] ? "Yes" : "No");
};

ValueComponentManager.registerValueComponent((definition) => {
  if (definition == "boolean" || typeof(definition) == "object" && Object.keys(definition || {}).length == 2 && definition?.["true"] && definition?.["false"]) {
    return [BooleanValue, 50];
  }
});
