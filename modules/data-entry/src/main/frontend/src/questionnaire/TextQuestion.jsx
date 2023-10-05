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

import { Typography } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import PropTypes from "prop-types";

import MultipleChoice from "./MultipleChoice";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

// Component that renders a multiple choice question, with optional text input.
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional arguments:
//  max: Integer denoting maximum number of arguments that may be selected
//  min: Integer denoting minimum number of arguments that may be selected
//  text: String containing the question to ask
//  defaults: Array of arrays, each with two values, a "label" which will be displayed to the user,
//            and a "value" denoting what will actually be stored
//  validationRegexp: String of a regular expression tested against the input
//  validationErrorText: String to display when the regexp is not matched
//  displayMode: Either "input", "textbox", or undefined denoting the type of
//             user input. Currently, only "input" is supported
//
// sample usage:
// <TextQuestion
//    text="Test text question (lowercase only)"
//    defaults={[
//      ["One", "1"],
//      ["Two", "2"],
//      ["Three", "3"]
//    ]}
//    displayMode={"input"}
//    validationRegexp={"[a-z]+"}
//    validationErrorText={"Please enter a lowercase input"}
//    />
function TextQuestion(props) {
  let { displayMode, validationRegexp, validationErrorText } = {...props, ...props.questionDefinition};
  const [errorMessage, setErrorMessage] = useState();
  const regexpTest = new RegExp(validationRegexp);

  // Callback function if a regex is defined
  let validate = (text) => {
    if (validationRegexp) {
      setErrorMessage(!!!text || regexpTest.test(text) ? undefined : validationErrorText);
    }
  }

  return (
    <Question
      disableInstructions
      {...props}
      >
      <MultipleChoice
        input={displayMode === "input" || displayMode === "list+input"}
        textbox={displayMode === "textbox"}
        onUpdate={validate}
        errorMessage={errorMessage}
        {...props}
        />
    </Question>);
}

TextQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    minAnswers: PropTypes.number,
    maxAnswers: PropTypes.number,
    displayMode: PropTypes.oneOf([undefined, "input", "textbox", "list", "list+input", "hidden"]),
    validationRegexp: PropTypes.string,
    validationErrorText: PropTypes.string,
  }).isRequired,
  text: PropTypes.string,
  minAnswers: PropTypes.number,
  maxAnswers: PropTypes.number,
  defaults: PropTypes.array,
  validationErrorText: PropTypes.string
};

TextQuestion.defaultProps = {
  validationErrorText: "Invalid input"
};

const StyledTextQuestion = withStyles(QuestionnaireStyle)(TextQuestion)
export default StyledTextQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  return [StyledTextQuestion, 0];
});
