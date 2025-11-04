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

import { useEffect, useState } from "react";

import { List, ListItem } from "@mui/material";
import PropTypes from 'prop-types';
import { v4 as uuidv4 } from 'uuid';

import { checkPropTypes } from "../propTypes";
import AnswerComponentManager from "./AnswerComponentManager";
import { useFormWriterContext } from "./FormContext";
import Note from "./Note";
import Question from "./Question";
import FormattedText from "../components/FormattedText";

// Component that displays an autocreated question of any type.
//
// Other options are passed to the <question> widget
let AutocreatedQuestion = (props) => {
  checkPropTypes(AutocreatedQuestion, props);
  const {
    isEdit,
    noteComponent = Note,
    noteProps,
    onChangeNote,
    pageActive = true,
    path,
    ...rest
  } = props;
  const { existingAnswer, questionName } = rest;
  const { displayMode, enableNotes } = { ...props.questionDefinition, ...rest };

  const [isFormatted, changeIsFormatted] = useState(false);

  // If we are in edit mode, upon loading the pre-filled answers, place them
  // in the form context where they can be accessed by computed answers
  const changeFormContext = useFormWriterContext();
  // Rename this variable to start with a capital letter so React knows it is a component
  const NoteComponent = noteComponent;

  let { onAddSuggestion } = { ...props, ...noteProps };
  let [ answerID ] = useState((existingAnswer && existingAnswer[0]) || uuidv4());
  let answerPath = path + "/" + answerID;

  useEffect(() => {
    if (isEdit) {
      let value = existingAnswer?.[1].value;
      if (typeof(value) != "undefined" && value != "") {
        let answer = Array.of(value).flat().map(v => [v, v]);
        changeFormContext((oldContext) => ({ ...oldContext, [questionName]: answer }));
      }
    }
  }, []);

  useEffect(() => {
    let formatted = (displayMode === "formatted" || displayMode === "summary");
    if (formatted !== isFormatted) {
      changeIsFormatted(formatted)
    };
  }, [displayMode])

  // Autocreated answers are read-only and displayed the same in view and edit modes
  // Answer instructions are not displayed since there's nothing the user can do in this form to actually follow them, as the answers are read-only
  return (
    <Question
      isEdit={isEdit}
      preventDefaultView
      disableInstructions
      {...props}
    >
      { typeof(existingAnswer?.[1].value) != 'undefined' &&
        <List sx={{ p: 0 }}>
          { Array.of(existingAnswer[1].displayedValue).flat().map(v => (
            <ListItem key={existingAnswer[0]+v} sx={{ py: 0 }}>
              { isFormatted ? <FormattedText>{`${v}`}</FormattedText> : v }
            </ListItem>
          ))}
        </List>
      }
      { isEdit && enableNotes &&
        <NoteComponent
          existingAnswer={existingAnswer}
          answerPath={answerPath}
          onChangeNote={onChangeNote}
          onAddSuggestion={onAddSuggestion}
          pageActive={pageActive}
          {...noteProps}
        />
      }
    </Question>
  )
}

AutocreatedQuestion.propTypes = {
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    description: PropTypes.string,
    displayMode: PropTypes.oneOf(['plain', 'formatted', 'hidden', 'summary']),
    unitOfMeasurement: PropTypes.string
  }).isRequired
};

export default  AutocreatedQuestion;

AnswerComponentManager.registerAnswerComponent((definition) => {
  if (definition.entryMode === "autocreated") {
    return [AutocreatedQuestion, 80];
  }
});
