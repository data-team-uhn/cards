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
import { Grid, TextField, Typography, withStyles } from "@material-ui/core";

import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import QuestionComponentManager from "../questionnaireEditor/QuestionComponentManager";

// Text Input field used by Edit dialog component
let TextInput = (props) => {
  let { objectKey, data } = props;

  let formatString = (key) => {
    let formattedString = key.charAt(0).toUpperCase() + key.slice(1);
      return formattedString.split(/(?=[A-Z])/).join(' ');
  }

  return (
    <Grid container alignItems='flex-end' spacing={2} key={objectKey}>
      <Grid item xs={6}><Typography>{ formatString(objectKey)}</Typography></Grid>
      <Grid item xs={6}>
        <TextField name={objectKey} id={objectKey} defaultValue={data[objectKey] || ''} required={objectKey.includes('text')}/>
      </Grid>
    </Grid>
  )
}

TextInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

const StyledTextInput = withStyles(QuestionnaireStyle)(TextInput);
export default StyledTextInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  return [StyledTextInput, 0];
});
