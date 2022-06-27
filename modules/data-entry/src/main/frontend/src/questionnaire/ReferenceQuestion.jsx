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
import { InputAdornment, TextField, Typography } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";
import DateQuestionUtilities from "./DateQuestionUtilities";
import Question from "./Question";
import {Time} from "./TimeQuestion";
import FormattedText from "../components/FormattedText";
import QuestionnaireStyle from './QuestionnaireStyle';
import { useFormReaderContext } from "./FormContext";
import { MakeRequest } from "../vocabQuery/util.jsx";


// Component that renders a computed value as a question field
// Computed value is placed in a <input type="hidden"> tag for submission.
//
// Mandatory props:
// text: the question to be displayed
// expression: the javascript run to determine the value.
//   Values between the tags `@{` and `}` will be interpreted as input variables.
//   These tags will be removed and the value of the question named by the
//     input variable will be provided as a function argument.
//
// Other options are passed to the <question> widget
//
// Sample usage, given question_a and question_b are number questions:
//<ReferenceQuestion
//  />
let ReferenceQuestion = (props) => {
  const { existingAnswer, classes, pageActive, questionDefinition, ...rest} = props;
  const { text, unitOfMeasurement, dataType, displayMode, dateFormat } = {...props.questionDefinition, ...props};

  let initialValue = existingAnswer?.[1].value || "";
  const [displayValue, changeDisplayValue] = useState(initialValue);
  const [answer, changeAnswer] = useState(initialValue === "" ? [] : [["value", initialValue]]);
  const [fieldType, changeFieldType] = useState("string")
  const [muiInputProps, changeMuiInputProps] = useState({});
  const [isFormatted, changeIsFormatted] = useState(false);

  const form = useFormReaderContext();

  let setFieldType = (value) => {
    if (fieldType !== value) {
      changeFieldType(value);
    }
  }

  useEffect(() => {
    if (unitOfMeasurement) {
      muiInputProps.endAdornment = <InputAdornment position="end">{unitOfMeasurement}</InputAdornment>;
    } else {
      delete muiInputProps.endAdornment;
    }
  }, [unitOfMeasurement])

  useEffect(() => {
    let formatted = (displayMode === "formatted" || displayMode === "summary");
    if (formatted !== isFormatted) {
      changeIsFormatted(formatted)
    };
  }, [displayMode])

  let capitalizedDataType = dataType.substring(0, 1).toUpperCase() + dataType.substring(1);
  let answerType, answerNodeType = `cards:${capitalizedDataType}Answer`, newFieldType;
  switch (dataType) {
    case "boolean":
      answerType = "Long"; // Long, not Boolean
      break;
    case "date":
      newFieldType = DateQuestionUtilities.getFieldType(dateFormat);
      setFieldType(newFieldType);
      switch (newFieldType) {
        case "long":
          answerType = "Long";
          break;
        case "string":
          answerType = "Date";
          break;
        default:
          answerType = "Date";
          break;
      }
      break;
    case "long":
    case "double":
    case "decimal":
      answerType = capitalizedDataType;
      break;
    case "vocabulary":
      answerType = "String";
      break;
    case "time":
      answerType = "String";
      newFieldType = Time.timeQuestionFieldType(dateFormat);
      setFieldType(newFieldType);
      break;
    case "text":
    default: // Fallthrough default to string reference
      answerType = "String";
      answerNodeType = "cards:TextAnswer"
      break;
  }

  return (
    <Question
      defaultDisplayFormatter={isFormatted ? (label, idx) => <FormattedText>{label}</FormattedText> : undefined}
      currentAnswers={typeof(displayValue) !== "undefined" && displayValue !== "" ? 1 : 0}
      {...props}
      >
      {
        pageActive && <>
          { isFormatted ? <FormattedText>
              {displayValue + (unitOfMeasurement ? (" " + unitOfMeasurement) : '')}
            </FormattedText>
          :
          <TextField
            variant="standard"
            type={fieldType}
            disabled={true}
            className={classes.textField + " " + classes.answerField}
            value={displayValue}
            InputProps={muiInputProps}
          />
          }
        </>
      }
      <Answer
        answers={answer}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType={answerNodeType}
        valueType={answerType}
        pageActive={pageActive}
        {...rest}
        />
    </Question>
  )
}

ReferenceQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    description: PropTypes.string,
    questionnaire: PropTypes.string.isRequired,
    question: PropTypes.string.isRequired,
    displayMode: PropTypes.oneOf(['input', 'formatted', 'hidden', 'summary']),
    unitOfMeasurement: PropTypes.string
  }).isRequired
};

const StyledReferenceQuestion = withStyles(QuestionnaireStyle)(ReferenceQuestion);
export default StyledReferenceQuestion;

AnswerComponentManager.registerAnswerComponent((definition) => {
  if (definition.entryMode === "reference") {
    return [StyledReferenceQuestion, 80];
  }
});
