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
import { Grid, Typography, withStyles } from "@material-ui/core";

import QuestionnaireStyle from './QuestionnaireStyle';
import BooleanInput from './BooleanInput';
import LongInput from './LongInput';
import ObjectInput from './ObjectInput';
import StringInput from './StringInput';

let Fields = (props) => {
  let { data, JSON } = props;

  let formatString = (key) => {
    let formattedString = key.charAt(0).toUpperCase() + key.slice(1);
      return formattedString.split(/(?=[A-Z])/).join(' ');
    }

    let inputTypes = (key, value) => {
    return [
      {
        dataType: 'boolean',
        score: ( value === 'boolean' ? 60 : 10),
        component: (
          <Grid container alignItems='flex-end' spacing={2} key={key}>
            <Grid item xs={6}><Typography>{ formatString(key) }</Typography></Grid>
            <Grid item xs={6}><BooleanInput objectKey={key} data={data}/></Grid>
          </Grid>
        )
      },
      {
        dataType: 'string',
        score: ( value === 'string' ? 60 : 40),
        component: (
          <Grid container alignItems='flex-end' spacing={2} key={key}>
            <Grid item xs={6}><Typography>{ formatString(key) }</Typography></Grid>
            <Grid item xs={6}><StringInput objectKey={key} data={data}/></Grid>
          </Grid>
        )
      },
      {
        dataType: 'long',
        score: ( value === 'long' ? 60 : 10),
        component: (
          <Grid container alignItems='flex-end' spacing={2} key={key}>
            <Grid item xs={6}><Typography>{ formatString(key) }</Typography></Grid>
            <Grid item xs={6}><LongInput objectKey={key} data={data}/></Grid>
          </Grid>
        )
      },
      {
        dataType: 'object',
        score: ( typeof(value) === 'object' ? 60 : 0),
        component: (<ObjectInput objectKey={key} value={value} data={data}></ObjectInput>)
      }
    ]
  }
    
  let fieldInput = (key, value) => {
    return inputTypes(key, value).reduce((a,b) => a.score > b.score ? a : b).component;
  }
  
  return Object.entries(JSON).map(([key, value]) => (
    fieldInput(key, value)
  ));
}

Fields.propTypes = {
  data: PropTypes.object.isRequired,
  JSON: PropTypes.object.isRequired
};
  
export default withStyles(QuestionnaireStyle)(Fields);