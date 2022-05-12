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
import { Grid, Typography } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import QuestionComponentManager from "../questionnaireEditor/QuestionComponentManager";
import ValueComponentManager from "../questionnaireEditor/ValueComponentManager";
import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';

// Unused imports required for the component manager
import AnswerOptions from "./AnswerOptions";
import BooleanInput from "./BooleanInput";
import CodeInput from "./CodeInput";
import ConditionalValueInput from "./ConditionalValueInput";
import ListInput from "./ListInput";
import NumberInput from "./NumberInput";
import ObjectInput from "./ObjectInput";
import TextInput from "./TextInput";
import MarkdownTextField from "./MarkdownTextField";
import ReferenceInput from "./ReferenceInput";
import { FieldsProvider } from "./FieldsContext.jsx";

export function formatString(str) {
  return str.charAt(0).toUpperCase() + str.slice(1).replace( /([A-Z])/g, " $1" ).toLowerCase();
}

let Fields = (props) => {
  let { data, JSON, edit, classes, ...rest } = props;

  /**
   * Method responsible for displaying a question from the questionnaire
   *
   * @param {String} key the label of the question
   * @param {Object} value the data type of the question
   * @returns a React component that renders the question
   */
  let displayEditField = (key, value) => {
    // This variable must start with an upper case letter so that React treats it as a component
    const FieldDisplay = QuestionComponentManager.getQuestionComponent(value);
    return (
        <FieldDisplay
          key={key}
          objectKey={key}
          value={value}
          data={data}
          {...rest}
          />
    );
  };

  let hasValueToDisplay = (key, spec) => {
    return (
      typeof(data[key]) != "undefined" ||
      spec?.childrenType && Object.values(data).some(c => c["jcr:primaryType"] == spec.childrenType)
    );
  }

  let displayStaticField = (key, value) => {
    const ValueDisplay = ValueComponentManager.getValueComponent(value);

    if (!hasValueToDisplay(key, value)) return '';

    return (<React.Fragment key={key}>
      <Grid container alignItems='flex-start' spacing={2} direction="row" className="cards-questionnaire-entry-props">
        <Grid item xs={4}>
          <Typography variant="subtitle2">{formatString(key)}:</Typography>
        </Grid>
        <Grid item xs={8}>
          <ValueDisplay key={key} objectKey={key} value={value} data={data} />
        </Grid>
      </Grid>
      {
        typeof(value) == "object" && typeof(value[data[key]]) == "object"?
        Object.entries(value[data[key]]).filter(([k, _]) => !k.startsWith("//"))
                                        .map(([k, v]) => displayStaticField(k, v))
        : ""
      }
    </React.Fragment>);
  };

  // Note that we remove the meta fields, starting with `//`, such as `//REQUIRED which indicates which fields are mandatory
  return <FieldsProvider>
      {
          Object.entries(JSON).filter(([key, _]) => !key.startsWith("//"))
                              .map(([key, value]) => edit ? displayEditField(key, value) : displayStaticField(key, value))
      }
    </FieldsProvider>;
}

Fields.propTypes = {
  data: PropTypes.object.isRequired,
  JSON: PropTypes.object.isRequired,
  edit: PropTypes.bool.isRequired,
  onChange: PropTypes.func
};

export default withStyles(QuestionnaireStyle)(Fields);
