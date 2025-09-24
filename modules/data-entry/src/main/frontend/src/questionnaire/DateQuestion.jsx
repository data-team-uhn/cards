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
import { checkPropTypes } from "../propTypes";
import { Typography } from "@mui/material";

import { withStyles } from 'tss-react/mui';

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";
import DateTimeUtilities from "../components/DateTimeUtilities";

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
  checkPropTypes(DateQuestion, props);
  let {existingAnswer, classes, pageActive, ...rest} = props;
  let {dateFormat, type, lowerLimit, upperLimit} = {dateFormat: DateTimeUtilities.defaultDateFormat, type: DateTimeUtilities.TIMESTAMP_TYPE, ...props.questionDefinition, ...props};

  const existingValues = existingAnswer && existingAnswer[1].value || "";
  const upperLimitLuxon = DateTimeUtilities.toPrecision(DateTimeUtilities.processRelativeDate(upperLimit));
  const lowerLimitLuxon = DateTimeUtilities.toPrecision(DateTimeUtilities.processRelativeDate(lowerLimit));

  const [formatError, setFormatError] = useState();
  const [endFormatError, setEndFormatError] = useState();
  const [minMaxError, setMinMaxError] = useState();
  const [rangeError, setRangeError] = useState();

  const views = DateTimeUtilities.getPickerViews(dateFormat);

  const [ displayedDate, setDisplayedDate ] = useState(DateTimeUtilities.toPrecision(
    typeof(existingValues) === "object" ? existingValues[0] : existingValues));
  const [ displayedEndDate, setDisplayedEndDate ] = useState(DateTimeUtilities.toPrecision(
    typeof(existingValues) === "object" ? existingValues[1] : ""));
  const isRange = (type === DateTimeUtilities.INTERVAL_TYPE);
  const hasTime = DateTimeUtilities.formatHasTime(dateFormat);
  const PickerComponent = hasTime ? DateTimePicker : DatePicker;

  const rangeErrorMessage = "Invalid date range: end date should be after the start date";

  useEffect(() => {
    validateInput(null, displayedDate, false);
    isRange && validateInput(null, displayedEndDate, true);
  }, []);

  let setDate = (value, isEnd) => {
    if (isEnd) {
      setDisplayedEndDate(value);
    } else {
      setDisplayedDate(value);
    }
  }

  let cleanErrorMessages = (isEnd) => {
    setMinMaxError(null);
    setRangeError(null);
    if (isEnd) {
      setEndFormatError(null);
    } else {
      setFormatError(null);
    }
  }

  let validateInput = (event, date, isEnd) => {
    if (!date) return;
    if (date?.invalid) {
      let message = "Invalid date";
      if (date.invalid?.explanation) {
        // Picker does not update invalid error explanation until the state is changed
        // need to replace input with current value and question date format
        let explanation = date.invalid?.explanation.replace(/the input "([^"]*)"/, `the input "${event.currentTarget.value}"`);
        explanation = explanation.replace(/as format .*/, `as format ${dateFormat.toLowerCase()}`);
        message = message + (explanation ? ": " + explanation : "");
      }

      if (isEnd) {
        setEndFormatError(message);
      } else {
        setFormatError(message);
      }
    } else {
      // Test that date is within our upperLimit/lowerLimit (if they are defined)
      if ((lowerLimitLuxon && !lowerLimitLuxon.invalid && (isRange ? lowerLimitLuxon : date) < lowerLimitLuxon) &&
          (upperLimitLuxon && !upperLimitLuxon.invalid && (isRange ? upperLimitLuxon : date) > upperLimitLuxon)) {
        setMinMaxError(`Date${isRange ? 's' : ''} must be between ${lowerLimitLuxon.toFormat(dateFormat)} and ${upperLimitLuxon.toFormat(dateFormat)}`);
      }
      if (lowerLimitLuxon && !lowerLimitLuxon.invalid && date < lowerLimitLuxon) {
        setMinMaxError(`Date${isRange ? 's' : ''} must be after ${lowerLimitLuxon.toFormat(dateFormat)}`);
      }
      if (upperLimitLuxon && !upperLimitLuxon.invalid && date > upperLimitLuxon) {
        setMinMaxError(`Date${isRange ? 's' : ''} must be before ${upperLimitLuxon.toFormat(dateFormat)}`);
      }
      // Determine if the end date is earlier than the start date
      if (isRange) {
        let startDateValue = isEnd ? displayedDate : date;
        let endDateValue = isEnd ? date : displayedEndDate;
        if (startDateValue && endDateValue && endDateValue < startDateValue) {
          setRangeError(rangeErrorMessage);
        }
      }
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

  let errorMessage = formatError || minMaxError || rangeError;

  let getDateField = (isEnd, date, formatError) => {
    return (
    <LocalizationProvider dateAdapter={AdapterLuxon}>
      <PickerComponent
        views={views}
        format={dateFormat}
        label={dateFormat.toLowerCase()}
        minDate={lowerLimitLuxon || undefined}
        maxDate={upperLimitLuxon || undefined}
        value={date}
        onChange={(value) => {
          setDate(value, isEnd);
          cleanErrorMessages(isEnd);
          validateInput(null, value, isEnd);
        }}
        slotProps={{ textField: {
                       variant: 'standard',
                       error: formatError || minMaxError || rangeError,
                       className: classes.textField,
                       helperText: formatError || minMaxError || null,
                       onBlur: (event) => validateInput(event, date, isEnd),
                       onFocus: (event) => cleanErrorMessages(isEnd),
                     },
                     field: {
                       clearable: true,
                       onClear: () => setDate(null, isEnd),
                     },
        }}
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
      limits[1] = DateTimeUtilities.toPrecision(limits[1])?.toFormat(dateFormat);
    }
    return dateDisplayFormatter(limits.join(' - '), idx);
  }

  let dateDisplayFormatter = function(label, idx) {
    return (
      <div>
        <Typography component="div" color={pageActive && errorMessage ? "error" : ""}>
          { label }
        </Typography>
        { pageActive && errorMessage &&
          <Typography component="div" color="error" variant="caption">
            { errorMessage }
          </Typography>
        }
      </div>
    );
  }

  let isAnswerComplete = function() {
    return type == DateTimeUtilities.INTERVAL_TYPE && outputAnswers.length == 2 || outputAnswers.length == 1;
  }

  let getAnswerValueInstructions = function() {
    if (lowerLimitLuxon || upperLimitLuxon) {
      let min = lowerLimitLuxon?.toFormat(dateFormat);
      let max = upperLimitLuxon?.toFormat(dateFormat);
      if (min && max) {
        return `Between ${min} and ${max}`;
      } else if (min) {
        return `${min} or later`;
      } else {
        return `Before or on ${max}`;
      }
    }
    return null;
  }

  const instructions = getAnswerValueInstructions();

  return (
    <Question
      defaultDisplayFormatter={isRange? rangeDisplayFormatter : dateDisplayFormatter}
      compact={isRange}
      currentAnswers={isAnswerComplete() ? 1 : 0}
      {...props}
      >
      { pageActive && instructions &&
        <Typography
          component="p"
          color="textSecondary"
          className="cards-answerInstructions"
          variant="caption"
        >
          { instructions }
        </Typography>
      }
      { isRange && rangeError && <Typography
          component="p"
          color="error"
          className={classes.answerInstructions}
          variant="caption"
        >
          { rangeError }
        </Typography>
      }
      { pageActive &&
        <div className={isRange ? classes.range : ''}>
          { getDateField(false, displayedDate, formatError) }
          { /* If this is an interval, allow the user to select a second date */
          isRange &&
          <React.Fragment>
            <span className="separator">&mdash;</span>
            { getDateField(true, displayedEndDate, endFormatError) }
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

const StyledDateQuestion = withStyles(DateQuestion, QuestionnaireStyle);
export default StyledDateQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date") {
    let dateType = DateTimeUtilities.getDateType(questionDefinition.dateFormat);
    if ( [DateTimeUtilities.FULL_DATE_TYPE, DateTimeUtilities.DATETIME_TYPE, DateTimeUtilities.MONTH_DATE_TYPE].includes(dateType)) {
      return [StyledDateQuestion, 70];
    } else {
      // Default date handler
      return [StyledDateQuestion, 50];
    }
  }
});
