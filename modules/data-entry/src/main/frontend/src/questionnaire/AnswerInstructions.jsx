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

import { useMemo } from "react";

import { Typography } from "@mui/material";
import PropTypes from "prop-types";

import { checkPropTypes } from "../propTypes";
import { hasWarningFlags } from "./FormUtilities";

// Display instructions regarding how many answers must be provided to a question,
// based on minAnswers and maxAnswers from the question definition

function AnswerInstructions (props) {
  checkPropTypes(AnswerInstructions, props);
  let {
    variant = "verbose",
    minAnswers = 0,
    maxAnswers = 1,
    currentAnswers = 0,
    answerLabel = "value",
  } = props;

  let { isEdit, existingAnswer } = props;

  const instructionsExist = (minAnswers > 0 || maxAnswers > 1) && (isEdit || hasWarningFlags(existingAnswer));
  const isMandatory = minAnswers == 1 && !(maxAnswers > minAnswers);
  const isAtLeast = !(minAnswers < maxAnswers);
  const isExactly = minAnswers == maxAnswers;
  const isUpTo = !(minAnswers > 0);
  let range = minAnswers + " - " + maxAnswers;
  if (isUpTo) {
    range = "up to " + maxAnswers;
  } else if (isExactly) {
    range = minAnswers;
  } else if (isAtLeast) {
    range = "at least " + minAnswers;
  }

  const answerIsAcceptable  = useMemo(() =>
    (currentAnswers >= minAnswers) && (!(maxAnswers >= minAnswers) || currentAnswers <= maxAnswers)
      || !isEdit && !hasWarningFlags(existingAnswer)
  , [currentAnswers]);

  if (variant == "asterisc") {
    return (minAnswers > 0 ? <Typography variant="h6" color="error">*</Typography> : null);
  }

  if (["required", "optional", "short"].includes(variant)) {
    if (minAnswers > 0 && variant != "optional") {
      return (
        <Typography variant="caption" color="error">Required</Typography>
      );
    } else if (minAnswers == 0 && variant != "required") {
      return (
        <Typography variant="caption" color="textSecondary">Optional</Typography>
      );
    } else {
      return null;
    }
  }

  return (instructionsExist && (
    <Typography
      component="p"
      color={answerIsAcceptable ? 'textSecondary' : 'error'}
      className="cards-answerInstructions"
      variant="caption"
    >
      {
        (isMandatory) ?
          "This answer is required"
          :
          "Please provide " + range + " " + answerLabel + "s"
      }
    </Typography>
  )
  );
}

AnswerInstructions.propTypes = {
  variant: PropTypes.oneOf(["asterisc", "required", "optional", "short", "verbose"]),
  minAnswers: PropTypes.number,
  maxAnswers: PropTypes.number,
  currentAnswers: PropTypes.number,
  answerLabel: PropTypes.string,
};

export function isRequired(questionDefinition) {
  return questionDefinition?.minAnswers > 0;
}

export default AnswerInstructions;
