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

import { TextField } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import PropTypes from "prop-types";

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

import { AdapterLuxon } from '@mui/x-date-pickers/AdapterLuxon';
import { LocalizationProvider } from '@mui/x-date-pickers';
import { TimePicker } from '@mui/x-date-pickers/TimePicker';
import { DateTime } from "luxon";
import DateTimeUtilities from "./DateTimeUtilities";

// Component that renders a time question
// Selected answers are placed in a series of <input type="hidden"> tags for submission.
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
//  text="Please enter a time after noon"
//  lowerLimit={"12:01"}
//  upperLimit={"23:59"}
//  />
function TimeQuestion(props) {
  let {existingAnswer, classes, pageActive, ...rest} = props;
  let {text, lowerLimit, upperLimit, errorText, minAnswers, dateFormat} = {dateFormat: "mm:ss", ...props.questionDefinition, ...props};
  let currentStartValue = (existingAnswer && existingAnswer[1].value && DateTime.fromFormat(existingAnswer[1].value, dateFormat).isValid)
    ? DateTime.fromFormat(existingAnswer[1].value, dateFormat) : null;
  const [selectedTime, changeTime] = useState(currentStartValue);
  const [error, setError] = useState(undefined);
  const defaultErrorMessage = errorText || "Please enter a valid time";
  const [errorMessage, setErrorMessage] = useState(defaultErrorMessage);
  const views = DateTimeUtilities.getPickerViews(dateFormat);
  const isHourMinuteSeconds = DateTimeUtilities.formatIsHourMinuteSeconds(dateFormat);
  const maxTime = upperLimit ? DateTime.fromFormat(upperLimit, dateFormat) : null;
  const minTime = lowerLimit ? DateTime.fromFormat(lowerLimit, dateFormat) : null;

  // Error check existing answers when first loading the page
  if (existingAnswer && existingAnswer[1].value && DateTime.fromFormat(existingAnswer[1].value, dateFormat).invalid) {
    setError(true);
    setErrorMessage(DateTime.fromFormat(existingAnswer[1].value, dateFormat).invalidExplanation);
  }

  let outputAnswers = [["time", selectedTime && selectedTime.isValid ? selectedTime.toFormat(dateFormat) : null]];
  return (
    <Question
      currentAnswers={!!selectedTime?.toFormat(dateFormat) ? 1 : 0}
      {...props}
      >
      {
        pageActive && <>
          <LocalizationProvider dateAdapter={AdapterLuxon}>
            <TimePicker
              ampm={false}
              label={dateFormat}
              views={views}
              inputFormat={dateFormat}
              mask={isHourMinuteSeconds ? "__:__:__" : "__:__"}
              openTo={views.includes('hours') ? "hours" : "minutes"}
              maxTime={maxTime}
              minTime={minTime}
              onChange={(newValue) => {
                setError(false);
                changeTime(newValue);
              }}
              value={selectedTime}
              renderInput={(params) =>
                <TextField
                  variant="standard"
                  className={classes.textField}
                  {...params}
                  helperText={error ? errorMessage : null}
                  onBlur={(event) => { if (selectedTime?.invalid) {
                                          setError(true);
                                          setErrorMessage("Invalid time: "  + selectedTime.invalid.explanation);
                                       }
                         }}
                  InputProps={{
                    ...params.InputProps,
                    error: error
                  }}
                />}
            />
          </LocalizationProvider>
        </>
      }
      <Answer
        answers={outputAnswers}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="cards:TimeAnswer"
        valueType="Time"
        pageActive={pageActive}
        {...rest}
        />
    </Question>);
}

TimeQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  text: PropTypes.string,
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
