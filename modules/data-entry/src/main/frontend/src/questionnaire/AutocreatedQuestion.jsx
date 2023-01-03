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
  const { existingAnswer, classes, pageActive, questionName} = props;
  const { unitOfMeasurement, displayMode } = {...props.questionDefinition, ...props};

  let initialValue = existingAnswer?.[1].value || "";
  let answer = initialValue === "" ? [] : [["value", initialValue]];
  const [muiInputProps, changeMuiInputProps] = useState({});
  const [isFormatted, changeIsFormatted] = useState(false);

  // Hooks must be pulled from the top level, so this cannot be moved to inside the useEffect()
  const changeFormContext = useFormWriterContext();

  // When the answers change, we inform the FormContext
  useEffect(() => {
    if (answer) {
      changeFormContext((oldContext) => ({...oldContext, [questionName]: answer}));
    }
  }, [answer]);

  useEffect(() => {
    if (unitOfMeasurement) {
      changeMuiInputProps(muiInputProps => ({ ...muiInputProps, endAdornment: <InputAdornment position="end">{unitOfMeasurement}</InputAdornment>}));
    } else {
      changeMuiInputProps(muiInputProps => ({ ...muiInputProps, endAdornment: undefined}));
    }
  }, [unitOfMeasurement])

  useEffect(() => {
    let formatted = (displayMode === "formatted" || displayMode === "summary");
    if (formatted !== isFormatted) {
      changeIsFormatted(formatted)
    };
  }, [displayMode])

  return (
    <Question
      defaultDisplayFormatter={isFormatted ? (label, idx) => <FormattedText>{label}</FormattedText> : undefined}
      currentAnswers={typeof(initialValue) !== "undefined" && initialValue !== "" ? 1 : 0}
      {...props}
      >
      {
        pageActive && <>
          { isFormatted ? <FormattedText>
              {initialValue + (unitOfMeasurement ? (" " + unitOfMeasurement) : '')}
            </FormattedText>
          :
          <TextField
            variant="standard"
            disabled={true}
            className={classes.textField + " " + classes.answerField}
            value={initialValue}
            InputProps={muiInputProps}
          />
          }
        </>
      }
    </Question>
  )
}

AutocreatedQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    description: PropTypes.string,
    displayMode: PropTypes.oneOf(['input', 'formatted', 'hidden', 'summary']),
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
