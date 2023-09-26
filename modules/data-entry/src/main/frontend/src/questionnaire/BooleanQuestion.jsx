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
import withStyles from '@mui/styles/withStyles';

import PropTypes from "prop-types";

import MultipleChoice from "./MultipleChoice";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

// Component that renders a yes/no question, with optional "unknown" option.
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional arguments:
//  text: String containing the question to ask
//  enableUnknown: Boolean denoting whether an unknown option should be allowed
//  yesLabel: String containing the label for 'true'
//  noLabel: String containing the label for 'false'
//  unknownLabel: String containing the label for 'undefined'
//
// Sample usage:
//
// <BooleanQuestion
//   text="Has the patient checked in on time?"
//   />
// <BooleanQuestion
//   text="Has the patient eaten breakfast?"
//   enableUnknown
//   />
// <BooleanQuestion
//   text="This statement is false."
//   enableUnknown
//   yesLabel="True"
//   noLabel="False"
//   unknownLabel="Does not compute"
//   />
function BooleanQuestion(props) {
  const {classes, ...rest} = props;
  const {yesLabel, noLabel, unknownLabel, enableUnknown} = { ...props.questionDefinition, ...props }

  // Define the defaults for yesLabel, etc. here because we want questionDefinition to be able to
  // override them, and the props to be able to override the questionDefinition
  let options = [[yesLabel || "Yes", "1", true], [noLabel || "No", "0", true]];
  if (enableUnknown) {
    options.push([unknownLabel || "Unknown", "-1", true]);
  }

  return (
    <Question
      disableInstructions
      {...props}
      >
      <MultipleChoice
        answerNodeType="cards:BooleanAnswer"
        valueType="Long" /* Notably not "Boolean", since we need it to be stored as a long in the backend */
        maxAnswers={1}
        defaults={options}
        {...rest}
        />
    </Question>);
}

BooleanQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
  }).isRequired,
  text: PropTypes.string,
  enableUnknown: PropTypes.bool,
  yesLabel: PropTypes.string,
  noLabel: PropTypes.string,
  unknownLabel: PropTypes.string
};

const StyledBooleanQuestion = withStyles(QuestionnaireStyle)(BooleanQuestion)
export default StyledBooleanQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "boolean") {
    return [StyledBooleanQuestion, 50];
  }
});
