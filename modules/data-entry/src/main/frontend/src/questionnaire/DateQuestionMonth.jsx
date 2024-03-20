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

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";
import DateQuestionUtilities from "./DateQuestionUtilities";

// Component that renders a date/time question
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
// text: the question to be displayed
// type: "timestamp" for a single date or "interval" for two dates
// dateFormat: A string specifying a date format, including month but not date, as detected by DateQuestionUtilities
// lowerLimit: lower date limit (inclusive) given as an object or string parsable by luxon
// upperLimit: upper date limit (inclusive) given as an object or string parsable by luxon
// Other options are passed to the <question> widget
//
// Sample usage:
//<DateQuestion
//  dateFormat="yyyy-MM"
//  lowerLimit={new Date("01-01-2019")}
//  upperLimit={new Date("12-31-2019")}
//  type="timestamp"
//  />
function DateQuestionMonth(props) {
  let {classes, ...rest} = props;
  let {dateFormat, minAnswers, type, lowerLimit, upperLimit, existingAnswer, pageActive} = {dateFormat: "yyyy/MM", minAnswers: 0, type: DateQuestionUtilities.TIMESTAMP_TYPE, ...props.questionDefinition, ...props};

  let startValues = existingAnswer && existingAnswer[1].value || "";

  const [ displayedDate, setDisplayedDate ] = useState(DateQuestionUtilities.formatDateAnswer(
    dateFormat,
    DateQuestionUtilities.stripTimeZone(typeof(startValues) === "object" ? startValues[0] : startValues)));
  const [ displayedEndDate, setDisplayedEndDate ] = useState(DateQuestionUtilities.formatDateAnswer(
    dateFormat,
    DateQuestionUtilities.stripTimeZone(typeof(startValues) === "object" ? startValues[1] : "")));
  const upperLimitMoment = DateQuestionUtilities.toPrecision(upperLimit);
  const lowerLimitMoment = DateQuestionUtilities.toPrecision(lowerLimit);

  const [error, setError] = useState(false);
  const [errorMessage, setErrorMessage] = useState("Invalid date");

  let inputRegExp = new RegExp();
  let strictInputRegExp = new RegExp();
  const yearRegExp = "\\d{4}";
  // Create a RegExp to test for the display format
  let regExpString = `^${dateFormat.replaceAll(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`;
  // tests for month being in the format `1x` or `0x` or just `x`
  inputRegExp = new RegExp(regExpString.replace("yyyy", `(${yearRegExp})`).replace("MM", `(1[0-2]|0?[1-9])`));
  // tests for month being strictly in the format `1x` or `0x`
  strictInputRegExp = new RegExp(regExpString.replace("yyyy", `(${yearRegExp})`).replace("MM", `(1[0-2]|0[1-9])`));

  let onBlur = (value, isEnd) => {
    if (validateMonthString(value)) {
      let startDate = isEnd ? toPrecisionFromString(displayedDate, dateFormat) : null;
      let parsedDate = DateQuestionUtilities.formatDateAnswer(dateFormat, boundDate(value, startDate));
      setDate(value.length === 0 ? value : parsedDate, isEnd);
      if (!isEnd) {
        // Fix the end date if it is earlier than the start date
        type === DateQuestionUtilities.INTERVAL_TYPE && displayedEndDate.length > 0
          && setDate(DateQuestionUtilities.formatDateAnswer(dateFormat, boundDate(displayedEndDate, parsedDate)), true);
      }
    }
  }

  let setDate = (value, isEnd) => {
    if (isEnd) {
      setDisplayedEndDate(value);
    } else {
      setDisplayedDate(value);
    }
  }

  let validateMonthString = (value) => {
    let valid = isValidMonthString(value);
    if (valid || (minAnswers > 0 && value.length === 0)) {
      setError(false);
    } else {
      let exampleDate = dateFormat.replace("yyyy", "2000").replace("MM", "01");
      setError(true);
      setErrorMessage(`Please enter a month in the format ${dateFormat.toLowerCase()}, such as ${exampleDate}`);
    }
    return valid;
  }

  let isValidMonthString = (value) => {
    return inputRegExp.test(value);
  }

  // Check that the given date is within the upper/lower limit (if specified),
  // and also after an optional startDate
  let boundDate = (date, startDate = null) => {
    date = toPrecisionFromString(date, dateFormat);
    if (upperLimitMoment && upperLimitMoment < date) {
      date = upperLimitMoment;
    }

    if (lowerLimitMoment && lowerLimitMoment >= date) {
      date = lowerLimitMoment;
    }

    if (startDate && startDate >= date) {
      date = startDate;
    }
    return(date);
  }

  let toPrecisionFromString = (value, dateFormat) => {
    return DateQuestionUtilities.toPrecision(typeof value === "string" ? displayMonthToString(value) : value, dateFormat);
  }

  let displayMonthToString = (value) => {
    let monthIndex = dateFormat.indexOf('MM');
    if (!strictInputRegExp.test(value)) {
      // Input has a single digit month. Prepend month with 0 to ensure Luxon can handle the date
      value = [value.slice(0, monthIndex), "0", value.slice(monthIndex)].join('');
    }

    return value.replace('/', '-') + '-01'
  }

  let getSlingDate = (isEnd) => {
    let dateString = isEnd ? displayedEndDate : displayedDate;
    if (!isValidMonthString(dateString)) {
      dateString = "";
    }
    if (dateString.length > 0) {
      dateString = toPrecisionFromString(dateString, dateFormat)?.toFormat(DateQuestionUtilities.slingDateFormat) || "";
    }
    return dateString;
  }

  let outputStart = getSlingDate(false);
  let outputEnd = getSlingDate(true);
  let outputAnswers = outputStart.length > 0 ? [["date", outputStart]] : [];
  if (type === DateQuestionUtilities.INTERVAL_TYPE && outputEnd.length > 0) {
    outputAnswers.push(["endDate", outputEnd]);
  }

  let getTextField = (isEnd, value) => {
    return (
      <TextField
        variant="standard"
        type="text"
        className={classes.textField + isEnd ? "" : (" " + classes.answerField)}
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
        onChange={(event) => setDate(event.target.value, isEnd)}
        onBlur={() => onBlur(value, isEnd)}
        placeholder={dateFormat.toLowerCase()}
        value={value}
      />
    )
  }

  return (
    <Question
      currentAnswers={DateQuestionUtilities.isAnswerComplete(outputAnswers, type) ? 1 : 0}
      {...props}
      >
      {pageActive && <>
        {error && <Typography color='error'>{errorMessage}</Typography>}
        {getTextField(false, displayedDate)}
        { /* If this is an interval, allow the user to select a second date */
        type === DateQuestionUtilities.INTERVAL_TYPE &&
        <React.Fragment>
          <span className={classes.mdash}>&mdash;</span>
          {getTextField(true, displayedEndDate)}
        </React.Fragment>
        }
      </>}
      <Answer
        answers={outputAnswers}
        valueType="Date"
        {...rest}
        />
    </Question>);
}

DateQuestionMonth.propTypes = DateQuestionUtilities.PROP_TYPES;

const StyledDateQuestionMonth = withStyles(QuestionnaireStyle)(DateQuestionMonth);
export default StyledDateQuestionMonth;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date"
    && DateQuestionUtilities.getDateType(questionDefinition.dateFormat) === DateQuestionUtilities.MONTH_DATE_TYPE)
  {
    return [StyledDateQuestionMonth, 60];
  }
});
