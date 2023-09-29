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

import React, { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { v4 as uuidv4 } from 'uuid';

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
  let { answers, answerMetadata, existingAnswer, pageActive, path, questionName, questionDefinition, valueType, onChangeNote, noteComponent, noteProps, onAddedAnswerPath, onDecidedOutputPath, sectionAnswersState } = props;
  let { dataType, enableNotes } = { ...props, ...questionDefinition };
  let { onAddSuggestion } = { ...props, ...noteProps };
  let [ answerID ] = useState((existingAnswer && existingAnswer[0]) || uuidv4());

  const answerPath = path + "/" + answerID;
  const answerNodeType = props.answerNodeType || "cards:" + dataType.charAt(0).toUpperCase() + dataType.slice(1) + "Answer";
  const isMultivalued = questionDefinition.maxAnswers != 1;

  useEffect(() => {
    if (sectionAnswersState !== undefined) {
      let idHistory = [];
      if (questionName in sectionAnswersState) {
        idHistory = sectionAnswersState[questionName];
      }
      if (idHistory.indexOf(answerPath) < 0)
      {
        idHistory.push(answerPath);
        sectionAnswersState[questionName] = idHistory;
        onAddedAnswerPath(sectionAnswersState);
      }
    }
  });

  // Update any listeners what our final output path will be
  useEffect(() => {
    onDecidedOutputPath && onDecidedOutputPath(answerPath);
  }, [answerPath]);

  // Hooks must be pulled from the top level, so this cannot be moved to inside the useEffect()
  const changeFormContext = useFormWriterContext();
  // Rename this variable to start with a capital letter so React knows it is a component
  const NoteComponent = noteComponent;

  // When the answers change, we inform the FormContext
  useEffect(() => {
    if (answers && answers.length > 0) {
      changeFormContext((oldContext) => ({...oldContext, [questionName]: answers}));
    }
  }, [answers]);

  return (
    <React.Fragment>
      <input type="hidden" name={`${answerPath}/jcr:primaryType`} value={answerNodeType}></input>
      <input type="hidden" name={`${answerPath}/question`} value={questionDefinition['jcr:uuid']}></input>
      <input type="hidden" name={`${answerPath}/question@TypeHint`} value="Reference"></input>

      {/* Add the answers, if any exist, or otherwise delete them */}
      {(answers && answers.length) ?
        (<React.Fragment>
          <input type="hidden" name={`${answerPath}/value@TypeHint`} value={valueType + (isMultivalued ? '[]' : '')}></input>
          {answers.map( (element, index) => {
            return (
              <input type="hidden" name={`${answerPath}/value`} key={element[VALUE_POS] === undefined ? index : element[VALUE_POS] + "" + index} value={element[VALUE_POS]}></input>
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
                    value={value}></input>
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
  noteComponent: PropTypes.elementType,
  pageActive: PropTypes.bool
};

Answer.defaultProps = {
  valueType: 'String',
  noteComponent: Note,
  pageActive: true
};

export default Answer;
