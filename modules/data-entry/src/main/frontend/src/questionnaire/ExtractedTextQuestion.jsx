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

import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Button, Collapse, TextField } from "@mui/material";
import PropTypes from "prop-types";

import { checkPropTypes } from "../propTypes";
import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";
import Question from "./Question";

// Component that renders an extracted-text question: a large multiline text field.
// If the question definition has a "prompt" property, it is displayed as a
// collapsible read-only text field beneath the question title so the user can
// inspect the extraction prompt without it cluttering the editing area.
//
// Sample usage:
//
// <ExtractedTextQuestion
//   questionDefinition={{
//     text: "Study Title",
//     dataType: "extractedText",
//     prompt: "Extract the official title of the research study...",
//   }}
// />
function ExtractedTextQuestion(props) {
  checkPropTypes(ExtractedTextQuestion, props);
  const { existingAnswer, pageActive, questionDefinition, ...rest } = props;

  const currentStartValue = existingAnswer?.[1]?.value || "";
  const [value, setValue] = useState(currentStartValue);
  const [promptOpen, setPromptOpen] = useState(false);
  const [answerPath, setAnswerPath] = useState(null);
  // True once the user has changed the value away from what the server stored (the AI-extracted value, if any).
  const edited = value !== currentStartValue;

  // Keep the field in sync with the stored answer so values written by the server-side extraction
  // (after navigating to this page or pressing Re-extract) are reflected in the input. The dependency is
  // the stored value itself, so typing into the field is never overwritten by an unrelated re-render.
  useEffect(() => {
    setValue(currentStartValue);
  }, [currentStartValue]);

  const { prompt } = questionDefinition;
  const questionWithNotes = { ...questionDefinition, enableNotes: true };

  return (
    <Question
      disableInstructions
      {...props}
      questionDefinition={questionWithNotes}
    >
      {pageActive && prompt && (
        <>
          <Button
            variant="text"
            size="small"
            onClick={() => setPromptOpen(open => !open)}
            startIcon={promptOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}
            sx={{ mb: 0.5, textTransform: "none" }}
          >
            {promptOpen ? "Hide prompt" : "Show prompt"}
          </Button>
          <Collapse in={promptOpen}>
            <TextField
              multiline
              fullWidth
              variant="outlined"
              value={prompt}
              slotProps={{ input: { readOnly: true } }}
              size="small"
              sx={{ mb: 1 }}
            />
          </Collapse>
        </>
      )}
      <TextField
        className="cards-answerTextField"
        multiline
        fullWidth
        minRows={3}
        variant="standard"
        onChange={event => setValue(event.target.value)}
        value={value}
      />
      <Answer
        answers={[["value", value]]}
        questionDefinition={questionWithNotes}
        answerNodeType="cards:ExtractedTextAnswer"
        valueType="String"
        existingAnswer={existingAnswer}
        pageActive={pageActive}
        onDecidedOutputPath={setAnswerPath}
        {...rest}
      />
      {/* Once the user edits an AI-extracted value, mark it as no longer AI-extracted and drop the
          supporting evidence/confidence so they cannot misrepresent a hand-entered value. */}
      {pageActive && edited && answerPath &&
        <>
          <input type="hidden" name={`${answerPath}/extracted`} value="false" />
          <input type="hidden" name={`${answerPath}/extracted@TypeHint`} value="Boolean" />
          <input type="hidden" name={`${answerPath}/evidence@Delete`} value="0" />
          <input type="hidden" name={`${answerPath}/confidence@Delete`} value="0" />
        </>
      }
    </Question>
  );
}

ExtractedTextQuestion.propTypes = {
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    prompt: PropTypes.string,
  }).isRequired,
};

export default ExtractedTextQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "extractedText") {
    return [ExtractedTextQuestion, 50];
  }
});
