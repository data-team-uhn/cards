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
import JSZip from "jszip";
import * as pdfjsLib from "pdfjs-dist";
import pdfWorkerSrc from "pdfjs-dist/build/pdf.worker.min.mjs";
import PropTypes from "prop-types";

import AnswerComponentManager from "./AnswerComponentManager";
import FileQuestion from "./FileQuestion";
import { checkPropTypes } from "../propTypes";

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerSrc;

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
 * Try to open a PDF with PDF.js; rejects corrupted or non-PDF content.
 *
 * @param {File} file uploaded file
 * @returns {Promise<{valid: boolean, pageCount?: number, error?: string}>}
 */
async function validatePdf(file) {
  try {
    const data = await file.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data }).promise;
    return {
      valid: true,
      pageCount: pdf.numPages,
    };
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error("PDF validation failed:", err);
    return {
      valid: false,
      error: "The PDF is corrupted or cannot be read.",
    };
  }
}

/**
 * Confirm a DOCX is a readable Open XML zip with the required parts.
 *
 * @param {File} file uploaded file
 * @returns {Promise<{valid: boolean, error?: string}>}
 */
async function validateDocx(file) {
  try {
    const zip = await JSZip.loadAsync(file);
    const hasContentTypes = Boolean(zip.file("[Content_Types].xml"));
    const hasDocument = Boolean(zip.file("word/document.xml"));

    if (!hasContentTypes || !hasDocument) {
      return {
        valid: false,
        error: "The file is not a valid DOCX document.",
      };
    }

    return { valid: true };
  } catch {
    return {
      valid: false,
      error: "The DOCX file is corrupted or cannot be read.",
    };
  }
}

/**
 * Legacy .doc files are OLE compound documents; check the magic header only.
 *
 * @param {File} file uploaded file
 * @returns {Promise<{valid: boolean, error?: string}>}
 */
async function validateDoc(file) {
  try {
    const header = await file.slice(0, 8).arrayBuffer();
    const bytes = new Uint8Array(header);
    const oleMagic = [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1];
    const matches = oleMagic.every((value, index) => bytes[index] === value);
    if (!matches) {
      return {
        valid: false,
        error: "The selected file is not a valid DOC document.",
      };
    }
    return { valid: true };
  } catch {
    return {
      valid: false,
      error: "The DOC file is corrupted or cannot be read.",
    };
  }
}

/**
 * Validate proposal uploads by size, extension, and file content.
 *
 * @param {FileList|File[]} files selected files
 * @returns {Promise<string|undefined>} user-visible error when any file is unsupported
 */
async function validateProposalFiles(files) {
  const errors = [];
  for (let i = 0; i < files.length; i++) {
    const file = files[i];

    if (file.size === 0) {
      errors.push("The selected file is empty (0 bytes).");
      continue;
    }

    if (file.size > MAX_FILE_SIZE) {
      errors.push("The selected file exceeds the maximum size of 50 MB.");
      continue;
    }

    const extension = extractFileExtension(file.name);
    if (!extension || !ACCEPTED_PROPOSAL_EXTENSIONS.includes(extension)) {
      const format = extension || "(unknown)";
      errors.push(
        `Unsupported file format ${format}, file ${file.name} can not be processed. `
        + "Accepted formats: .pdf, .docx, .doc"
      );
      continue;
    }

    let contentResult;
    if (extension === ".pdf") {
      contentResult = await validatePdf(file);
    } else if (extension === ".docx") {
      contentResult = await validateDocx(file);
    } else if (extension === ".doc") {
      contentResult = await validateDoc(file);
    }

    if (contentResult && !contentResult.valid) {
      errors.push(`${file.name}: ${contentResult.error}`);
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
