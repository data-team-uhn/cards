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

import { withStyles } from 'tss-react/mui';

import NumberQuestion from "./NumberQuestion";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";
import DateQuestionUtilities from "./DateQuestionUtilities";

// Component that renders a year only date question
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
// text: the question to be displayed
// type: "timestamp" for a single date or "interval" for two dates
// dateFormat: yyyy
// lowerLimit: lower date limit (inclusive) given as an object or string parsable by luxon
// upperLimit: upper date limit (inclusive) given as an object or string parsable by luxon
// Other options are passed to the <question> widget
//
// Sample usage:
//<DateQuestionYear
//  text="Please enter a date-time in 2019"
//  dateFormat="yyyy-MM-dd HH:mm:ss"
//  lowerLimit="2019"
//  upperLimit="today"
//  type="timestamp"
//  />
function DateQuestionYear(props) {
  let {existingAnswer, classes, ...rest} = props;
  let {text, dateFormat, minAnswers, type, lowerLimit, upperLimit} = {dateFormat: "yyyy", minAnswers: 0, type: DateQuestionUtilities.TIMESTAMP_TYPE, ...props.questionDefinition, ...props};
  return (
    <NumberQuestion
      minAnswers={minAnswers}
      maxAnswers={1}
      dataType="long"
      isRange={(type === DateQuestionUtilities.INTERVAL_TYPE)}
      answerNodeType="cards:DateAnswer"
      valueType="Long"
      existingAnswer={existingAnswer}
      maxValue={+DateQuestionUtilities.processRelativeDate(upperLimit, true, DateQuestionUtilities.yearTag) || 9999}
      minValue={+DateQuestionUtilities.processRelativeDate(lowerLimit, false, DateQuestionUtilities.yearTag) || 1000}
      disableValueInstructions={typeof(upperLimit) == 'undefined' && typeof(lowerLimit) == 'undefined'}
      {...rest}
      />
  );
}

DateQuestionYear.propTypes = DateQuestionUtilities.PROP_TYPES;

const StyledDateQuestionYear = withStyles(DateQuestionYear, QuestionnaireStyle);
export default StyledDateQuestionYear;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date"
    && DateQuestionUtilities.getDateType(questionDefinition.dateFormat) === DateQuestionUtilities.YEAR_DATE_TYPE)
  {
    return [StyledDateQuestionYear, 60];
  }
});
