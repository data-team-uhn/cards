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

import React, { useState } from "react";
import PropTypes from 'prop-types';
import { InputAdornment, TextField, Typography, withStyles } from "@material-ui/core";

import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";
import Question from "./Question";
import FormattedText from "../components/FormattedText";
import QuestionnaireStyle from './QuestionnaireStyle';
import { useFormReaderContext } from "./FormContext";


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
//<ComputedQuestion
//  text="Result of of a divided by b"
//  expression="if (@{question_b} === 0) setError('Can not divide by 0'); return @{question_a}/@{question_b}"
//  />
let ComputedQuestion = (props) => {
  const { existingAnswer, classes, ...rest} = props;
  const { text, expression, unitOfMeasurement, isFormatted } = {...props.questionDefinition, ...props};
  const [error, changeError] = useState(false);
  const [errorMessage, changeErrorMessage] = useState(false);

  let initialValue = existingAnswer?.[1].value || "";
  const [value, changeValue] = useState(initialValue);
  const [answer, changeAnswer] = useState(initialValue === "" ? [] : [["value", initialValue]]);

  const form = useFormReaderContext();
  const startTag = "@{";
  const endTag = "}";
  const defaultTag = ":-";

  let setError = (input) => {
    if (error !== input) changeError(input);
  }
  let setErrorMessage = (input) => {
    if (errorMessage !== input) changeErrorMessage(input);
  }
  let setValue = (input) => {
    if (value !== input) {
      changeValue(input);
      changeAnswer([["value", typeof(input) === "undefined" ? "" : input]]);
    }
  }

  let missingValue = false;
  let getQuestionValue = (name, form, defaultValue) => {
    let value;
    if (form[name] && form[name][0]) {
      value = form[name][0][1];
    }
    if (typeof(value) === "undefined" || value === "") {
      if (defaultValue != null) {
        value = defaultValue;
      } else {
        missingValue = true;
      }
    }
    if (!isNaN(Number(value))) {
      value = Number(value);
    }
    return value;
  }

  let parseExpressionInputs = (expr, form) => {
    let inputNames = [];
    let inputValues = [];
    let start = expr.indexOf(startTag);
    let end = expr.indexOf(endTag, start);
    while(start > -1 && end > -1) {
      let optionStart = expr.indexOf(defaultTag, start);
      let hasOption = (optionStart > -1 && optionStart < end);
      // Divide the text between the start and end tag the question name and a default value if provided
      let inputName;
      let defaultValue = null;
      if (hasOption) {
        inputName = expr.substring(start + startTag.length, optionStart);
        defaultValue = expr.substring(optionStart + defaultTag.length, end);
      } else {
        inputName = expr.substring(start + startTag.length, end);
      }
      if (!inputNames.includes(inputName)) {
        inputNames.push(inputName);
        inputValues.push(getQuestionValue(inputName, form, defaultValue));
      }
      // Remove the start and end tags as well as the default option if provided
      expr = [expr.substring(0, start), expr.substring(start + startTag.length, hasOption ? optionStart : end), expr.substring(end + endTag.length)].join('');
      start = expr.indexOf(startTag, hasOption ? optionStart : end - startTag.length);
      end = expr.indexOf(endTag, start);
    }
    return [inputNames, inputValues, expr];
  }

  let evaluateExpression = () => {
    let result;
    let expressionError = null;
    try {
      let parseResults = parseExpressionInputs(expression, form);
      let expressionArguments = ["form", "setError"].concat(parseResults[0]);
      result = new Function(expressionArguments, parseResults[2])
        (form, (errorMessage) => {expressionError = errorMessage}, ...parseResults[1]);
      if (missingValue || typeof(result) === "undefined" || (typeof(result) === "number" && isNaN(result))) {
        result = "";
      }
    }
    catch(err) {
      console.error(`Error encountered evaluating expression:\n${expression}\n`, err);
      result = "";
    }
    if (expressionError) {
      setError(true);
      setErrorMessage(`Error encountered evaluating expression:\n${expressionError}`);
      result = "";
    } else {
      setError(false);
    }

    setValue(typeof(result) === "undefined" ? "" : result.toString());
  }

  const muiInputProps = {}
  if (unitOfMeasurement) {
    muiInputProps.endAdornment = <InputAdornment position="end">{unitOfMeasurement}</InputAdornment>;
  }

  evaluateExpression();

  return (
    <Question
      defaultDisplayFormatter={isFormatted ? (label, idx) => <FormattedText>{label}</FormattedText> : undefined}
      currentAnswers={typeof(value) !== "undefined" && value !== "" ? 1 : 0}
      {...props}
      >
      {error && <Typography color='error'>{errorMessage}</Typography>}
      {isFormatted ?
      <FormattedText>{value + (unitOfMeasurement ? (" " + unitOfMeasurement) : '')}</FormattedText>
      :
      <TextField
        multiline
        disabled={true}
        className={classes.textField + " " + classes.answerField}
        value={value}
        InputProps={muiInputProps}
        onChange={evaluateExpression()}
      />
      }
      <Answer
        answers={answer}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="cards:ComputedAnswer"
        valueType="computed"
        {...rest}
        />
    </Question>
  )
}

ComputedQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    expression: PropTypes.string.isRequired,
    description: PropTypes.string,
    unitOfMeasurement: PropTypes.string
  }).isRequired
};

const StyledComputedQuestion = withStyles(QuestionnaireStyle)(ComputedQuestion);
export default StyledComputedQuestion;

AnswerComponentManager.registerAnswerComponent((definition) => {
  if (definition.dataType === "computed") {
    return [StyledComputedQuestion, 50];
  }
});
