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
  "yyyy-MM-dd hh",
  "yyyy-MM-dd hh:mm",
  "yyyy-MM-dd hh:mm:ss"
]

const ALLOWABLE_DATETIME_FORMATS = DATE_FORMATS.concat(DATETIME_FORMATS)

// Truncates fields in the given moment object or date string
// according to the given format string
function amendMoment(date, format) {
  let new_date = date;
  if (typeof new_date === "string") {
    new_date = moment(new_date);
  }

  if (format.search("ss") < 0) {
    new_date.set("s", 0);
  }
  if (format.search("mm") < 0) {
    new_date.set("m", 0);
  }
  if (format.search("hh") < 0) {
    new_date.set("h", 0);
  }
  if (format.search("dd") < 0) {
    // Days are 1-indexed in moment
    new_date.set("D", 1);
  }
  if (format.search("MM") < 0) {
    new_date.set("M", 0);
  }

  return(new_date);
}

// Component that renders a date/time question
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
//  Widget configuration:
// name: the name attribute of the input
// type: single timestamp | interval
// precision: yyyy, yyyy-MM, yyyy-MM-dd, yyyy-MM-dd hh,  yyyy-MM-dd hh:mm, yyyy-MM-dd hh:mm:ss
// displayFormat (defaults to precision)
// lowerLimit
// upperLimit
// Note: date answer types will not have predefined options
// Displayed as inputs with calendar dropdowns. If the type is 'interval', the second date-time must be greater or equal to the first.
//
// Sample usage:
// <NumberQuestion
//    name="Please enter the patient's age"
//    defaults={[
//      {"id": "<18", "label": "<18"}
//    ]}
//    max={1}
//    minValue={18}
//    type="integer"
//    errorText="Please enter an age above 18, or select the <18 option"
//    />
function DateQuestion(props) {
  let {defaults, name, type, precision, displayFormat, lowerLimit, upperLimit, classes, ...rest} = props;
  const [selectedDate, changeDate] = useState(amendMoment(moment(), precision));
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
        errorText="Please insert a valid date."
        />
    );
  }

  // The default value of displayFormat, if not given, is precision's value
  if (typeof displayFormat === "undefined") {
    displayFormat = precision;
  }

  // Type-check and change the selected date
  let checkAndSelectDate = (date) => {
    if (upperLimit && upperLimitMoment < date) {
      date = upperLimitMoment;
    }

    if (lowerLimit && lowerLimitMoment > date) {
      date = lowerLimitMoment;
    }
    changeDate(date);
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

  return (
    <Question
      text={name}
      >
      {error && <Typography color='error'>{errorText}</Typography>}
      <Answer
        answers={[["date", selectedDate.toString()]]}
        />
      <TextField
        id="date"
        type={textFieldType}
        className={classes.textField + " " + classes.answerPadding}
        InputLabelProps={{
          shrink: true,
          max: upperLimit,
          min: lowerLimit
        }}
        onChange={
          (value) => {
            // Year inputs should preappend a dummy month/day so as to work with moment.js
            checkAndSelectDate(amendMoment(event.target.value, precision));}
        }
        value={outputDateString}
      />
      { /* If this is an interval, allow the user to select a second date*/
      type === INTERVAL_TYPE &&
      <React.Fragment>
        <span className={classes.mdash}>&mdash;</span>
        <TextField
          id="date"
          type={textFieldType}
          className={classes.textField}
          InputLabelProps={{
            shrink: true,
            max: upperLimit,
            min: lowerLimit
          }}
          onChange={
            (value) => {
              changeEndDate(boundEndDate(amendMoment(event.target.value, precision), selectedDate));
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
  precision: PropTypes.oneOf(ALLOWABLE_DATETIME_FORMATS).isRequired,
  displayFormat: PropTypes.oneOf(ALLOWABLE_DATETIME_FORMATS),
  type: PropTypes.oneOf(["timestamp", "interval"]).isRequired,
  lowerLimit: PropTypes.object,
  upperLimit: PropTypes.object
};

DateQuestion.defaultProps = {
  errorText: "Invalid input",
  type: 'float'
};

export default withStyles(QuestionnaireStyle)(DateQuestion);
