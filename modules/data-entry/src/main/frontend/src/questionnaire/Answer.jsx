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
import uuidv4 from "uuid/v4";

import Note from "./Note";
import { useFormWriterContext } from "./FormContext";

export const LABEL_POS = 0;
export const VALUE_POS = 1;

// Holds answers and automatically generates hidden inputs
// for form submission
function Answer (props) {
  let { answers, answerNodeType, existingAnswer, path, questionName, questionDefinition, valueType, onChangeNote, noteComponent, noteProps } = props;
  let { enableNotes, sourceVocabulary } = { ...props, ...questionDefinition };
  let [ answerID ] = useState((existingAnswer && existingAnswer[0]) || uuidv4());
  let answerPath = path + "/" + answerID;
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
          <input type="hidden" name={`${answerPath}/value@TypeHint`} value={valueType}></input>
          {answers.map( (element, index) => {
            return (
              <input type="hidden" name={`${answerPath}/value`} key={element[VALUE_POS] === undefined ? index : element[VALUE_POS]} value={element[VALUE_POS]}></input>
              );
          })}
        </React.Fragment>)
      :
        <input type="hidden" name={`${answerPath}/value@Delete`} value="0"></input>
      }
      {enableNotes &&
        <NoteComponent
          existingAnswer={existingAnswer}
          answerPath={answerPath}
          onChangeNote={onChangeNote}
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
    noteComponent: PropTypes.elementType
};

Answer.defaultProps = {
  answerNodeType: "lfs:TextAnswer",
  valueType: 'String',
  noteComponent: Note
};

export default Answer;
