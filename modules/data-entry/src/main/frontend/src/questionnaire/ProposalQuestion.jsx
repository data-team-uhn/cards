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

import { Typography } from "@mui/material";
import PropTypes from "prop-types";

import AnswerComponentManager from "./AnswerComponentManager";
import FileQuestion from "./FileQuestion";
import FormattedText from "../components/FormattedText";
import { checkPropTypes } from "../propTypes";
import MarkdownText from "../questionnaireEditor/MarkdownText";

function ProposalNote(props) {
  const {
    answerPath,
    existingAnswer,
    onChangeNote,
    pageActive,
    readonly,
    value,
    placeholder = "Parsed markdown notes",
  } = props;
  const [ note, setNote ] = useState(existingAnswer?.[1]?.note || "");

  useEffect(() => {
    if (typeof(value) != "undefined") {
      setNote(value || "");
    }
  }, [value]);

  useEffect(() => onChangeNote?.(note), [note, onChangeNote]);

  if (!pageActive) {
    return <></>;
  }

  if (readonly) {
    return note ? (
      <div>
        <Typography variant="subtitle1">Notes</Typography>
        <FormattedText>{note}</FormattedText>
      </div>
    ) : null;
  }

  return (
    <>
      <Typography variant="subtitle1">Notes</Typography>
      <MarkdownText
        value={note}
        preview="edit"
        height={260}
        onChange={(newValue) => setNote(newValue || "")}
      />
      {note
        ? <input type="hidden" name={`${answerPath}/note`} value={note} />
        : <input type="hidden" name={`${answerPath}/note@Delete`} value="0" />}
      {(!note || note.trim().length === 0) && <Typography variant="caption" color="textSecondary">{placeholder}</Typography>}
    </>
  );
}

ProposalNote.propTypes = {
  answerPath: PropTypes.string,
  existingAnswer: PropTypes.array,
  onChangeNote: PropTypes.func,
  pageActive: PropTypes.bool,
  readonly: PropTypes.bool,
  value: PropTypes.string,
  placeholder: PropTypes.string,
};

function ProposalQuestion(props) {
  checkPropTypes(ProposalQuestion, props);
  const { questionDefinition, ...rest } = props;

  return (
    <FileQuestion
      questionDefinition={{ ...questionDefinition, maxAnswers: 1, enableNotes: true }}
      {...rest}
      answerNodeType="cards:ProposalAnswer"
      accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      noteComponent={ProposalNote}
    />
  );
}

ProposalQuestion.propTypes = {
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
  }).isRequired,
};

export default ProposalQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "proposal") {
    return [ProposalQuestion, 50];
  }
});
