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

import { useState } from "react";

import { Typography, withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import MultipleChoice from "./MultipleChoice";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

// Component that renders a multiple choice question, with optional text input
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// arguments:
//  max: Integer denoting maximum number of arguments that may be selected
//  min: Integer denoting minimum number of arguments that may be selected
//  name: String containing the question to ask
//  defaults: Array of objects, each with an "id" representing internal ID
//            and a "value" denoting what will be displayed
//  regexp: String of a regular expression tested against the input
//  errorText: String to display when the regexp is not matched
//  userInput: Either "input", "textbox", or undefined denoting the type of
//             user input. Currently, only "input" is supported
//
// sample usage:
// <TextQuestion
//    name="Test text question (lowercase only)"
//    defaults={[
//      {"id": "1", "label": "1"},
//      {"id": "2", "label": "2"},
//      {"id": "3", "label": "3"}
//    ]}
//    userInput={"input"}
//    regexp={"[a-z]+"}
//    errorText={"Please enter a lowercase input"}
//    />
function TextQuestion(props) {
  let {defaults, max, min, name, userInput, regexp, errorText, ...rest} = props;
  const [error, setError] = useState(false);
  const regexTest = new RegExp(regexp);

  // Callback function if a regex is defined
  let checkRegex = (text) => {
    if (regexp) {
      setError(!regexTest.test(text));
    }
  }

  return (
    <Question
      text={name}
      >
      {error && <Typography color='error'>{errorText}</Typography>}
      <MultipleChoice
        max={max}
        min={min}
        defaults={defaults}
        input={userInput==="input"}
        textbox={userInput==="textbox"}
        onChange={checkRegex}
        {...rest}
        />
    </Question>);
}

TextQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  name: PropTypes.string,
  min: PropTypes.number,
  max: PropTypes.number,
  defaults: PropTypes.array,
  userInput: PropTypes.oneOf([undefined, "input", "textbox"]),
  regexp: PropTypes.string,
  errorText: PropTypes.string
};

TextQuestion.defaultProps = {
  errorText: "Invalid input"
};

export default withStyles(QuestionnaireStyle)(TextQuestion);
