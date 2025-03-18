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

import PropTypes from "prop-types";

import Question from "./Question";

import AnswerComponentManager from "./AnswerComponentManager";
import MultipleChoice from "./MultipleChoice";
import VocabularyQuery from "../vocabQuery/VocabularyQuery.jsx";

import NCRNote from "./NCRNote.jsx";

// Component that renders a vocabulary question.
//
// Sample usage:
//
// <VocabularyQuestion
//   questionDefinition={{
//     text: "Does the patient have any skin conditions?",
//     sourceVocabularies: ["HP"],
//     vocabularyFilters: {"HP": ["HP:0000951"]}
//   }}
//   />
function VocabularyQuestion(props) {
  PropTypes.checkPropTypes(VocabularyQuestion.propTypes, props, 'prop', 'VocabularyQuestion');
  let { questionDefinition } = props;
  let { maxAnswers } = { ...questionDefinition, ...props };

  // In order to determine indentation levels, we need to see if there are any default suggestions
  // (i.e. children of the definition that are of type cards:AnswerOption)
  let defaults = props.defaults || Object.values(props.questionDefinition)
    .filter(value => value['jcr:primaryType'] == 'cards:AnswerOption');
  let singleInput = maxAnswers === 1;
  let isBare = singleInput && defaults.length > 0;

  return (
    <Question
      disableInstructions
      {...props}
      >
      <MultipleChoice
        customInput = {VocabularyQuery}
        customInputProps = {{
          questionDefinition: questionDefinition,
          focusAfterSelecting: !singleInput,
          isNested: isBare,
          variant: "labeled",
          clearOnClick: !singleInput,
          enableSelection: true
        }}
        answerNodeType = "cards:VocabularyAnswer"
        noteComponent={NCRNote}
        noteProps={{
          vocabulary: questionDefinition.sourceVocabularies
        }}
        {...props}
        />
    </Question>);
}

VocabularyQuestion.propTypes = {
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    sourceVocabularies: PropTypes.array.isRequired
  }).isRequired,
  text: PropTypes.string
};

export default VocabularyQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "vocabulary") {
    return [VocabularyQuestion, 50];
  }
});
