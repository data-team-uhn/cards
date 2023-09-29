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

import React from "react";

import withStyles from '@mui/styles/withStyles';

import NumberQuestion from "./NumberQuestion";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";
import DateQuestionUtilities from "./DateQuestionUtilities";

// Component that renders a year only date question
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
// type: "timestamp" for a single date or "interval" for two dates
// lowerLimit: lower date limit (inclusive) given as an object or string parsable by luxon
// upperLimit: upper date limit (inclusive) given as an object or string parsable by luxon
// Other options are passed to the <question> widget
//
// Sample usage:
//<DateQuestionYear
//  lowerLimit={new Date("01-01-2019")}
//  upperLimit={new Date("12-31-2019")}
//  type="timestamp"
//  />
function DateQuestionYear(props) {
  let {classes, questionDefinition, ...rest} = props;
  let {minAnswers, type, lowerLimit, upperLimit} = {minAnswers: 0, type: DateQuestionUtilities.TIMESTAMP_TYPE, ...questionDefinition, ...props};
  return (
    <NumberQuestion
      questionDefinition={{...questionDefinition,
        minAnswers: minAnswers,
        maxAnswers: 1,
        maxValue: upperLimit || 9999,
        minValue: lowerLimit || 1000,
        dataType: "long"
      }}
      errorText="Please insert a valid year."
      isRange={type === DateQuestionUtilities.INTERVAL_TYPE}
      disableValueInstructions={typeof(upperLimit) == 'undefined' && typeof(lowerLimit) == 'undefined'}
      {...rest}
      />
  );
}

DateQuestionYear.propTypes = DateQuestionUtilities.PROP_TYPES;

const StyledDateQuestionYear = withStyles(QuestionnaireStyle)(DateQuestionYear);
export default StyledDateQuestionYear;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date"
    && DateQuestionUtilities.getDateType(questionDefinition.dateFormat) === DateQuestionUtilities.YEAR_DATE_TYPE)
  {
    return [StyledDateQuestionYear, 60];
  }
});
