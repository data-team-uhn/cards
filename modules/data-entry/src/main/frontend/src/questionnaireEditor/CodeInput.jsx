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

import TextInput from "./TextInput";
import FormattedText from "../components/FormattedText.jsx";
import QuestionComponentManager from "../questionnaireEditor/QuestionComponentManager";
import ValueComponentManager from "../questionnaireEditor/ValueComponentManager";

// Code Input field used by Edit dialog component
let CodeInput = (props) => <TextInput {...props} multiline variant="filled" />

CodeInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (definition === 'code') {
    return [CodeInput, 50];
  }
});


// Formatted code block
let CodeBlock = (props) => {
  let { objectKey, data } = props;
  return <FormattedText>{"```\n" + data[objectKey] + "\n```"}</FormattedText>
};

ValueComponentManager.registerValueComponent((definition) => {
    if (definition == "code") {
      return [CodeBlock, 50];
    }
});
