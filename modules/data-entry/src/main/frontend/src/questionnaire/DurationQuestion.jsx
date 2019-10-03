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

import React, { useState } from 'react';
import { withStyles, Typography } from "@material-ui/core";

import PropTypes from "prop-types";

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

function DurationQuestion(props) {
  let {name, lowerLimit, upperLimit, precision, classes, errorText} = props;
  const [answerDuration, setAnswer] = useState(new Date(0));
  const [error, setError] = useState(false);

  let checkError = (newDate) => {
    if ((typeof lowerLimit !== "undefined" && newDate < lowerLimit)
      || (typeof upperLimit !== "undefined" && newDate > upperLimit)) {
      setError(true);
      return true;
    }
    return false;
  }

  let AlterDuration = (event, func) => {
    let newDate = new Date(answerDuration.getTime());
    newDate[func](parseInt(event.target.value));

    if (checkError(newDate)) {
      return;
    }

    setAnswer(newDate);
  }

  // Convert the prop's precisions into a list of useable settings
  // Use an array instead of an object to preserve ordering
  const precisionMap = [
    ["y", "y", "getYear", "setYear"],
    ["M", "m", "getMonth", "setMonth"],
    ["d", "d", "getDay", "setDay"],
    ["h", "h", "getHours", "setHours"],
    ["m", "min", "getMinutes", "setMinutes"],
    ["s", "s", "getSeconds", "setSeconds"]
  ]

  return (
    <Question text={name}>
      {error && <Typography color="error">{errorText}</Typography>}
      {/* Loop through our precision mapping to render each portion */
      precisionMap.map((mapping) => {
        if (precision.includes(mapping[0])) {
          let displayText = mapping[1];
          let getter = mapping[2];
          let setter = mapping[3];
          return (
            <React.Fragment>
              <input
                type="number"
                className={classes.durationInput}
                min={lowerLimit && lowerLimit[getter]()}
                max={upperLimit && upperLimit[getter]()}
                onChange={(event) => {AlterDuration(event, setter)}}
                />
              {displayText}
            </React.Fragment>
          );
        }
      })}
    </Question>);
}

DurationQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  name: PropTypes.string,
  // Only the applicable y/M/d/h/m/s of the limits are used
  lowerLimit: PropTypes.instanceOf(Date),
  upperLimit: PropTypes.instanceOf(Date),
  precision: PropTypes.arrayOf((propValue, key) => {
      // The input must be a contiguous sublist of y M d h m s
      const allowablePrecisions = ['y', 'M', 'd', 'h', 'm', 's'];
      if (!propValue[key] in allowablePrecisions) {
          return new Error("Unknown precision given to DurationQuestion: " + propValue[key]);
      };
  }).isRequired,
  errorText: PropTypes.string
};

DurationQuestion.defaultProps = {
  errorText: "Invalid input"
}

export default withStyles(QuestionnaireStyle)(DurationQuestion);
