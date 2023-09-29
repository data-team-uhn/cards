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

import React from "react";

import withStyles from '@mui/styles/withStyles';

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
  // By default we enable 22 numbered chromosomes plus X and Y
  const defaultValues = {
    chromosomeNumber : 22,
    'X' : true,
    'Y' : true,
    'Z' : false,
    'W' : false,
    'MT': false
  };

  let { chromosomeNumber } = {...defaultValues, ...props.questionDefinition};
  let defaults = [];
  for (let i = 1; i <= chromosomeNumber; i++) {
    defaults.push([i.toString(), i.toString(), true]);
  }

  // We override the defaults above with the questionnaire definition
  const enabledChromosomes = {...Object.entries(defaultValues).reduce((accumulator, [key, value]) => {accumulator[`enable${key}`] = value; return accumulator;}, {}), ...props.questionDefinition};

  // Whatever is left enabled, we display
  for (let chromosome of Object.keys(defaultValues)) {
    if (enabledChromosomes[`enable${chromosome}`] === true) {
      defaults.push([chromosome, chromosome, true]);
    }
  }

  return (
    <Question
      disableInstructions
      {...props}
      >
      <MultipleChoice
        defaults={defaults}
        {...props}
        />
    </Question>);
}

ChromosomeQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    minAnswers: PropTypes.number,
    maxAnswers: PropTypes.number,
    chromosomeNumber: PropTypes.number
  }).isRequired,
};

const StyledChromosomeQuestion = withStyles(QuestionnaireStyle)(ChromosomeQuestion)
export default StyledChromosomeQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "chromosome") {
    return [StyledChromosomeQuestion, 50];
  }
});
