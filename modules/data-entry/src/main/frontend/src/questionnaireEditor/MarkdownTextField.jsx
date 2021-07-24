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
import { withStyles } from "@material-ui/core";

import EditorInput from "./EditorInput";
import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import QuestionComponentManager from "../questionnaireEditor/QuestionComponentManager";
import MarkdownText from "./MarkdownText";

// Markdown Text Input field used by Edit dialog component
let MarkdownTextField = (props) => {
  let { objectKey, data, onChange, classes } = props;
  const [value, setValue] = useState(data[objectKey] || '');

  return (
    <EditorInput name={objectKey}>
      <MarkdownText value={value} onChange={value => {setValue(value); onChange && onChange(value);}} />
      <input type="hidden" name={objectKey} value={value} />
    </EditorInput>
  )
}

MarkdownTextField.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired,
  onChange: PropTypes.func
};

const StyledMarkdownTextField = withStyles(QuestionnaireStyle)(MarkdownTextField);
export default StyledMarkdownTextField;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (definition === 'markdown') {
    return [StyledMarkdownTextField, 50];
  }
});
