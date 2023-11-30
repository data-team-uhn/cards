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

import React from "react";

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
//    validationRegexp={"^[a-z]+$"}
//    validationErrorText={"Please enter a lowercase input"}
//    />
function TextQuestion(props) {
  let { dataType, displayMode, validationRegexp, validationErrorText } = {validationErrorText: "Invalid input", ...props.questionDefinition, ...props};
  const regexp = new RegExp(validationRegexp);
  const answerNodeType = "cards:" + dataType.charAt(0).toUpperCase() + dataType.slice(1) + "Answer";

  // Validation against the regular expression if one is provided
  // Empty inputs are considered valid
  // If no regexp is provided, all inputs are valid
  let validate = validationRegexp ? text => (!text || regexp.test(text)) : undefined;

  // If a validation regexp is provided, deplace the view mode formatter
  // with one that highlights the saved values which are invalid
  let displayFormatter = (
    validationRegexp ?
      (label, idx) => (
        <div>
          <Typography component="div" color={validate(label) ? "" : "error"}>{label}</Typography>
          { !validate(label) &&
            <Typography component="div" color="error" variant="caption">
              { validationErrorText }
            </Typography>
          }
        </div>
      )
    : undefined
  );

  return (
    <Question
      disableInstructions
      defaultDisplayFormatter={displayFormatter}
      {...props}
      >
      <MultipleChoice
        input={displayMode === "input" || displayMode === "list+input"}
        textbox={displayMode === "textbox"}
        answerNodeType={answerNodeType}
        validate={validate}
        validationErrorText={validationErrorText}
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
    liveValidation: PropTypes.bool,
  }).isRequired,
  text: PropTypes.string,
  minAnswers: PropTypes.number,
  maxAnswers: PropTypes.number,
  defaults: PropTypes.array,
};

const StyledTextQuestion = withStyles(QuestionnaireStyle)(TextQuestion)
export default StyledTextQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  return [StyledTextQuestion, 0];
});
