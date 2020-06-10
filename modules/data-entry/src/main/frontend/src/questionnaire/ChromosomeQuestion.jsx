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

import { Typography, withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import MultipleChoice from "./MultipleChoice";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

// Component that renders a multiple choice question, with the choices being
// the set of chromosomes in an organism.
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission. By default the chromosome for humans are disabled: 22 numbered
// chromosomes, plus X and Y. The question supports any number of chromosomes,
// and any of the X, Y, Z, W, and MT chromosomes can be enabled.
//
// Optional arguments:
//  max: Integer denoting maximum number of arguments that may be selected
//  min: Integer denoting minimum number of arguments that may be selected
//  text: String containing the question to ask
//  chromosomeNumber: Integer denoting how many numbered chromosomes to display (22 by default)
//  enableX: Boolean, whether the X chromosome is enabled or not (true by default)
//  enableY: Boolean, whether the Y chromosome is enabled or not (true by default)
//  enableZ: Boolean, whether the Z chromosome is enabled or not (false by default)
//  enableW: Boolean, whether the W chromosome is enabled or not (false by default)
//  enableMT: Boolean, whether the Mitochondrial DNA chromosome is enabled or not (false by default)
//
// sample usage:
// <ChromosomeQuestion
//    text="Test text question (lowercase only)"
//    />
function ChromosomeQuestion(props) {
  let { ...rest } = props;
  let { chromosomeNumber,  chromosomeX, chromosomeY, chromosomeZ, chromosomeW, chromosomeMT } = {...props.questionDefinition, ...props};
  chromosomeNumber = chromosomeNumber || 22;
  let menuitems_list = [];
  for (let i = 1; i <= chromosomeNumber; i++) {
    menuitems_list.push(i.toString());
  }
  if (chromosomeX === false) {
    chromosomeX = false;
  } else {
    chromosomeX = true;
    menuitems_list.push("X");
  }
  if (chromosomeY === false) {
    chromosomeY = false;
  } else {
    chromosomeY = true;
    menuitems_list.push("Y");
  }
  if (chromosomeZ === true) {
      chromosomeZ = true;
      menuitems_list.push("Z");
  } else {
    chromosomeZ = false;
  }
  if (chromosomeW === true) {
      chromosomeW = true;
      menuitems_list.push("W");
  } else {
    chromosomeW = false;
  }
  if (chromosomeMT === true) {
      chromosomeMT = true;
      menuitems_list.push("MT");
  } else {
    chromosomeMT = false;
  }

  return (
    <Question
      {...rest}
      >
      <MultipleChoice
        answerNodeType="lfs:ChromosomeAnswer"
        input="input"
        textbox="textbox"
        menuitems={menuitems_list}
        {...rest}
        />
    </Question>);
}

ChromosomeQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    minAnswers: PropTypes.number,
    maxAnswers: PropTypes.number,
    chromosomeNumber: PropTypes.number
  }).isRequired,
  text: PropTypes.string,
  minAnswers: PropTypes.number,
  maxAnswers: PropTypes.number,
  chromosomeNumber: PropTypes.number
};

ChromosomeQuestion.defaultProps = {
  displayType: "select"
};

const StyledChromosomeQuestion = withStyles(QuestionnaireStyle)(ChromosomeQuestion)
export default StyledChromosomeQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "chromosome") {
    return [StyledChromosomeQuestion, 50];
  }
});
