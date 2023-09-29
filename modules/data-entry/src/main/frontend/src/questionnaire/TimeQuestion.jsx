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

import { TextField, Typography } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import PropTypes from "prop-types";

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

export class Time {
  constructor (timeString, isMinuteSeconds = true) {
    if (typeof(timeString) === "string" && timeString.length === 5 && timeString.charAt(2) === ':') {
      let values = timeString.split(":");
      this.isMinuteSeconds = isMinuteSeconds;
      this.first = parseInt(values[0]);
      this.second = parseInt(values[1]);
      this.isValid = this.checkValid();
    } else {
      this.isValid = false;
    }
  }

  checkValid() {
    let firstValid = typeof(this.first) === "number" && this.first >= 0 && this.first < (this.isMinuteSeconds ? 60 : 24)
    let secondValid = typeof(this.second) === "number" && this.second < 60 && this.second >= 0;
    return firstValid && secondValid;
  }

  numberToDoubleDigit(num) {
    num = num.toFixed(0);
    return `${num}`.padStart(2, "0");
  }

  toString() {
    return this.isValid ? `${this.numberToDoubleDigit(this.first)}:${this.numberToDoubleDigit(this.second)}` : ""
  }

  valueOf() {
    return this.isValid ? (this.first*60 + this.second) * (this.isMinuteSeconds ? 1 : 60) : undefined;
  }

  static formatIsMinuteSeconds(dateFormat) {
    return typeof(dateFormat) === "string" && dateFormat.toLowerCase() === "mm:ss";
  }

  static timeQuestionFieldType(dateFormat) {
    return this.formatIsMinuteSeconds(dateFormat) ? "string" : "time";
  }
}

// Component that renders a time question
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
// text: the question to be displayed
// lowerLimit: lower time limit (inclusive) given as a string in the format HH:mm or mm:ss
// upperLimit: upper date limit (inclusive) given as a string in the format HH:mm or mm:ss
// errorText: text to be displayed if the input time is not within the specified range
// Other options are passed to the <question> widget
//
// Sample usage:
//<TimeQuestion
//  lowerLimit={"12:01"}
//  upperLimit={"23:59"}
//  />
function TimeQuestion(props) {
  let {classes, ...rest} = props;
  let {lowerLimit, upperLimit, errorText, minAnswers, dateFormat, existingAnswer, pageActive} = {...props.questionDefinition, ...props};
  let currentStartValue = (existingAnswer && existingAnswer[1].value && new Time(existingAnswer[1].value).isValid)
    ? existingAnswer[1].value : "";
  const [selectedTime, changeTime] = useState(currentStartValue);
  const [error, setError] = useState(undefined);
  const defaultErrorMessage = errorText || "Please enter a valid time";
  const [errorMessage, setErrorMessage] = useState(defaultErrorMessage);
  const isMinuteSeconds = Time.formatIsMinuteSeconds(dateFormat);
  const lowerTime = new Time(lowerLimit, isMinuteSeconds);
  const upperTime = new Time(upperLimit, isMinuteSeconds);
  const minuteSecondTest = new RegExp(/([0-5]\d):([0-5]\d)/);

  let checkError = (timeString) => {
    let time = new Time(timeString, isMinuteSeconds);
    if (isMinuteSeconds && !minuteSecondTest.test(timeString)) {
      setError(true);
      setErrorMessage("Please enter a time in the format mm:ss, such as 01:23");
    } else if ((minAnswers > 0 && !time.isValid) || (upperLimit && upperTime < time) || (lowerLimit && lowerTime > time)) {
      setError(true);
      setErrorMessage(defaultErrorMessage)
    } else {
      setError(false);
    }
  }

  // Error check existing answers when first loading the page
  if (typeof(error) === "undefined" && selectedTime !== "") {
    checkError(selectedTime);
  }

  let outputAnswers = [["time", selectedTime]];
  return (
    <Question
      currentAnswers={!!selectedTime ? 1 : 0}
      {...props}
      >
      {
        pageActive && <>
          {error && <Typography color='error'>{errorMessage}</Typography>}
          <TextField
            variant="standard"
            /* time input is hh:mm or hh:mm:ss only */
            type={Time.timeQuestionFieldType(dateFormat)}
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
        </>
      }
      <Answer
        answers={outputAnswers}
        valueType="Time"
        {...rest}
        />
    </Question>);
}

TimeQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  minAnswers: PropTypes.number,
  lowerLimit: PropTypes.string,
  upperLimit: PropTypes.string,
  errorText: PropTypes.string,
  dateFormat: PropTypes.string
};

const StyledTimeQuestion = withStyles(QuestionnaireStyle)(TimeQuestion);
export default StyledTimeQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "time") {
    return [StyledTimeQuestion, 50];
  }
});
