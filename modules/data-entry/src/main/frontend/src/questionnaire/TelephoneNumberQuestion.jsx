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
import withStyles from '@mui/styles/withStyles';
import 'react-phone-input-2/lib/style.css';
import PropTypes from "prop-types";

import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";
import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";
import PhoneInput from 'react-phone-input-2'

// Component that renders a vocabulary question.
//
// Sample usage:
//
// <TelephoneNumberQuestion
//   questionDefinition={{
//     text: "Please enter the phone number",
//   }}
//   />
function TelephoneNumberQuestion(props) {
  const { existingAnswer, classes, pageActive, questionDefinition, ...rest} = props;

  let currentStartValue = existingAnswer && existingAnswer[1].value || "";
  const [phone, changePhone] = useState(currentStartValue);

  let outputAnswers = [["value", phone]];
  return (
    <Question
      disableInstructions
      {...props}
      >
      <PhoneInput
        country='ca'
        onlyCountries={['ca', 'us']}
        regions={['north-america']}
        placeholder=""
        value={phone}
        onChange={phone => changePhone(phone)}
      />
      <Answer
        answers={outputAnswers}
        questionDefinition={props.questionDefinition}
        answerNodeType="cards:PhoneAnswer"
        valueType="String"
        existingAnswer={existingAnswer}
        pageActive={pageActive}
        {...rest}
        />
    </Question>);
}

TelephoneNumberQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
  }).isRequired,
  text: PropTypes.string
};


const StyledTelephoneNumberQuestion = withStyles(QuestionnaireStyle)(TelephoneNumberQuestion)
export default StyledTelephoneNumberQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "phone") {
    return [StyledTelephoneNumberQuestion, 50];
  }
});
