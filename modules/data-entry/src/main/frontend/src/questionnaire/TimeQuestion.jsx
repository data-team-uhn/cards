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

import { TextField, Typography, withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

class Time {
  constructor (timeString) {
    if (typeof(timeString) === "string" && timeString.length === 5 && timeString.charAt(2) === ':') {
      let values = timeString.split(":");
      this.hours = parseInt(values[0]);
      this.minutes = parseInt(values[1]);
      this.isValid = this.checkValid();
    } else {
      this.isValid = false;
    }
  }

  checkValid() {
    return typeof(this.hours) === "number"
      && typeof(this.minutes) === "number"
      && this.hours < 24 && this.hours >= 0
      && this.minutes < 60 && this.minutes >= 0
  }

  numberToDoubleDigit(num) {
    num = num.toFixed(0);
    return `${num}`.padStart(2, "0");
  }

  toString() {
    return this.isValid ? `${this.numberToDoubleDigit(this.hours)}:${this.numberToDoubleDigit(this.minutes)}` : ""
  }

  valueOf() {
    return this.isValid ? (this.hours*60 + this.minutes) : undefined;
  }
}

// Component that renders a time question
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
// text: the question to be displayed
// lowerLimit: lower time limit (inclusive) given as a string in the format HH:mm
// upperLimit: upper date limit (inclusive) given as a string in the format HH:mm
// errorText: text to be displayed if the input time is not within the specified range
// Other options are passed to the <question> widget
//
// Sample usage:
//<TimeQuestion
//  text="Please enter a time after noon"
//  lowerLimit={new Date("12:01")}
//  upperLimit={new Date("23:59")}
//  />
function TimeQuestion(props) {
  let {existingAnswer, classes, ...rest} = props;
  let {text, lowerLimit, upperLimit, errorText} = {...props.questionDefinition, ...props};
  let currentStartValue = (existingAnswer && existingAnswer[1].value && new Time(existingAnswer[1].value).isValid)
    ? existingAnswer[1].value : "";
  const [selectedTime, changeTime] = useState(currentStartValue);
  const [error, setError] = useState(false);
  const lowerTime = new Time(lowerLimit);
  const upperTime = new Time(upperLimit);

  if (!errorText) {
    errorText = "Please enter a valid time";
  }

  let checkError = (timeString) => {
    let time = new Time(timeString);
    if ((upperLimit && upperTime < time) || (lowerLimit && lowerTime >= time)) {
      setError(true);
    } else {
      setError(false);
    }
  }

  // Determine how to display the currently selected value
  let outputAnswers = [["time", selectedTime]];

  return (
    <Question
      text={text}
      {...rest}
      >
      {error && <Typography color='error'>{errorText}</Typography>}
      <TextField
        type="time"
        className={classes.textField + " " + classes.answerField}
        InputLabelProps={{
          shrink: true,
        }}
        InputProps={{
          className: classes.textField
        }}
        inputProps={{
          max: upperLimit,
          min: lowerLimit
        }}
        onChange={(event) => {
          checkError(event.target.value);
          changeTime(event.target.value);
        }}

        value={selectedTime}
      />
      <Answer
        answers={outputAnswers}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="lfs:TimeAnswer"
        valueType="Time"
        {...rest}
        />
    </Question>);
}

TimeQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  text: PropTypes.string,
  lowerLimit: PropTypes.string,
  upperLimit: PropTypes.string,
  errorText: PropTypes.string
};

const StyledTimeQuestion = withStyles(QuestionnaireStyle)(TimeQuestion);
export default StyledTimeQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "time") {
    return [StyledTimeQuestion, 50];
  }
});
