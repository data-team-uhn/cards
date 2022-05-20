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

import React, { useState, useEffect, useContext } from "react";
import { CircularProgress, withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import MultipleChoice from "./MultipleChoice";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

// Component that renders a question, where the answer options are children of a given JCR node

function ResourceQuestion(props) {
  const {classes, ...rest} = props;
  const {primaryType, labelProperty} = { ...props.questionDefinition };
  const [options, setOptions] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    // TODO: with too many matching resources, default to a suggested input
    // (to be implemented) regardless of the display mode
    const url = (
      primaryType
      ? `/query?query=select * from [${primaryType}]&limit=1000`
      : undefined
    );
    url && fetchWithReLogin(globalLoginDisplay, url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => setOptions(
        json?.rows?.map(row => [row[labelProperty] || row["@name"], row["@path"], true])
        || []
      ))
  }, []);

  return (
    <Question
      disableInstructions
      {...props}
      >
      { options ?
        <MultipleChoice
          answerNodeType="cards:ResourceAnswer"
          valueType="String"
          defaults={options.length > 0 ? options : undefined}
          {...rest}
          />
        : <CircularProgress />
      }
    </Question>);
}

ResourceQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    primaryType: PropTypes.string,
    labelProperty: PropTypes.string,
  }).isRequired,
};

const StyledResourceQuestion = withStyles(QuestionnaireStyle)(ResourceQuestion)
export default StyledResourceQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "resource") {
    return [StyledResourceQuestion, 50];
  }
});
