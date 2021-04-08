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
import { withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import { LABEL_POS, VALUE_POS } from "../questionnaire/Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";
import MultipleChoice from "./MultipleChoice";
import VocabularyQuery from "../vocabQuery/query.jsx";

// Component that renders a vocabulary question.
//
// Sample usage:
//
// <VocabularyQuestion
//   questionDefintion={{
//     text: "Does the patient have any skin conditions?",
//     sourceVocabularies={["hpo"]},
//     vocabularyFilter: ["HP:0000951"]
//   }}
//   />
function VocabularyQuestion(props) {
  let { classes, ...rest } = props;
  let { questionDefinition, sourceVocabularies } = { ...props.questionDefinition, ...props };

  return (
    <Question
      {...props}
      >
      <MultipleChoice
        customInput = {VocabularyQuery}
        customInputProps = {{
          vocabularies: sourceVocabularies,
          questionDefinition: questionDefinition
        }}
        answerNodeType = "lfs:VocabularyAnswer"
        {...rest}
        />
    </Question>);
}

VocabularyQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    sourceVocabularies: PropTypes.array.isRequired
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
