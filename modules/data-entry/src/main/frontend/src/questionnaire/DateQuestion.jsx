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

import { TextField } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";
import DateTimeUtilities from "./DateTimeUtilities";

import { AdapterLuxon } from "@mui/x-date-pickers/AdapterLuxon";
import { LocalizationProvider } from '@mui/x-date-pickers';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';

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
  let {dateFormat, type, lowerLimit, upperLimit} = {dateFormat: DateTimeUtilities.defaultDateFormat, type: DateTimeUtilities.TIMESTAMP_TYPE, ...props.questionDefinition, ...props};

  const existingValues = existingAnswer && existingAnswer[1].value || "";
  const upperLimitLuxon = DateTimeUtilities.toPrecision(upperLimit);
  const lowerLimitLuxon = DateTimeUtilities.toPrecision(lowerLimit);

  const [error, setError] = useState(false);
  const [errorMessage, setErrorMessage] = useState("Invalid date");

  const views = DateTimeUtilities.getPickerViews(dateFormat);

  const [ displayedDate, setDisplayedDate ] = useState(DateTimeUtilities.toPrecision(
    DateTimeUtilities.stripTimeZone(typeof(existingValues) === "object" ? existingValues[0] : existingValues)));
  const [ displayedEndDate, setDisplayedEndDate ] = useState(DateTimeUtilities.toPrecision(
    DateTimeUtilities.stripTimeZone(typeof(existingValues) === "object" ? existingValues[1] : "")));
  const isRange = (type === DateTimeUtilities.INTERVAL_TYPE);
  const hasTime = DateTimeUtilities.formatHasTime(dateFormat);
  const PickerComponent = hasTime ? DateTimePicker : DatePicker;

  useEffect(() => {
    // Determine if the end date is earlier than the start date
    if (displayedDate && displayedEndDate && displayedEndDate < displayedDate) {
      setError(true);
      setErrorMessage("Invalid date range");
    }
  }, [displayedDate, displayedEndDate]);

  let setDate = (value, isEnd) => {
    setError(false);
    if (isEnd) {
      setDisplayedEndDate(value);
    } else {
      setDisplayedDate(value);
    }
  }

  let getSlingDate = (isEnd) => {
    let date = isEnd ? displayedEndDate : displayedDate;
    if (date) {
      date = date.toFormat(DateTimeUtilities.slingDateFormat) || "";
    }
    return date;
  }

  let outputStart = getSlingDate(false);
  let outputEnd = getSlingDate(true);
  let outputAnswers = outputStart && outputStart !== "Invalid DateTime" && outputStart.length > 0 ? [["date", outputStart]] : [];
  if (isRange && outputEnd && outputEnd !== "Invalid DateTime" && outputEnd.length > 0) {
    outputAnswers.push(["endDate", outputEnd]);
  }

  let getDateField = (isEnd, date) => {
    return (
    <LocalizationProvider dateAdapter={AdapterLuxon}>
      <PickerComponent
        ampm={false}
        views={views}
        inputFormat={dateFormat}
        label={dateFormat.toLowerCase()}
        minDate={lowerLimitLuxon || undefined}
        maxDate={upperLimitLuxon || undefined}
        value={date}
        onChange={(value) => {
          setError(false);
          setDate(value, isEnd);
        }}
        renderInput={ (params) =>
          <TextField
            variant="standard"
            className={classes.textField}
            {...params}
            helperText={error ? errorMessage : null}
            onBlur={(event) => { if (date?.invalid) {
                                   setError(true);
                                   setErrorMessage("Invalid date: "  + date.invalid.explanation);
                                 }
                    }}
            InputProps={{
              ...params.InputProps,
              error: error
            }}
          />
        }
      />
    </LocalizationProvider>);
  }

  let rangeDisplayFormatter = function(label, idx) {
    const initialValue = Array.from(existingAnswer?.[1]?.value || []);
    let limits = initialValue.slice(0, 2);
    if (idx > 0 || limits.length == 0) return '';
    limits[0] = label;
    // In case of invalid data (only one limit of the range is available)
    if (limits.length == 1) {
      limits.push("");
    } else {
      limits[1] = DateTimeUtilities.toPrecision(DateTimeUtilities.stripTimeZone(limits[1]))?.toFormat(dateFormat);
    }
    return limits.join(' - ');
  }

  return (
    <Question
      defaultDisplayFormatter={isRange? rangeDisplayFormatter : undefined}
      compact={isRange}
      currentAnswers={DateTimeUtilities.isAnswerComplete(outputAnswers, type) ? 1 : 0}
      {...props}
      >
      {pageActive &&
        <div className={isRange ? classes.range : ''}>
          {getDateField(false, displayedDate)}
          { /* If this is an interval, allow the user to select a second date */
          isRange &&
          <React.Fragment>
            <span className="separator">&mdash;</span>
            {getDateField(true, displayedEndDate)}
          </React.Fragment>
          }
        </div>
      }
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

DateQuestion.propTypes = DateTimeUtilities.PROP_TYPES;

const StyledDateQuestion = withStyles(QuestionnaireStyle)(DateQuestion);
export default StyledDateQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date") {
    let dateType = DateTimeUtilities.getDateType(questionDefinition.dateFormat);
    if ( [DateTimeUtilities.FULL_DATE_TYPE, DateTimeUtilities.DATETIME_TYPE, DateTimeUtilities.MONTH_DATE_TYPE].includes(dateType)) {
      return [StyledDateQuestion, 60];
    } else {
      // Default date handler
      return [StyledDateQuestion, 50];
    }
  }
});
