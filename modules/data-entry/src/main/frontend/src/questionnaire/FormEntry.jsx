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

import { Grid } from "@material-ui/core";

import AnswerComponentManager from "./AnswerComponentManager";
import Section from "./Section";

// FIXME In order for the questions to be registered, they need to be loaded, and the only way to do that at the moment is to explicitly invoke them here. Find a way to automatically load all question types, possibly using self-declaration in a node, like the assets, or even by filtering through assets.

import BooleanQuestion from "./BooleanQuestion";
import DateQuestion from "./DateQuestion";
import NumberQuestion from "./NumberQuestion";
import PedigreeQuestion from "./PedigreeQuestion";
import TextQuestion from "./TextQuestion";
import VocabularyQuestion from "./VocabularyQuestion";

const QUESTION_TYPES = ["lfs:Question"];
const SECTION_TYPES = ["lfs:Section", "lfs:SectionLink"];
export const ENTRY_TYPES = QUESTION_TYPES.concat(SECTION_TYPES);

/**
 * Method responsible for displaying a question from the questionnaire, along with its answer(s).
 *
 * @param {Object} questionDefinition the question definition JSON
 * @param {string} path the path to the parent of the question
 * @param {Object} existingAnswer form data that may include answers already submitted for this component
 * @param {string} key the node name of the question definition JCR node
 * @returns a React component that renders the question
 */
let displayQuestion = (questionDefinition, path, existingAnswer, key) => {
  const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
    .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
      && value["question"]["jcr:uuid"] === questionDefinition["jcr:uuid"]);
  // This variable must start with an upper case letter so that React treats it as a component
  const QuestionDisplay = AnswerComponentManager.getAnswerComponent(questionDefinition);
  return (
    <Grid item key={key} className={"questionContainer"}>
      <QuestionDisplay
        key={key}
        questionDefinition={questionDefinition}
        existingAnswer={existingQuestionAnswer}
        path={path} />
    </Grid>
  );
};

/**
 * Method responsible for displaying a section from the questionnaire, along with its answer(s).
 * TODO: Somehow pass the conditional state upwards from here
 *
 * @param {Object} sectionDefinition the section definition JSON
 * @param {string} path the path to the parent of the section
 * @param {int} depth the section nesting depth
 * @param {Object} existingAnswer form data that may include answers already submitted for this component
 * @param {string} key the node name of the section definition JCR node
 * @returns a React component that renders the section
 */
let displaySection = (sectionDefinition, path, depth, existingAnswer, key) => {
  // Parse through SectionLink nodes to find our Section node
  // TODO: What if a sectionRef loop is present?
  while (sectionDefinition && sectionDefinition["sling:resourceType"] == "sectionLink") {
    sectionDefinition = sectionDefinition["ref"];
  }
  const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
    .find(([key, value]) => value["sling:resourceType"] == "lfs/AnswerSection"
      && value["question"]["jcr:uuid"] === sectionDefinition["jcr:uuid"]);
  return (
    <Section
      key={key}
      depth={depth}
      sectionDefinition={sectionDefinition}
      existingAnswer={existingQuestionAnswer}
      path={path}
      />
  );
}

export default function FormEntry(questionDefinition, path, depth, existingAnswers, key) {
  // TODO: As before, I'm writing something that's basically an if statement
  // this should instead be via a componentManager
  if (QUESTION_TYPES.includes(questionDefinition["jcr:primaryType"])) {
      return displayQuestion(questionDefinition, path, existingAnswers, key);
  } else if (SECTION_TYPES.includes(questionDefinition["jcr:primaryType"])) {
      return displaySection(questionDefinition, path, depth, existingAnswers, key);
  }
}
