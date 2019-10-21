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

import { TextField, Typography, withStyles } from "@material-ui/core";

import PropTypes from "prop-types";
import moment from "moment";
import * as jdfp from "moment-jdateformatparser";

import Answer from "./Answer";
import NumberQuestion from "./NumberQuestion";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

const DATE_FORMATS = [
  "yyyy",
  "yyyy-MM",
  "yyyy-MM-dd"
]

const DATETIME_FORMATS = [
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
    'h':'day',
    'D': 'month',
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
// name: the question to be displayed
// type: "timestamp" for a single date or "interval" for two dates
// precision: yyyy, yyyy-MM, yyyy-MM-dd, yyyy-MM-dd hh,  yyyy-MM-dd hh:mm, yyyy-MM-dd hh:mm:ss
// displayFormat (defaults to precision)
// lowerLimit: lower date limit (inclusive) given as an object or string parsable by moment()
// upperLimit: upper date limit (inclusive) given as an object or string parsable by moment()
// Other options are passed to the <question> widget
//
// Sample usage:
//<DateQuestion
//  name="Please enter a date-time in 2019"
//  precision="yyyy-MM-dd hh:mm:ss"
//  lowerLimit={new Date("01-01-2019")}
//  upperLimit={new Date("12-31-2019")}
//  type="timestamp"
//  />
function DateQuestion(props) {
  let {defaults, name, type, precision, displayFormat, lowerLimit, upperLimit, classes, ...rest} = props;
  const [selectedDate, changeDate] = useState(amendMoment(moment(), precision));
  const [selectedEndDate, changeEndDate] = useState(amendMoment(moment(), precision));
  const [error, setError] = useState(false);
  const upperLimitMoment = moment(upperLimit);
  const lowerLimitMoment = moment(lowerLimit);
  const isMonth = precision === DATE_FORMATS[1];
  const isDate = DATE_FORMATS.includes(precision);

  // If we're given a year, instead supply the NumberQuestion widget
  if (precision === DATE_FORMATS[0]) {
    return (
      <NumberQuestion
        minValue={0}
        name={name}
        max={1}
        userInput="input"
        type="integer"
        errorText="Please insert a valid year range."
        isRange={type === INTERVAL_TYPE}
        {...rest}
        />
    );
  }

  // The default value of displayFormat, if not given, is precision's value
  if (typeof displayFormat === "undefined") {
    displayFormat = precision;
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

  // Determine the granularity of the input textfield
  const textFieldType = isMonth ? "month" :
    isDate ? "date" :
    "datetime-local";

  // Determine how to display the currently selected value
  const outputDate = amendMoment(selectedDate, displayFormat);
  const outputDateString = isMonth ? outputDate.format(moment.HTML5_FMT.MONTH) :
      isDate ? outputDate.format(moment.HTML5_FMT.DATE) :
      outputDate.format(moment.HTML5_FMT.DATETIME_LOCAL);
  const outputEndDate = amendMoment(selectedEndDate, displayFormat);
  const outputEndDateString = isMonth ? outputEndDate.format(moment.HTML5_FMT.MONTH) :
      isDate ? outputEndDate.format(moment.HTML5_FMT.DATE) :
      outputEndDate.format(moment.HTML5_FMT.DATETIME_LOCAL);
  let outputAnswers = [["date", selectedDate.formatWithJDF(precision)]];
  if (type === INTERVAL_TYPE) {
    outputAnswers.push(["endDate", selectedEndDate.formatWithJDF(precision)])
  }

  return (
    <Question
      text={name}
      {...rest}
      >
      {error && <Typography color='error'>{errorText}</Typography>}
      <Answer
        answers={[["date", selectedDate.toString()]]}
        />
      <TextField
        id="date"
        type={textFieldType}
        className={classes.textField + " " + classes.searchWrapper}
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
            let parsedDate = boundDate(amendMoment(event.target.value, precision));
            changeDate(parsedDate);

            // Also fix the end date if it is earlier than the given start date
            type === INTERVAL_TYPE && changeEndDate(boundEndDate(selectedEndDate, parsedDate));
          }
        }
        value={outputDateString}
      />
      { /* If this is an interval, allow the user to select a second date */
      type === INTERVAL_TYPE &&
      <React.Fragment>
        <span className={classes.mdash}>&mdash;</span>
        <TextField
          id="date"
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
              let parsedDate = amendMoment(event.target.value, precision);
              changeEndDate(boundEndDate(parsedDate, selectedDate));
            }
          }
          value={outputEndDateString}
        />
      </React.Fragment>
      }
    </Question>);
}

DateQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  name: PropTypes.string,
  precision: PropTypes.oneOf(ALLOWABLE_DATETIME_FORMATS),
  displayFormat: PropTypes.oneOf(ALLOWABLE_DATETIME_FORMATS),
  type: PropTypes.oneOf([TIMESTAMP_TYPE, INTERVAL_TYPE]).isRequired,
  lowerLimit: PropTypes.object,
  upperLimit: PropTypes.object
};

DateQuestion.defaultProps = {
  errorText: "Invalid input",
  precision: "yyyy-MM-dd",
  type: TIMESTAMP_TYPE
};

export default withStyles(QuestionnaireStyle)(DateQuestion);
