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
// dateFormat: A string specifying a date format including date, as detected by DateQuestionUtilities
// lowerLimit: lower date limit (inclusive) given as an object or string parsable by luxon
// upperLimit: upper date limit (inclusive) given as an object or string parsable by luxon
// Other options are passed to the <question> widget
//
// Sample usage:
//<DateQuestion
//  dateFormat="yyyy-MM-dd HH:mm:ss"
//  lowerLimit={new Date("01-01-2019")}
//  upperLimit={new Date("12-31-2019")}
//  type="timestamp"
//  />
function DateQuestionFull(props) {
  let {classes, ...rest} = props;
  let {dateFormat, type, lowerLimit, upperLimit, existingAnswer, pageActive} = {dateFormat: "yyyy-MM-dd", minAnswers: 0, type: DateQuestionUtilities.TIMESTAMP_TYPE, ...props.questionDefinition, ...props};

  let startValues = existingAnswer && existingAnswer[1].value || "";

  const [ startDate, setStartDate ] = useState(DateQuestionUtilities.toPrecision(
    DateQuestionUtilities.stripTimeZone(typeof(startValues) === "object" ? startValues[0] : startValues)
  ));
  const [ endDate, setEndDate ] = useState(
    typeof(startValues) === "object" ? DateQuestionUtilities.toPrecision(DateQuestionUtilities.stripTimeZone(startValues[1])) : null
  );
  const upperLimitMoment = DateQuestionUtilities.toPrecision(upperLimit);
  const lowerLimitMoment = DateQuestionUtilities.toPrecision(lowerLimit);

  let setDate = (value, isEnd) => {
    if (isEnd) {
      setEndDate(value);
    } else {
      setStartDate(value);
    }
  }

  let processChange = (value, isEnd) => {
    setDate(DateQuestionUtilities.toPrecision(value, dateFormat), isEnd);
  }

  let processBlur = (value, isEnd) => {
    let lowerBoundDate = isEnd ? startDate : null;
    let parsedDate = boundDate(value, lowerBoundDate);
    setDate(parsedDate, isEnd);
    if (!isEnd && type === DateQuestionUtilities.INTERVAL_TYPE && endDate) {
      // Fix the end date if it is earlier than the start date
      setDate(boundDate(endDate, parsedDate), true);
    }
  }

  // Check that the given date is within the upper/lower limit (if specified),
  // and also after an optional startDate
  let boundDate = (date, startDate = null) => {
    date = DateQuestionUtilities.toPrecision(date, dateFormat);
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

  let getSlingDate = (isEnd) => {
    let date = isEnd ? endDate : startDate;
    return date ? date.toFormat(DateQuestionUtilities.slingDateFormat) : "";
  }

  // Determine the granularity of the input textfield
  const textFieldType = DateQuestionUtilities.getDateType(dateFormat) === DateQuestionUtilities.DATETIME_TYPE
    ? "datetime-local"
    : "date";

  let outputStart = getSlingDate(false);
  let outputEnd = getSlingDate(true);
  let outputAnswers = outputStart && outputStart !== "Invalid date" ? [["date", outputStart]] : [];
  if (type === DateQuestionUtilities.INTERVAL_TYPE && outputEnd && outputEnd !== "Invalid date") {
    outputAnswers.push(["endDate", outputEnd]);
  }

  let getTextField = (isEnd, value) => {
    return (
      <TextField
        variant="standard"
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
        onChange={(event) => processChange(event.target.value, isEnd)}
        onBlur={(event) => processBlur(event.target.value, isEnd)}
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
      {
        pageActive && <>
          {getTextField(false, DateQuestionUtilities.dateToFormattedString(startDate, textFieldType))}
          { /* If this is an interval, allow the user to select a second date */
          type === DateQuestionUtilities.INTERVAL_TYPE &&
          <React.Fragment>
            <span className={classes.mdash}>&mdash;</span>
            {getTextField(true, DateQuestionUtilities.dateToFormattedString(endDate, textFieldType))}
          </React.Fragment>
          }
        </>
      }
      <Answer
        answers={outputAnswers}
        valueType="Date"
        {...rest}
        />
    </Question>);
}

DateQuestionFull.propTypes = DateQuestionUtilities.PROP_TYPES;

const StyledDateQuestionFull = withStyles(QuestionnaireStyle)(DateQuestionFull);
export default StyledDateQuestionFull;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date") {
    let dateType = DateQuestionUtilities.getDateType(questionDefinition.dateFormat)
    if ( dateType === DateQuestionUtilities.FULL_DATE_TYPE || dateType === DateQuestionUtilities.DATETIME_TYPE) {
      return [StyledDateQuestionFull, 60];
    } else {
      // Default date handler
      return [StyledDateQuestionFull, 50];
    }
  }
});
