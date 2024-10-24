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

import React, { useState } from "react";

import { IconButton, Tooltip } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import ContentCopyIcon from "@mui/icons-material/ContentCopy";

import PropTypes from "prop-types";

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";
import FormattedText from "../components/FormattedText.jsx";
import AnswerComponentManager from "./AnswerComponentManager";

// Component that renders an identifier question, with optional copy button.
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
//  text: String containing text to show alongside the identifier
//  displayMode: "plain", "plain+copy", or undefined denoting how the identifier is presented
//             to the user. "+copy" adds a button the user can press to copy the value to clipboard.
//             Defaults to "plain".
//
// Sample usage:
// <NumberQuestion
//    text="Visit Identifier"
//    displayMode="plain+copy"
//    dataType="identifier"
//    />
function IdentifierQuestion(props) {
  const { existingAnswer, classes, pageActive, isEdit, ...rest} = props;
  const { displayMode } = {...props.questionDefinition };
  const [ text, setText ] = useState("Copy to Clipboard")

  const initialValue = existingAnswer?.[1]?.value || "";
  const answer = initialValue === "" ? [] : [["value", initialValue]];

  const handleClick = () => {
    navigator.clipboard.writeText(initialValue);
    setText("Copied");
  }

  const handleClose = () => {
    setText("Copy to Clipboard");
  }

  return (
    <Question
      disableInstructions
      preventDefaultView
      {...props}
      >
        { pageActive && <>
          <FormattedText className={classes.identifierQuestionText}>{initialValue}</FormattedText>
          { displayMode.endsWith("+copy") &&
            <Tooltip title={text} onClose={handleClose} className={classes.identifierQuestionButton}>
              <IconButton onClick={handleClick}>
                <ContentCopyIcon />
              </IconButton>
            </Tooltip>
          }
          <div style={{clear: "both"}}></div>
        </> }
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
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    displayMode: PropTypes.oneOf(["plain", "plain+copy"]),
  }).isRequired
};

IdentifierQuestion.defaultProps = {
  questionDefinition: PropTypes.shape({
    displayMode: "plain"
  })
};

const StyledIdentifierQuestion = withStyles(QuestionnaireStyle)(IdentifierQuestion)
export default StyledIdentifierQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (["identifier"].includes(questionDefinition.dataType)) {
    return [StyledIdentifierQuestion, 50];
  }
});
