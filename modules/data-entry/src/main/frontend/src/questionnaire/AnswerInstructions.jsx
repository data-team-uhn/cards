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

import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import { Typography, withStyles } from "@material-ui/core";

import QuestionnaireStyle from "./QuestionnaireStyle";

// Display instructions regarding how many answers must be provided to a question,
// based on minAnswers and maxAnswers from the question definition

function AnswerInstructions (props) {
  let { classes, minAnswers, maxAnswers, currentAnswers, answerLabel } = props;
  let [ answerIsAcceptable, setAnswerAcceptable] = useState(currentAnswers >= minAnswers && (maxAnswers == 0 || currentAnswers <= maxAnswers));

  const instructionsExist = minAnswers > 0 || maxAnswers > 1;
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

  useEffect(() => {
    setAnswerAcceptable((currentAnswers >= minAnswers) && (!(maxAnswers >= minAnswers) || currentAnswers <= maxAnswers))
  }, [currentAnswers]);

  return (instructionsExist && (
    <Typography
      component="p"
      color={ answerIsAcceptable ? 'textSecondary' : 'error'}
      className={classes.answerInstructions}
      variant="caption"
    >
      {
        (isMandatory) ?
        "This question is mandatory"
        :
        "Please provide " + range + " " + answerLabel + "s"
      }
    </Typography>
    )
  );
}

AnswerInstructions.propTypes = {
    classes: PropTypes.object.isRequired,
    minAnswers: PropTypes.number,
    maxAnswers: PropTypes.number,
    currentAnswers: PropTypes.number,
    answerLabel: PropTypes.string,
};

AnswerInstructions.defaultProps = {
    minAnswers: 0,
    maxAnswers: 0,
    currentAnswers: 0,
    answerLabel: "value",
};

export default withStyles(QuestionnaireStyle)(AnswerInstructions);
