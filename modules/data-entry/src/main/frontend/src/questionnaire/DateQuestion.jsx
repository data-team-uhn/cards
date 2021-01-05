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
import moment from "moment";
import * as jdfp from "moment-jdateformatparser";

import Answer from "./Answer";
import NumberQuestion from "./NumberQuestion";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

export const MONTH_FORMATS = [
  "yyyy-MM",
  "MM-yyyy",
  "yyyy/MM",
  "MM/yyyy"
]

export const DATE_FORMATS = [
  "yyyy",
  "yyyy-MM-DD",
  "yyyy-MM-dd",
  "MM-DD-yyyy",
  "MM-dd-yyyy"
].concat(MONTH_FORMATS);

export const DATETIME_FORMATS = [
  "yyyy-MM-DD HH:mm",
  "yyyy-MM-dd HH:mm",
  "yyyy-MM-DD HH:mm:ss",
  "yyyy-MM-dd HH:mm:ss"
]

const TIMESTAMP_TYPE = "timestamp";
const INTERVAL_TYPE = "interval";
const slingDateFormat = "yyyy-MM-DDTHH:mm:ss";

const ALLOWABLE_DATETIME_FORMATS = DATE_FORMATS.concat(DATETIME_FORMATS)

// Truncates fields in the given moment object or date string
// according to the given format string
export function amendMoment(date, format) {
  let new_date = date;
  if (typeof new_date === "string") {
    new_date = moment(new_date);
  }

  // Determine the coarsest measure to truncate the input to
  const truncate = {
    'S':'second',
    's':'minute',
    'm':'hour',
    'H':'day',
    'd':'month',
    'M':'year'
  };
  let truncateTo;
  // Both 'd' and 'D' should truncate to month
  format = format.replaceAll("D","d");
  for (let [formatSpecifier, targetPrecision] of Object.entries(truncate)) {
    if (format.indexOf(formatSpecifier) < 0) {
      truncateTo = targetPrecision;
    }
  }

  return(new_date.startOf(truncateTo));
}

// Convert a moment string to a month display
function momentStringToDisplayMonth(dateFormat, value) {
  // Switch month and year if required as Moment returns a fixed order
  let monthIndex = dateFormat.indexOf('MM');
  if (monthIndex === 0) {
    let separator = dateFormat[2];
    // Switch back from moment supported yyyy/mm to desired mm/yyyy.
    value = [value.slice(5, 7), separator, value.slice(0, 4)].join('');
  } else if (monthIndex === 5) {
    value = value.replaceAll("-", dateFormat[4]);
  }
  if (value.length > 7) {
    // Cut off any text beyond "yyyy/mm"
    value = value.substring(0, 7);
  }
  return value;
}

// Format a DateAnswer given the given dateFormat
export function formatDateAnswer(dateFormat, value, forDisplay = true) {
  if (!value || value.length === 0) {
    return "";
  }
  if (Array.isArray(value)) {
    return `${formatDateAnswer(dateFormat, value[0], forDisplay)} to ${formatDateAnswer(dateFormat, value[1], forDisplay)}`;
  }
  if (dateFormat === DATE_FORMATS[0]) {
    // Year-only dates are displayed like a number
    return value;
  }
  dateFormat = dateFormat || "yyyy-MM-dd";
  // Quick fix for moment using a different date specifier than Java
  dateFormat = dateFormat.replaceAll('d', "D");
  let date = amendMoment(value, dateFormat);
  let isMonth = MONTH_FORMATS.includes(dateFormat);
  let isDate = DATE_FORMATS.includes(dateFormat);
  if (isMonth) {
    return momentStringToDisplayMonth(
      dateFormat,
      !date.isValid() ? "" :
      date.format(moment.HTML5_FMT.MONTH)
      );
  } else if (forDisplay) {
    return date.format(dateFormat);
  } else {
    let content = isDate ? date.format(moment.HTML5_FMT.DATE) :
      date.format(moment.HTML5_FMT.DATETIME_LOCAL);
    return content;
  }
}

// Component that renders a date/time question
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
// text: the question to be displayed
// type: "timestamp" for a single date or "interval" for two dates
// dateFormat: yyyy, yyyy-MM, yyyy-MM-dd, yyyy-MM-dd hh:mm, yyyy-MM-dd hh:mm:ss
// lowerLimit: lower date limit (inclusive) given as an object or string parsable by moment()
// upperLimit: upper date limit (inclusive) given as an object or string parsable by moment()
// Other options are passed to the <question> widget
//
// Sample usage:
//<DateQuestion
//  text="Please enter a date-time in 2019"
//  dateFormat="yyyy-MM-dd hh:mm:ss"
//  lowerLimit={new Date("01-01-2019")}
//  upperLimit={new Date("12-31-2019")}
//  type="timestamp"
//  />
function DateQuestion(props) {
  let {existingAnswer, classes, ...rest} = props;
  let {text, dateFormat, minAnswers, type, lowerLimit, upperLimit} = {dateFormat: "yyyy-MM-dd", minAnswers: 0, type: TIMESTAMP_TYPE, ...props.questionDefinition, ...props};

  // If we're given a year, instead supply the NumberQuestion widget
  if (dateFormat === DATE_FORMATS[0]) {
    return (
      <NumberQuestion
        minValue={0}
        maxAnswers={1}
        dataType="long"
        errorText="Please insert a valid year range."
        isRange={type === INTERVAL_TYPE}
        answerNodeType="lfs:DateAnswer"
        valueType="Long"
        existingAnswer={existingAnswer}
        {...rest}
        />
    );
  }

  let startValues = existingAnswer && existingAnswer[1].value || "";
  const isMonth = MONTH_FORMATS.includes(dateFormat);
  const isDate = DATE_FORMATS.includes(dateFormat);

  const [ displayedDate, setDisplayedDate ] = useState(formatDateAnswer(
    dateFormat,
    typeof(startValues) === "object" ? startValues[0] : startValues.replace(/-[0-9]{2}:[0-9]{2}$/gm,
    ''), isMonth));
  const [ displayedEndDate, setDisplayedEndDate ] = useState(formatDateAnswer(
    dateFormat,
    typeof(startValues) === "object" ? startValues[1].replace(/-[0-9]{2}:[0-9]{2}$/gm,
    '') : "", isMonth));
  const upperLimitMoment = upperLimit ? amendMoment(moment(upperLimit), slingDateFormat) : null;
  const lowerLimitMoment = lowerLimit ? amendMoment(moment(lowerLimit), slingDateFormat) : null;

  const [error, setError] = useState(false);
  const [errorMessage, setErrorMessage] = useState("Invalid date");

  let inputRegExp = new RegExp();
  let strictInputRegExp = new RegExp();

  if (isMonth) {
    const yearRegExp = "\\d{4}";
    // Create a RegExp to test for the display format
    let regExpString = `^${dateFormat.replaceAll(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`;
    inputRegExp = new RegExp(regExpString.replace("yyyy", `(${yearRegExp})`).replace("MM", `(1[0-2]|0?[1-9])`));
    strictInputRegExp = new RegExp(regExpString.replace("yyyy", `(${yearRegExp})`).replace("MM", `(1[0-2]|0[1-9])`));
  }

  let onChange = (value, isEnd) => {
    if (isMonth) {
      setDate(value, isEnd);
    } else {
      processChange(value, isEnd);
    }
  }

  let onBlur = (value, isEnd) => {
    if (isMonth && validateMonthString(value)) {
      processChange(value, isEnd);
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

  let processChange = (value, isEnd) => {
    let startDate = isEnd ? amendMomentFromString(displayedDate, dateFormat) : null;
    let parsedDate = formatDateAnswer(dateFormat, boundDate(value, startDate), isMonth);
    setDate(value.length === 0 ? value : parsedDate, isEnd);
    if (!isEnd) {
      // Fix the end date if it is earlier than the start date
      type === INTERVAL_TYPE && displayedEndDate.length > 0 && setDate(formatDateAnswer(dateFormat, boundDate(displayedEndDate, parsedDate), isMonth), true);
    }
  }

  // Check that the given date is within the upper/lower limit (if specified),
  // and also after an optional startDate
  let boundDate = (date, startDate = null) => {
    date = amendMomentFromString(date, dateFormat);
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

  let amendMomentFromString = (value, dateFormat) => {
    return amendMoment((isMonth && typeof value === "string") ? displayMonthToMomentString(value) : value, dateFormat);
  }

  let displayMonthToMomentString = (value) => {
    let monthIndex = dateFormat.indexOf('MM');
    if (!strictInputRegExp.test(value)) {
      // Input has a single digit month. Prepend month with 0 to ensure Moment can handle the date
      value = [value.slice(0, monthIndex), "0", value.slice(monthIndex)].join('');
    }

    // Make sure month and year are ordered correctly for parsing via Moment
    if (monthIndex === 0) {
      // Moment requires year before month.
      let yearIndex = dateFormat.indexOf('y');
      value = [value.slice(yearIndex, yearIndex + 4), '-', value.slice(0, 2)].join('');
    }
    return value.replace('/', '-') + '-01'
  }

  let getSlingDate = (isEnd) => {
    let dateString = isEnd ? displayedEndDate : displayedDate;
    if (isMonth && !isValidMonthString(dateString)) {
      dateString = "";
    }
    if (dateString.length > 0) {
      dateString = amendMomentFromString(dateString, dateFormat).formatWithJDF(slingDateFormat);
    }
    return dateString;
  }

  // Determine the granularity of the input textfield
  const textFieldType = isMonth ? "text" :
    isDate ? "date" :
    "datetime-local";

  let outputStart = getSlingDate(false);
  let outputEnd = getSlingDate(true);
  let outputAnswers = outputStart.length > 0 ? [["date", outputStart]] : [];
  if (type === INTERVAL_TYPE && outputEnd.length > 0) {
    outputAnswers.push(["endDate", outputEnd]);
  }

  let getTextField = (isEnd, value) => {
    return (
      <TextField
        type={textFieldType}
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
        onChange={(event) => onChange(event.target.value, isEnd)}
        onBlur={() => onBlur(value, isEnd)}
        placeholder={dateFormat.toLowerCase()}
        // Browser date picker (used when not a month) requires "-" as separators
        value={isMonth ? value : value.replaceAll("/", "-")}
      />
    )
  }

  return (
    <Question
      text={text}
      {...rest}
      >
      {error && <Typography color='error'>{errorMessage}</Typography>}
      {getTextField(false, displayedDate)}
      { /* If this is an interval, allow the user to select a second date */
      type === INTERVAL_TYPE &&
      <React.Fragment>
        <span className={classes.mdash}>&mdash;</span>
        {getTextField(true,displayedEndDate)}
      </React.Fragment>
      }
      <Answer
        answers={outputAnswers}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="lfs:DateAnswer"
        valueType="Date"
        {...rest}
        />
    </Question>);
}

DateQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    dateFormat: PropTypes.oneOf(ALLOWABLE_DATETIME_FORMATS),
    type: PropTypes.oneOf([TIMESTAMP_TYPE, INTERVAL_TYPE]),
    lowerLimit: PropTypes.object,
    upperLimit: PropTypes.object,
  })
};

const StyledDateQuestion = withStyles(QuestionnaireStyle)(DateQuestion);
export default StyledDateQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date") {
    return [StyledDateQuestion, 50];
  }
});
