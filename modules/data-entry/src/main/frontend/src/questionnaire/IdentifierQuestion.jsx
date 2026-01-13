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

import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import { Button, Tooltip } from "@mui/material";
import PropTypes from "prop-types";
import { v4 as uuidv4 } from 'uuid';

import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";
import Question from "./Question";

// Component that renders an identifier question as a plain, read-only string copyable on click, with an optional copy button.
//
// The expected displayMode is either "plain" or "plain+copy", the latter adding a visible button the user can press to copy the value to clipboard.
//
// Sample usage:
// <IdentifierQuestion questionDefinition={{ text: "Identifier", displayMode: "plain+copy", ... }} />

export default function IdentifierQuestion(props) {
  const { existingAnswer, pageActive, isEdit, ...rest } = props;
  const {
    displayMode = "plain",
    identifierType = "uuid"
  } = { ...props.questionDefinition };
  const COPY_TO_CLIPBOARD = "Copy to clipboard";
  const [ text, setText ] = useState(COPY_TO_CLIPBOARD);

  const [value, setValue] = useState(existingAnswer?.[1]?.value || "");
  const answer = [[value, value]];

  // Dead code, left intentionaly to add support for other identifiers
  useEffect(() => {
    if (isEdit && (!value || value.length == 0)) {
      switch (identifierType) {
        case "uuid":
        default:
          setValue(uuidv4());
          break;
      }
    }
  }, [identifierType, isEdit]);

  const handleClick = () => {
    navigator.clipboard.writeText(value);
    setText("Copied");
  }

  const handleClose = () => {
    setText(COPY_TO_CLIPBOARD);
  }

  return (
    <Question
      disableInstructions
      preventDefaultView
      {...props}
    >
      { pageActive &&
          <Tooltip title={text} onClose={handleClose}>
            <Button
              onClick={handleClick}
              endIcon={ displayMode.endsWith("+copy") ? <ContentCopyIcon /> : null}
              sx={{ padding: 0, textTransform: "none" }}
            >
              { value}
            </Button>
          </Tooltip>
      }
      { isEdit &&
        <Answer
          answers={answer}
          existingAnswer={existingAnswer}
          answerNodeType="cards:IdentifierAnswer"
          valueType="String"
          pageActive={pageActive}
          {...rest}
        /> }
    </Question>);
}

IdentifierQuestion.propTypes = {
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    displayMode: PropTypes.oneOf(["plain", "plain+copy"]),
  }).isRequired
};

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (["identifier"].includes(questionDefinition.dataType)) {
    return [IdentifierQuestion, 50];
  }
});
