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
import { withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";
import NCRNote from "./NCRNote";
import VocabularySelector from "../vocabSelector/select.jsx";

// Component that renders a vocabulary question.
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Required arguments:
//  text: String containing the question to ask
//  sourceVocabulary: String denoting the vocabulary source
//
// Optional arguments:
//  maxAnswers: Integer indicating the maximum number of terms allowed
//
// Other arguments are passed to the VocabularySelector component
//
// Sample usage:
//
// <VocabularyQuestion
//   text="Does the patient have any co-morbidities (non cancerous), including abnormal incidental imaging/labs?"
//   sourceVocabulary="hpo"
//   />
// <VocabularyQuestion
//   text="Does the patient have any skin conditions?"
//   sourceVocabulary="hpo"
//   vocabularyFilter={["HP:0000951"]}
//   />
// <!-- Alternate method of specifying arguments -->
// <VocabularyQuestion
//   questionDefintion={{
//     text: "Does the patient have any skin conditions?",
//     sourceVocabulary: "hpo",
//     vocabularyFilter: ["HP:0000951"]
//   }}
//   />
function VocabularyQuestion(props) {
  let { classes, ...rest } = props;
  let { enableNotes, maxAnswers, sourceVocabulary, vocabularyFilter } = { ...props.questionDefinition, ...props };
  let defaultSuggestions = props.defaults || Object.values(props.questionDefinition)
    // Keep only answer options
    // FIXME Must deal with nested options, do this recursively
    .filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption')
    // Only extract the labels and internal values from the node
    .map(value => [value.label || value.value, value.value, true])
    // Reparse defaults into a format VocabularySelector understands
    .reduce((object, value) => ({...object, [value[0]]: value[1]}), {});

  return (
    <Question
      {...rest}
      >
      <VocabularySelector
        vocabularyFilter = {vocabularyFilter}
        max = {maxAnswers}
        defaultSuggestions = {defaultSuggestions}
        source = {sourceVocabulary}
        {...rest}
      />
    </Question>);
}

VocabularyQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    sourceVocabulary: PropTypes.string.isRequired
  }).isRequired,
  text: PropTypes.string
};


const StyledVocabularyQuestion = withStyles(QuestionnaireStyle)(VocabularyQuestion)
export default StyledVocabularyQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "vocabulary") {
    return [StyledVocabularyQuestion, 50];
  }
});
