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

import { Alert, Typography } from "@mui/material";
import PropTypes from "prop-types";

import AnswerComponentManager from "./AnswerComponentManager";
import FileQuestion from "./FileQuestion";
import FormattedText from "../components/FormattedText";
import { checkPropTypes } from "../propTypes";
import MarkdownText from "../questionnaireEditor/MarkdownText";

const ACCEPTED_PROPOSAL_EXTENSIONS = [".pdf", ".docx", ".doc"];

const ACCEPTED_PROPOSAL_MIME_TYPES =
  ".pdf,.docx,.doc,application/pdf,application/msword,"
  + "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

/**
 * Extract the lowercase file extension including the leading dot.
 *
 * @param {string} fileName uploaded file name
 * @returns {string|null} extension such as ".pdf", or null when absent
 */
function extractFileExtension(fileName) {
  if (!fileName) {
    return null;
  }
  const dotIndex = fileName.lastIndexOf(".");
  if (dotIndex < 0 || dotIndex === fileName.length - 1) {
    return null;
  }
  return fileName.substring(dotIndex).toLowerCase();
}

/**
 * Validate proposal uploads by file extension.
 *
 * @param {FileList} files selected files
 * @returns {string|undefined} user-visible error when any file is unsupported
 */
function validateProposalFiles(files) {
  const errors = [];
  for (let i = 0; i < files.length; i++) {
    const file = files.item(i);
    const extension = extractFileExtension(file.name);
    if (!extension || !ACCEPTED_PROPOSAL_EXTENSIONS.includes(extension)) {
      const format = extension || "(unknown)";
      errors.push(
        `Unsupported file format ${format}, file ${file.name} can not be processed. `
        + "Accepted formats: .pdf, .docx, .doc"
      );
    }
  }
  return errors.length > 0 ? errors.join(" ") : undefined;
}

const PARSE_ERROR_PREFIX = "<!-- parse_error: ";

/**
 * Extract a user-visible parse error from a persisted proposal note.
 *
 * @param {string} note answer note value
 * @returns {string|null} error message when the note encodes a parse failure
 */
function extractParseError(note) {
  if (!note?.startsWith(PARSE_ERROR_PREFIX)) {
    return null;
  }
  return note.slice(PARSE_ERROR_PREFIX.length).replace(/\s*-->$/, "");
}

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

  const parseError = extractParseError(note);

  if (!pageActive) {
    return <></>;
  }

  if (readonly) {
    return (
      <>
        {parseError && (
          <Alert severity="error" sx={{ mb: 1 }}>
            {parseError}
          </Alert>
        )}
        {note && !parseError ? (
          <div>
            <Typography variant="subtitle1">Notes</Typography>
            <FormattedText>{note}</FormattedText>
          </div>
        ) : null}
      </>
    );
  }

  return (
    <>
      {parseError && (
        <Alert severity="error" sx={{ mb: 1 }}>
          {parseError}
        </Alert>
      )}
      <Typography variant="subtitle1">Notes</Typography>
      <MarkdownText
        value={parseError ? "" : note}
        preview="edit"
        height={260}
        onChange={(newValue) => setNote(newValue || "")}
      />
      {note
        ? <input type="hidden" name={`${answerPath}/note`} value={note} />
        : <input type="hidden" name={`${answerPath}/note@Delete`} value="0" />}
      {(!note || note.trim().length === 0) && !parseError
        && <Typography variant="caption" color="textSecondary">{placeholder}</Typography>}
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
      accept={ACCEPTED_PROPOSAL_MIME_TYPES}
      validateFiles={validateProposalFiles}
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
