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

import React, { useEffect, useState, useRef } from "react";
import PropTypes from "prop-types";
import { v4 as uuidv4 } from 'uuid';
import { checkPropTypes } from "../propTypes";

import Note from "./Note";
import { useFormWriterContext } from "./FormContext";

export const LABEL_POS = 0;
export const VALUE_POS = 1;
// Position used to read whether or not an option is a "default" suggestion (i.e. one provided by the questionnaire)
export const IS_DEFAULT_OPTION_POS = 2;
export const DESC_POS = 3;
// Position used to read whether or not an answer is a "default" for questions that don’t yet have an existing answer, option is displayed as selected
export const IS_DEFAULT_ANSWER_POS = 4;

// Holds answers and automatically generates hidden inputs
// for form submission
function Answer (props) {
  checkPropTypes(Answer, props);
  let {
    answers,
    answerMetadata,
    answerNodeType = "cards:TextAnswer",
    existingAnswer,
    pageActive = true,
    path,
    questionName,
    questionDefinition,
    valueType = "String",
    isMultivalued = false,
    onChangeNote,
    noteComponent = Note,
    noteProps,
    onAddedAnswerPath,
    onDecidedOutputPath,
    sectionAnswersState
  } = props;

  let { enableNotes } = { ...props, ...questionDefinition };
  let { onAddSuggestion } = { ...props, ...noteProps };
  let [ answerID ] = useState((existingAnswer && existingAnswer[0]) || uuidv4());
  let answerPath = path + "/" + answerID;

  // Track if we've already registered this answer path to avoid infinite loops
  const hasRegisteredPathRef = useRef(false);

  useEffect(() => {
    if (sectionAnswersState !== undefined && !hasRegisteredPathRef.current) {
      let idHistory = [];
      if (questionName in sectionAnswersState) {
        idHistory = sectionAnswersState[questionName];
      }
      if (idHistory.indexOf(answerPath) < 0)
      {
        idHistory.push(answerPath);
        sectionAnswersState[questionName] = idHistory;
        onAddedAnswerPath(sectionAnswersState);
        hasRegisteredPathRef.current = true;
      }
    }
  }, [sectionAnswersState, questionName, answerPath, onAddedAnswerPath]);

  // Update any listeners what our final output path will be
  useEffect(() => {
    onDecidedOutputPath?.(answerPath);
  }, [answerPath]);

  // Hooks must be pulled from the top level, so this cannot be moved to inside the useEffect()
  const changeFormContext = useFormWriterContext();
  // Rename this variable to start with a capital letter so React knows it is a component
  const NoteComponent = noteComponent;

  // Track previous answers to avoid unnecessary context updates
  const prevAnswersRef = useRef();

  // When the answers change, we inform the FormContext
  useEffect(() => {
    // Check if answers actually changed (reference or deep equality)
    const prevAnswers = prevAnswersRef.current;
    const answersChanged = prevAnswers !== answers &&
      (prevAnswers === undefined || JSON.stringify(prevAnswers) !== JSON.stringify(answers));

    if (answersChanged) {
      changeFormContext((oldContext) => {
        // Only update if the value in context is different
        const currentValue = oldContext[questionName];
        if (currentValue === answers) {
          return oldContext; // Return same reference if unchanged
        }
        // Deep comparison to avoid unnecessary updates
        if (JSON.stringify(currentValue) === JSON.stringify(answers)) {
          return oldContext; // Return same reference if values are equal
        }
        return {...oldContext, [questionName]: answers};
      });
      prevAnswersRef.current = answers;
    }
  }, [answers, changeFormContext, questionName]);

  return (
    <React.Fragment>
      <input type="hidden" className="cards-answer-id" value={answerID}></input>
      <input type="hidden" name={`${answerPath}/jcr:primaryType`} value={answerNodeType}></input>
      <input type="hidden" name={`${answerPath}/question`} value={questionDefinition['jcr:uuid']}></input>
      <input type="hidden" name={`${answerPath}/question@TypeHint`} value="Reference"></input>

      {/* Add the answers, if any exist, or otherwise delete them */}
      {answers?.length ?
        (<React.Fragment>
          <input type="hidden" name={`${answerPath}/value@TypeHint`} value={valueType + (isMultivalued ? '[]' : '')}></input>
          {answers.map( (element, index) => {
            return (
              <input type="hidden" name={`${answerPath}/value`} key={element[VALUE_POS] === undefined ? index : element[VALUE_POS] + "" + index} value={element[VALUE_POS] ?? undefined}></input>
              );
          })}
          {
            answerMetadata &&
              Object.entries(answerMetadata).map(([key, value], index) => {
                return (
                  <input
                    type="hidden"
                    name={`${answerPath}/${key}`}
                    key={value === undefined ? index + (answers ? answers.length : 0) : value}
                    value={value ?? undefined}></input>
                );
              })
          }
        </React.Fragment>)
      :
        <>
        <input type="hidden" name={`${answerPath}/value@Delete`} value="0"></input>
        { Object.entries(answerMetadata || {}).map(([key, value], index) => (
            <input
              type="hidden"
              name={`${answerPath}/${key}@Delete`}
              key={value === undefined ? index + (answers ? answers.length : 0) : value}
              value={0}
            />
          ))
        }
        </>
      }
      {enableNotes &&
        <NoteComponent
          existingAnswer={existingAnswer}
          answerPath={answerPath}
          onChangeNote={onChangeNote}
          onAddSuggestion={onAddSuggestion}
          pageActive={pageActive}
          {...noteProps}
          />
      }
    </React.Fragment>
    );
}

Answer.propTypes = {
  answers: PropTypes.array,
  answerNodeType: PropTypes.string,
  valueType: PropTypes.string,
  isMultivalued: PropTypes.bool,
  noteComponent: PropTypes.elementType,
  pageActive: PropTypes.bool
};

export default Answer;
