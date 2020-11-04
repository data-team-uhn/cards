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
import { TextField, Typography, withStyles } from "@material-ui/core";

import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";
import Question from "./Question";
import QuestionnaireStyle from './QuestionnaireStyle';
import { useFormReaderContext } from "./FormContext";

let ComputedQuestion = (props) => {
  const { existingAnswer, classes, ...rest} = props;
  const { text, expression } = {...props.questionDefinition, ...props};
  const [error, changeError] = useState(false);
  const [errorMessage, changeErrorMessage] = useState(false);

  let initialValue = existingAnswer && existingAnswer[1].value || "";
  const [value, changeValue] = useState(initialValue);
  const [answer, changeAnswer] = useState(initialValue === "" ? [] : [["value", initialValue]]);

  let setError = (input) => {
    if (error !== input) changeError(input);
  }
  let setErrorMessage = (input) => {
    if (errorMessage !== input) changeErrorMessage(input);
  }
  let setValue = (input) => {
    if (value !== input) {
      changeValue(input);
      changeAnswer(input ? [["value", input]] : []);
    }
  }

  let missingValue = false;
  let getQuestionValue = (name, form) => {
    let value;
    if (form[name] && form[name][0]) {
      value = form[name][0][1];
    }
    if (typeof(value) === undefined || value === "") {
      missingValue = true;
    }
    return value;
  }

  let evaluateExpression = () => {
    let form = useFormReaderContext();
    let result;
    let expressionError;
    try {
      expressionError = null;
      result = new Function(["form", "getQuestionValue", "setError"], expression)
        (form, getQuestionValue, (errorMessage) => {expressionError = errorMessage});
      if (missingValue || typeof(result) === undefined || isNaN(result)) {
        result = "";
      }
    }
    catch(err) {
      result = "";
    }
    if (expressionError) {
      setError(true);
      setErrorMessage(expressionError);
      result = "";
    } else {
      setError(false);
    }

    setValue(result);
  }

  return (
    <Question
      text={text}
      {...rest}
      >
      {error && <Typography color='error'>{errorMessage}</Typography>}
      <TextField
        readOnly={true}
        className={classes.textField + " " + classes.answerField}
        value={value}
        onChange={evaluateExpression()}
      />
      <Answer
        answers={answer}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="lfs:ComputedAnswer"
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
    description: PropTypes.string
  }).isRequired
};

const StyledComputedQuestion = withStyles(QuestionnaireStyle)(ComputedQuestion);
export default StyledComputedQuestion;

AnswerComponentManager.registerAnswerComponent((definition) => {
  if (definition.dataType === "computed") {
    return [StyledComputedQuestion, 50];
  }
});
