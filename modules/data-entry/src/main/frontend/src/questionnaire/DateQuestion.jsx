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
  "MM-yyyy"
]

export const DATE_FORMATS = [
  "yyyy",
  "yyyy-MM-dd",
  "MM-dd-yyyy"
].concat(MONTH_FORMATS);

export const DATETIME_FORMATS = [
  "yyyy-MM-dd HH:mm",
  "yyyy-MM-dd HH:mm:ss"
]

const TIMESTAMP_TYPE = "timestamp";
const INTERVAL_TYPE = "interval";

const ALLOWABLE_DATETIME_FORMATS = DATE_FORMATS.concat(DATETIME_FORMATS)

// Truncates fields in the given moment object or date string
// according to the given format string
function amendMoment(date, format) {
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
  for (let [formatSpecifier, targetPrecision] of Object.entries(truncate)) {
    if (format.indexOf(formatSpecifier) < 0) {
      truncateTo = targetPrecision;
    }
  }

  return(new_date.startOf(truncateTo));
}

// Component that renders a date/time question
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
// text: the question to be displayed
// type: "timestamp" for a single date or "interval" for two dates
// dateFormat: yyyy, yyyy-MM, yyyy-MM-dd, yyyy-MM-dd hh:mm, yyyy-MM-dd hh:mm:ss
// displayFormat (defaults to dateFormat)
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
  let {existingAnswer, displayFormat, classes, ...rest} = props;
  let {text, dateFormat, minAnswers, type, lowerLimit, upperLimit} = {dateFormat: "yyyy-MM-dd", minAnswers: 0, type: TIMESTAMP_TYPE, ...props.questionDefinition, ...props};
  let currentStartValue = existingAnswer && existingAnswer[1].value && Array.of(existingAnswer[1].value).flat()[0].split("T")[0] || null;

  const isMonth = MONTH_FORMATS.includes(dateFormat);
  const isDate = DATE_FORMATS.includes(dateFormat);

  const [selectedDate, changeDate] = useState(amendMoment(moment(currentStartValue), dateFormat));
  // FIXME There's no way to store the end date currently. Maybe add existingAnswer[1].endValue?
  const [selectedEndDate, changeEndDate] = useState(amendMoment(moment(), dateFormat));
  const [error, setError] = useState(false);
  const [errorMessage, setErrorMessage] = useState("Invalid date");
  const [monthDateString, setMonthDateString] = useState("");
  const [endMonthDateString, setEndMonthDateString] = useState("");
  const upperLimitMoment = amendMoment(moment(upperLimit), dateFormat);
  const lowerLimitMoment = amendMoment(moment(lowerLimit), dateFormat);
  const monthRegExp = "1[0-2]|0?[1-9]";
  const strictMonthRegExp = "1[0-2]|0[1-9]";
  const yearRegExp = "\\d{4}";
  let inputRegExp = new RegExp();
  let strictInputRegExp = new RegExp();

  // const monthYearTest = new RegExp(/\d{4}\/(0[1-9]|10|11|12)/);

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

  if (isMonth) {
    // Create a user friendly displayFormat and a sling-compatible dateFormat
    if (typeof displayFormat === "undefined") {
      displayFormat = dateFormat.replace('-', '/');
    }
    dateFormat = DATE_FORMATS[1];
    // Create a RegExp to test for the display format
    let regExpString = `^${displayFormat.toLowerCase().replace(/[.*+\-?^${}()|[\]\\]/g, '\\$&')}$`;
    inputRegExp = new RegExp(regExpString.replace("yyyy", `(${yearRegExp})`).replace("mm", `(${monthRegExp})`));
    strictInputRegExp = new RegExp(regExpString.replace("yyyy", `(${yearRegExp})`).replace("mm", `(${strictMonthRegExp})`));
  } else if (typeof displayFormat === "undefined") {
    // The default value of displayFormat for a non-month date, if not given, is dateFormat's value
    displayFormat = dateFormat;
  }

  // Check that the given date is within the upper/lower limit (if given)
  let boundDate = (date) => {
    if (upperLimit && upperLimitMoment < date) {
      date = upperLimitMoment;
    }

    if (lowerLimit && lowerLimitMoment >= date) {
      date = lowerLimitMoment;
    }
    return(date);
  }

  // Check that the given date is less than the upper limit, but also
  // greater than the start date
  let boundEndDate = (date, startDate) => {
    date = boundDate(date);

    if (date < startDate) {
      date = startDate;
    }
    return(date);
  }

  let momentToString = (date) => {
    return !date.isValid() ? "" :
    isMonth ? date.format(moment.HTML5_FMT.MONTH) :
    isDate ? date.format(moment.HTML5_FMT.DATE) :
    date.format(moment.HTML5_FMT.DATETIME_LOCAL);
  }

  let validateMonthString = (value) => {
    let valid = inputRegExp.test(value)
    if (valid || (minAnswers > 0 && value.length === 0)) {
      setError(false);
    } else {
      let exampleDate = displayFormat.toLowerCase().replace("yyyy", "2000").replace("mm", "01");
      setError(true);
      setErrorMessage(`Please enter a month in the format ${displayFormat.toLowerCase()}, such as ${exampleDate}`);
    }
    return valid;
  }

  let displayMonthToMomentString = (value) => {
    let monthIndex = displayFormat.toLowerCase().indexOf('m');
    if (!strictInputRegExp.test(value)) {
      // Input has a single digit month. Prepend month with 0 to ensure Moment can handle the date
      value = [value.slice(0, monthIndex), "0", value.slice(monthIndex)].join('');
    }

    // Make sure month and year are ordered correctly for parsing via Moment
    if (monthIndex === 0) {
      // Moment requires year before month.
      let yearIndex = displayFormat.toLowerCase().indexOf('y');
      value = [value.slice(yearIndex, yearIndex + 4), '-', value.slice(0, 2)].join('');
    }
    return value.replace('/', '-') + '-01'
  }

  let momentStringToDisplayMonth = (value) => {
    value = value.replace('-','/');

    // Switch month and year if required as Moment returns a fixed order
    let monthIndex = displayFormat.toLowerCase().indexOf('m');
    if (monthIndex === 0) {
      // Switch back from moment supported yyyy/mm to desired mm/yyyy.
      value = [value.slice(5, 7), '/', value.slice(0, 4)].join('');
    } else if (value.length > 7) {
      // Cut off any text beyond "yyyy/mm"
      value = value.substring(0, 7);
    }
    return value;
  }

  // Determine the granularity of the input textfield
  const textFieldType = isMonth ? "text" :
    isDate ? "date" :
    "datetime-local";

  if (isMonth && monthDateString == "" && existingAnswer) {
    setMonthDateString(momentStringToDisplayMonth(currentStartValue));
  }

  // Determine how to display the currently selected value
  const outputDate = amendMoment(selectedDate, displayFormat);
  let outputDateString = momentToString(outputDate);
  const outputEndDate = amendMoment(selectedEndDate, displayFormat);
  let outputEndDateString = momentToString(outputEndDate);
  if (isMonth) {
    outputDateString = monthDateString;
    outputEndDateString = endMonthDateString;
  }
  let outputAnswers = [["date", selectedDate.isValid() ? selectedDate.formatWithJDF(dateFormat) : '']];
  if (type === INTERVAL_TYPE) {
    outputAnswers.push(["endDate", selectedEndDate.isValid() ? selectedEndDate.formatWithJDF(dateFormat) : ''])
  }

  return (
    <Question
      text={text}
      {...rest}
      >
      {error && <Typography color='error'>{errorMessage}</Typography>}
      <TextField
        type={textFieldType}
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
        onChange={
          (event) => {
            let value = event.target.value;
            if (isMonth) {
              setMonthDateString(value);
            } else {
              let parsedDate = boundDate(amendMoment(value, dateFormat));
              changeDate(parsedDate);

              // Also fix the end date if it is earlier than the given start date
              type === INTERVAL_TYPE && changeEndDate(boundEndDate(selectedEndDate, parsedDate));
            }
          }
        }
        onBlur={ () => {
          if (isMonth) {
            if (validateMonthString(monthDateString)) {
              let parsedDate = boundDate(amendMoment(displayMonthToMomentString(monthDateString), dateFormat));
              changeDate(parsedDate);
              setMonthDateString(momentStringToDisplayMonth(momentToString(parsedDate)));

              // Also fix the end date if it is earlier than the given start date
              let parsedEndDate = boundEndDate(selectedEndDate, parsedDate);
              if (type === INTERVAL_TYPE) {
                changeEndDate(parsedEndDate);
                setEndMonthDateString(momentStringToDisplayMonth(momentToString(parsedEndDate)));
              }
            }
          }
        }}
        placeholder={displayFormat.toLowerCase()}
        value={outputDateString}
      />
      { /* If this is an interval, allow the user to select a second date */
      type === INTERVAL_TYPE &&
      <React.Fragment>
        <span className={classes.mdash}>&mdash;</span>
        <TextField
          type={textFieldType}
          className={classes.textField}
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
          onChange={
            (event) => {
              let value = event.target.value;
              if (isMonth) {
                setEndMonthDateString(value);
              } else {
                let parsedDate = amendMoment(value, dateFormat);
                changeEndDate(boundEndDate(parsedDate, selectedDate));
              }
            }
          }
          onBlur={ () => {
            if (isMonth) {
              if (validateMonthString(monthDateString)) {
                let parsedDate = boundEndDate(amendMoment(displayMonthToMomentString(monthDateString), dateFormat), selectedDate);
                changeEndDate(parsedDate);
                setEndMonthDateString(momentStringToDisplayMonth(momentToString(parsedDate)));
              }
            }
          }}
          placeholder={displayFormat.toLowerCase()}
          value={outputEndDateString}
        />
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
  displayFormat: PropTypes.oneOf(ALLOWABLE_DATETIME_FORMATS),
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
