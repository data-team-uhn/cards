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

  let instructionsExist = ( minAnswers > 0 || minAnswers == 0 && maxAnswers > 1);

  useEffect(() => {
    setAnswerAcceptable(currentAnswers >= minAnswers && (maxAnswers == 0 || currentAnswers <= maxAnswers))
  }, [currentAnswers]);

  return (instructionsExist && (
    <Typography
      component="p"
      color={ answerIsAcceptable ? 'textSecondary' : 'secondary'}
      className={classes.answerInstructions}
      variant="caption"
    >
      { (minAnswers == 1 && !(maxAnswers > minAnswers)) ?
      "This question is mandatory"
      :
      "Please provide " + minAnswers + (maxAnswers != minAnswers ? (" - " + maxAnswers) : "" ) + " " + answerLabel + (maxAnswers > 1 ? "s" : "")
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
    currentAnswers: 0,
    answerLabel: "value",
};

export default withStyles(QuestionnaireStyle)(AnswerInstructions);
