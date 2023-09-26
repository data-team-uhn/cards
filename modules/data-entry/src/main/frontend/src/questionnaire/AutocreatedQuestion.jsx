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

import React, { useEffect, useState } from "react";
import PropTypes from 'prop-types';
import { InputAdornment, TextField } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import AnswerComponentManager from "./AnswerComponentManager";
import Question from "./Question";
import FormattedText from "../components/FormattedText";
import QuestionnaireStyle from './QuestionnaireStyle';
import { useFormWriterContext } from "./FormContext";

// Component that displays an autocreated question of any type.
//
// Other options are passed to the <question> widget
let AutocreatedQuestion = (props) => {
  const { isEdit, ...rest } = props;
  const { existingAnswer, classes, pageActive, questionName} = rest;
  const { unitOfMeasurement, displayMode } = {...props.questionDefinition, ...rest};

  const [muiInputProps, changeMuiInputProps] = useState({});
  const [isFormatted, changeIsFormatted] = useState(false);


  // If we are in edit mode, upon loading the pre-filled answers, place them
  // in the form context where they can be accessed by computed answers
  const changeFormContext = useFormWriterContext();

  useEffect(() => {
    if (isEdit) {
      let value = existingAnswer?.[1].value;
      if (typeof(value) != "undefined" && value != "") {
        let answer = Array.of(value).flat().map(v => [v, v]);
        changeFormContext((oldContext) => ({...oldContext, [questionName]: answer}));
      }
    }
  }, []);

  useEffect(() => {
    let formatted = (displayMode === "formatted" || displayMode === "summary");
    if (formatted !== isFormatted) {
      changeIsFormatted(formatted)
    };
  }, [displayMode])

  // Autocreated answers are read-only and displayed the same in view and edit modes
  // Answer instructions are not displayed since there's nothing the user can do in this form to actually follow them, as the answers are read-only
  return (
    <Question
      isEdit={false}
      defaultDisplayFormatter={isFormatted ? (label, idx) => <FormattedText>{label}</FormattedText> : (label, idx) => label}
      disableInstructions
      {...rest}
    />
  )
}

AutocreatedQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    description: PropTypes.string,
    displayMode: PropTypes.oneOf(['plain', 'formatted', 'hidden', 'summary']),
    unitOfMeasurement: PropTypes.string
  }).isRequired
};

const StyledAutocreatedQuestion = withStyles(QuestionnaireStyle)(AutocreatedQuestion);
export default StyledAutocreatedQuestion;

AnswerComponentManager.registerAnswerComponent((definition) => {
  if (definition.entryMode === "autocreated") {
    return [StyledAutocreatedQuestion, 80];
  }
});
