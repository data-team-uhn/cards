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
import {
  Checkbox,
  Grid,
  Typography,
  withStyles
} from "@material-ui/core";

import QuestionnaireStyle from './QuestionnaireStyle';
import QuestionComponentManager from "./QuestionComponentManager";

// Boolean Input field used by Edit dialog component

let BooleanInput = (props) => {
  let { objectKey, data, definition } = props;
  let formatString = (key) => {
    let formattedString = key.charAt(0).toUpperCase() + key.slice(1);
      return formattedString.split(/(?=[A-Z])/).join(' ');
  }
  return (
    <Grid container alignItems='flex-end' spacing={2} key={objectKey || ''}>
      <Grid item xs={6}><Typography>{ formatString(objectKey) || '' }</Typography></Grid>
      <Grid item xs={6}><Checkbox name={objectKey || ''} id={objectKey || ''} defaultValue={data[objectKey] || ''} /></Grid>
    </Grid>
  )
}

BooleanInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

const StyledBooleanInput = withStyles(QuestionnaireStyle)(BooleanInput);
export default StyledBooleanInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (definition === 'boolean') {
    return [StyledBooleanInput, 50];
  }
});

