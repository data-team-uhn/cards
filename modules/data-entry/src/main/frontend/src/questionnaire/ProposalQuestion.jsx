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

import { Alert } from "@mui/material";
import PropTypes from "prop-types";

import AnswerComponentManager from "./AnswerComponentManager";
import FileQuestion from "./FileQuestion";
import { checkPropTypes } from "../propTypes";

const ACCEPTED_PROPOSAL_EXTENSIONS = [".pdf", ".docx", ".doc"];
const MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB

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

    if (file.size === 0) {
      errors.push("The selected file is empty (0 bytes).");
    }

    if (file.size > MAX_FILE_SIZE) {
      errors.push("The selected file exceeds the maximum size of 50 MB.");
    }

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
    existingAnswer,
    pageActive,
  } = props;

  const parseError = extractParseError(existingAnswer?.[1]?.note);

  if (!pageActive || !parseError) {
    return <></>;
  }

  return (
    <Alert severity="error" sx={{ mb: 1 }}>
      {parseError}
    </Alert>
  );
}

ProposalNote.propTypes = {
  existingAnswer: PropTypes.array,
  pageActive: PropTypes.bool,
};

function ProposalQuestion(props) {
  checkPropTypes(ProposalQuestion, props);
  const { questionDefinition, ...rest } = props;

  return (
    <FileQuestion
      questionDefinition={{ maxAnswers: 0, ...questionDefinition, enableNotes: true }}
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
