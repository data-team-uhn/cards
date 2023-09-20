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
import { Grid } from "@mui/material";

import AnswerComponentManager from "./AnswerComponentManager";
import Section from "./Section";
import Information from "./Information";

// FIXME In order for the questions to be registered, they need to be loaded, and the only way to do that at the moment is to explicitly invoke them here. Find a way to automatically load all question types, possibly using self-declaration in a node, like the assets, or even by filtering through assets.

import BooleanQuestion from "./BooleanQuestion";
import DateQuestionFull from "./DateQuestionFull";
import DateQuestionMonth from "./DateQuestionMonth";
import DateQuestionYear from "./DateQuestionYear";
import NumberQuestion from "./NumberQuestion";
import ChromosomeQuestion from "./ChromosomeQuestion";
import PedigreeQuestion from "./PedigreeQuestion";
import TimeQuestion from "./TimeQuestion";
import TextQuestion from "./TextQuestion";
import ComputedQuestion from "./ComputedQuestion";
import VocabularyQuestion from "./VocabularyQuestion";
import FileQuestion from "./FileQuestion";
import QuestionMatrix from "./QuestionMatrix";
import ResourceQuestion from "./ResourceQuestion";
import DicomQuestion from "./DicomQuestion";
import ReferenceQuestion from "./ReferenceQuestion";
import TelephoneNumberQuestion from "./TelephoneNumberQuestion";

import { hasWarningFlags } from "./FormUtilities";

export const QUESTION_TYPES = ["cards:Question"];
export const SECTION_TYPES = ["cards:Section"];
export const INFO_TYPES = ["cards:Information"];
export const ENTRY_TYPES = QUESTION_TYPES.concat(SECTION_TYPES).concat(INFO_TYPES);

/**
 * Method responsible for displaying a question from the questionnaire, along with its answer(s).
 *
 * @param {Object} questionDefinition the question definition JSON
 * @param {string} path the path to the parent of the question
 * @param {Object} existingAnswer form data that may include answers already submitted for this component
 * @param {string} key the node name of the question definition JCR node
 * @param {Object} classes style classes
 * @returns a React component that renders the question
 */
let displayQuestion = (questionDefinition, path, existingAnswer, key, classes, onAddedAnswerPath, sectionAnswersState, onChange, pageActive, isEdit, isSummary, instanceId) => {
  const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
    .find(([key, value]) => value["sling:resourceSuperType"] == "cards/Answer"
      && value["question"]["jcr:uuid"] === questionDefinition["jcr:uuid"]);

  // View mode should display all mandatory questions whether or not they have an answer
  if (!hasWarningFlags(existingQuestionAnswer) && (!(existingQuestionAnswer?.[1]["displayedValue"] || existingQuestionAnswer?.[1]["note"]) && !isEdit)) {
    return null;
  }

  // This variable must start with an upper case letter so that React treats it as a component
  const QuestionDisplay = AnswerComponentManager.getAnswerComponent(questionDefinition);

  let gridClasses = [];
  let displayMode = questionDefinition.displayMode;
  if (pageActive === false || displayMode == 'hidden' || (isSummary && displayMode !== "summary") || (!isSummary && displayMode === "summary")) {
    gridClasses.push(classes.hiddenQuestion);
  }

  // component will either render the default question display, or a list of questions/answers from the form (used for subjects)
  return (
    <Grid item key={key} className={gridClasses.join(" ")}>
      <QuestionDisplay
        questionDefinition={questionDefinition}
        existingAnswer={existingQuestionAnswer}
        path={path}
        questionName={key}
        onChange={onChange}
        onAddedAnswerPath={onAddedAnswerPath}
        pageActive={pageActive}
        sectionAnswersState={sectionAnswersState}
        isEdit={isEdit}
        instanceId={instanceId || ''}
        />
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
let displaySection = (sectionDefinition, path, depth, existingAnswer, key, onChange, visibleCallback, pageActive, isEdit, isSummary, instanceId, contentOffset) => {
  if (isSummary && sectionDefinition.displayMode !== "summary") {
    return null;
  }

  // Find the existing AnswerSection for this section, if available
  const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
    .filter(([key, value]) => value["sling:resourceType"] == "cards/AnswerSection"
      && value["section"]["jcr:uuid"] === sectionDefinition["jcr:uuid"]);

  return (
    <Section
      key={key}
      depth={depth}
      sectionDefinition={sectionDefinition}
      existingAnswer={existingQuestionAnswer}
      path={path}
      onChange={onChange}
      visibleCallback={visibleCallback}
      pageActive={pageActive}
      isEdit={isEdit}
      isSummary={isSummary}
      instanceId={instanceId || ''}
      contentOffset={contentOffset}
      />
  );
}

let displayInformation = (infoDefinition, key, classes, pageActive, isEdit) => {
  return (
    isEdit && pageActive && infoDefinition.text &&
    <Grid item key={key}>
      <Information infoDefinition={infoDefinition} />
    </Grid>
    || null
  );
}

/**
 * Method responsible for displaying a matrix section from the questionnaire.
 *
 * @param {Object} sectionDefinition the section definition JSON
 * @param {string} path the path to the parent of the question
 * @param {Object} existingAnswer form data that may include answers already submitted for this component
 * @param {string} key the node name of the question definition JCR node
 * @param {Object} classes style classes
 * @returns a React component that renders the matrix section
 */
let displayMatrix = (sectionDefinition, path, existingAnswer, key, classes, pageActive, isEdit) => {
  // Find the existing AnswerSection for this section, if available
  const existingSectionAnswer = existingAnswer && Object.entries(existingAnswer)
    .find(([key, value]) => value["sling:resourceType"] == "cards/AnswerSection"
      && value["section"]["jcr:uuid"] === sectionDefinition["jcr:uuid"]);

  const existingAnswers = existingSectionAnswer && Object.entries(existingSectionAnswer[1])
    .filter(answer => answer[1]["sling:resourceSuperType"]
      && answer[1]["sling:resourceSuperType"] === "cards/Answer");

  // Determine if the section is flagged as incomplete/invalid
  const isFlagged = hasWarningFlags(existingSectionAnswer);
  // View mode should display all mandatory questions whether or not they have an answer
  const hasAnswers = existingAnswers?.filter(answer => answer[1]["displayedValue"]).length > 0;
  if (!isEdit && !isFlagged && !hasAnswers) {
      // Do not show anything if not mandatory, in view mode and no value is recorded yet
      return null;
  }

  let gridClasses = [];
  if (pageActive === false || sectionDefinition.displayMode == 'hidden') {
    gridClasses.push(classes.hiddenQuestion);
  }

  return (
    <Grid item key={key} className={gridClasses.join(" ")}>
      <QuestionMatrix
        sectionDefinition={sectionDefinition}
        existingSectionAnswer={existingSectionAnswer}
        existingAnswers={existingAnswers}
        path={path}
        isEdit={isEdit}
        pageActive={pageActive}
      />
    </Grid>
  );
};

/**
 * Display a question or section from the questionnaire, along with its answer(s).
 *
 * @param {Object} entryDefinition the definition for this entry JSON
 * @param {string} path the path to the parent of the entry
 * @param {int} depth the section nesting depth
 * @param {Object} existingAnswers form data that may include answers already submitted for this component
 * @param {string} key the node name of the section definition JCR node
 * @param {Object} classes style classes
 * @returns a React component that renders the section
 */
 export default function FormEntry(props) {
  let { classes, entryDefinition, path, depth, existingAnswers, keyProp, onAddedAnswerPath, sectionAnswersState, onChange, visibleCallback, pageActive, isEdit, isSummary, instanceId, contentOffset} = props;
  // TODO: As before, I'm writing something that's basically an if statement
  // this should instead be via a componentManager
  if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
    if (visibleCallback) visibleCallback(true);
    return displayQuestion(entryDefinition, path, existingAnswers, keyProp, classes, onAddedAnswerPath, sectionAnswersState, onChange, pageActive, isEdit, isSummary, instanceId);
  } else if (SECTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
    if (visibleCallback) visibleCallback(true);
    if ("matrix" === entryDefinition["displayMode"]) {
      return displayMatrix(entryDefinition, path, existingAnswers, keyProp, classes, pageActive, isEdit);
    } else {
      return displaySection(entryDefinition, path, depth, existingAnswers, keyProp, onChange, visibleCallback, pageActive, isEdit, isSummary, instanceId, contentOffset);
    }
  } else if (INFO_TYPES.includes(entryDefinition["jcr:primaryType"])) {
    return displayInformation(entryDefinition, keyProp, classes, pageActive, isEdit);
  }
}
