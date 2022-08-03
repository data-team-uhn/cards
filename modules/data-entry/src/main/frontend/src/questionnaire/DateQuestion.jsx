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

import React, { useState, useEffect } from "react";

import { Box, TextField, Typography } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import { DateTime } from "luxon";

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";
import DateQuestionUtilities from "./DateQuestionUtilities";

import { AdapterLuxon } from "@mui/x-date-pickers/AdapterLuxon";
import { LocalizationProvider } from '@mui/x-date-pickers';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';

// Component that renders a date/time question
// Selected answers are placed in a series of <input type="hidden"> tags for submission.
//
// Optional props:
// text: the question to be displayed
// type: "timestamp" for a single date or "interval" for two dates
// dateFormat: A string specifying a date format
// lowerLimit: lower date limit (inclusive) given as an object or string parsable by luxon
// upperLimit: upper date limit (inclusive) given as an object or string parsable by luxon
// Other options are passed to the <question> widget
//
// Sample usage:
//<DateQuestion
//  text="Please enter a month date in 2019"
//  dateFormat="yyyy-MM"
//  lowerLimit={"01-01-2019"}
//  upperLimit={"12-31-2019"}
//  type="timestamp"
//  />
// Sample usage 2:
//<DateQuestion
//  text="Please enter a date-time in 2019"
//  dateFormat="yyyy-MM-dd HH:mm:ss"
//  lowerLimit={new Date("01-01-2019")}
//  upperLimit={new Date("12-31-2019")}
//  type="timestamp"
//  />
function DateQuestion(props) {
  let {existingAnswer, classes, pageActive, ...rest} = props;
  let {text, dateFormat, minAnswers, type, lowerLimit, upperLimit} = {dateFormat: DateQuestionUtilities.defaultDateFormat, minAnswers: 0, type: DateQuestionUtilities.TIMESTAMP_TYPE, ...props.questionDefinition, ...props};

  const existingValues = existingAnswer && existingAnswer[1].value || "";
  const upperLimitLuxon = DateQuestionUtilities.toPrecision(upperLimit);
  const lowerLimitLuxon = DateQuestionUtilities.toPrecision(lowerLimit);

  const [error, setError] = useState(false);
  const [errorMessage, setErrorMessage] = useState("Invalid date");

  const dateType = DateQuestionUtilities.getDateType(dateFormat);
  // Determine the granularity of the input textfield
  const textFieldType = DateQuestionUtilities.getFieldType(dateFormat);
  const views = DateQuestionUtilities.getPickerViews(dateFormat);

  const [ displayedDate, setDisplayedDate ] = useState(DateQuestionUtilities.toPrecision(
    DateQuestionUtilities.stripTimeZone(typeof(existingValues) === "object" ? existingValues[0] : existingValues)));
  const [ displayedEndDate, setDisplayedEndDate ] = useState(DateQuestionUtilities.toPrecision(
    DateQuestionUtilities.stripTimeZone(typeof(existingValues) === "object" ? existingValues[1] : "")));

  useEffect(() => {
    // Determine if the end date is earlier than the start date
    if (displayedDate && displayedEndDate && displayedEndDate < displayedDate) {
      setError(true);
      setErrorMessage("Invalid date range");
    }
  }, [displayedDate, displayedEndDate]);

  let processBlur = (value, isEnd) => {
    let lowerBoundDate = isEnd ? startDate : "";
    let parsedDate = boundDate(value, lowerBoundDate);
    setDate(parsedDate, isEnd);
    if (!isEnd && type === DateQuestionUtilities.INTERVAL_TYPE && endDate) {
      // Fix the end date if it is earlier than the start date
      setDate(boundDate(endDate, parsedDate), true);
    }
  }

  let setDate = (value, isEnd) => {
    setError(false);
    if (isEnd) {
      setDisplayedEndDate(value);
    } else {
      setDisplayedDate(value);
    }
  }

  let setRangeDates = (range) => {
    setDisplayedEndDate(range[1]);
    setDisplayedDate(range[0]);
  }

  let getSlingDate = (isEnd) => {
    let date = isEnd ? displayedEndDate : displayedDate;
    if (date) {
      date = date.toFormat(DateQuestionUtilities.slingDateFormat) || "";
    }
    return date;
  }

  let outputStart = getSlingDate(false);
  let outputEnd = getSlingDate(true);
  let outputAnswers = outputStart && outputStart !== "Invalid DateTime" && outputStart.length > 0 ? [["date", outputStart]] : [];
  if (type === DateQuestionUtilities.INTERVAL_TYPE && outputEnd && outputEnd !== "Invalid DateTime" && outputEnd.length > 0) {
    outputAnswers.push(["endDate", outputEnd]);
  }

  let getDateField = (isEnd, date) => {
    return (
    <LocalizationProvider dateAdapter={AdapterLuxon}>
      <DateTimePicker
        ampm={false}
        views={views}
        inputFormat={dateFormat}
        label={dateFormat.toLowerCase()}
        minDate={lowerLimitLuxon || undefined}
        maxDate={upperLimitLuxon || undefined}
        value={date}
        onChange={(value) => setDate(value, isEnd)}
        renderInput={ (params) =>
          <TextField
            variant="standard"
            InputLabelProps={{
              shrink: true,
            }}
            InputProps={{
              className: classes.textField
            }}
            helperText={null}
            {...params}
          />
        }
      />
    </LocalizationProvider>);
  }

  return (
    <Question
      currentAnswers={DateQuestionUtilities.isAnswerComplete(outputAnswers, type) ? 1 : 0}
      {...props}
      >
      {pageActive && <>
        { error && <Typography color='error'>{errorMessage}</Typography> }
        {getDateField(false, displayedDate)}
        { /* If this is an interval, allow the user to select a second date */
        type === DateQuestionUtilities.INTERVAL_TYPE &&
        <React.Fragment>
          <span className={classes.mdash}>&mdash;</span>
          {getDateField(true, displayedEndDate)}
        </React.Fragment>
        }
      </>}
      <Answer
        answers={outputAnswers}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="cards:DateAnswer"
        valueType="Date"
        pageActive={pageActive}
        {...rest}
      />
    </Question>
  );
}

DateQuestion.propTypes = DateQuestionUtilities.PROP_TYPES;

const StyledDateQuestion = withStyles(QuestionnaireStyle)(DateQuestion);
export default StyledDateQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date") {
    let dateType = DateQuestionUtilities.getDateType(questionDefinition.dateFormat);
    if ( [DateQuestionUtilities.FULL_DATE_TYPE, DateQuestionUtilities.DATETIME_TYPE, DateQuestionUtilities.MONTH_DATE_TYPE].includes(dateType)) {
      return [StyledDateQuestion, 60];
    } else {
      // Default date handler
      return [StyledDateQuestion, 50];
    }
  }
});
