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

import { checkPropTypes } from "../propTypes";
import AnswerComponentManager from "./AnswerComponentManager";
import NumberQuestion from "./NumberQuestion";
import DateTimeUtilities from "../components/DateTimeUtilities";

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
//  lowerLimit="2019"
//  upperLimit="today"
//  type="timestamp"
//  />
function DateQuestionYear(props) {
  checkPropTypes(DateQuestionYear, props);
  let { existingAnswer, ...rest } = props;
  let {
    minAnswers = 0,
    type = DateTimeUtilities.TIMESTAMP_TYPE,
    lowerLimit,
    upperLimit
  } = { ...props.questionDefinition, ...props };

  return (
    <NumberQuestion
      minAnswers={minAnswers}
      maxAnswers={1}
      dataType="long"
      isRange={(type === DateTimeUtilities.INTERVAL_TYPE)}
      answerNodeType="cards:DateAnswer"
      valueType="Long"
      existingAnswer={existingAnswer}
      maxValue={+DateTimeUtilities.processRelativeDate(upperLimit, true, DateTimeUtilities.yearTag) || 9999}
      minValue={+DateTimeUtilities.processRelativeDate(lowerLimit, false, DateTimeUtilities.yearTag) || 1000}
      disableValueInstructions={typeof(upperLimit) == 'undefined' && typeof(lowerLimit) == 'undefined'}
      {...rest}
    />
  );
}

DateQuestionYear.propTypes = DateTimeUtilities.PROP_TYPES;

export default DateQuestionYear;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date"
    && DateTimeUtilities.getDateType(questionDefinition.dateFormat) === DateTimeUtilities.YEAR_DATE_TYPE)
  {
    return [DateQuestionYear, 60];
  }
});
