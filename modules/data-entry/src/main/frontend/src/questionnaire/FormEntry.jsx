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

import React,  { useCallback } from "react";

import { Grid, withStyles, } from "@material-ui/core";

import AnswerComponentManager from "./AnswerComponentManager";
import Section from "./Section";
import QuestionnaireStyle from "./QuestionnaireStyle";

// FIXME In order for the questions to be registered, they need to be loaded, and the only way to do that at the moment is to explicitly invoke them here. Find a way to automatically load all question types, possibly using self-declaration in a node, like the assets, or even by filtering through assets.
import BooleanQuestion from "./BooleanQuestion";
import DateQuestion from "./DateQuestion";
import NumberQuestion from "./NumberQuestion";
import PedigreeQuestion from "./PedigreeQuestion";
import TextQuestion from "./TextQuestion";
import VocabularyQuestion from "./VocabularyQuestion";

const QUESTION_TYPES = ["lfs:Question"];
const SECTION_TYPES = ["lfs:Section"];
export const ENTRY_TYPES = QUESTION_TYPES.concat(SECTION_TYPES);

/**
 * Display a question or section from the questionnaire, along with its answer(s).
 *
 * @param {Object} entryDefinition the definition for this entry JSON
 * @param {string} path the path to the parent of the entry
 * @param {int} depth the section nesting depth
 * @param {Object} existingAnswers form data that may include answers already submitted for this component
 * @param {string} key the node name of the section definition JCR node
 * @returns a React component that renders the section
 */
function FormEntry(props) {
  let { classes, entryDefinition, path, depth, existingAnswers, key } = props;

  if (SECTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
      // Find the existing AnswerSection for this section, if available
    const existingQuestionAnswer = existingAnswers && Object.entries(existingAnswers)
      .filter(([key, value]) => value["sling:resourceType"] == "lfs/AnswerSection"
        && value["section"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);

    return (
      <Section
        key={key}
        depth={depth}
        sectionDefinition={entryDefinition}
        existingAnswer={existingQuestionAnswer}
        path={path}
        />
    );
  }

  const entry = /#(.+)/.exec(location.href);
  const anchor = entry ? entry[1] : '';
  // create a ref to store the question container DOM element
  const questionRef = useCallback(node => {
    if (node !== null) {
      node.scrollIntoView();
    }
  }, []);

  // TODO: As before, I'm writing something that's basically an if statement
  // this should instead be via a componentManager
  if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
    // if autofocus is needed and specified in the url
    
    const questionText = entryDefinition["text"].replace(/\s/g, '').substring(0, 10);
    const doHighlight = (anchor == questionText);

    const existingQuestionAnswer = existingAnswers && Object.entries(existingAnswers)
      .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
        && value["question"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);
    // This variable must start with an upper case letter so that React treats it as a component
    const QuestionDisplay = AnswerComponentManager.getAnswerComponent(entryDefinition);
    return (
      <Grid item key={key} ref={doHighlight ? questionRef : undefined} className={(doHighlight ? classes.highlightedSection : undefined)}>
        <QuestionDisplay
          questionDefinition={entryDefinition}
          existingAnswer={existingQuestionAnswer}
          path={path}
          questionName={key}
          />
      </Grid>
    );
  }
};

export default withStyles(QuestionnaireStyle)(FormEntry);