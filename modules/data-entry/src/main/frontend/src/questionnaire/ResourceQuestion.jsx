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
import { CircularProgress} from '@mui/material';
import withStyles from '@mui/styles/withStyles';

import PropTypes from "prop-types";

import MultipleChoice from "./MultipleChoice";
import Question from "./Question";
import ResourceQuery from "../resourceQuery/ResourceQuery";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

// Component that renders a question, where the answer options are children of a given JCR node

function ResourceQuestion(props) {
  const {classes, ...rest} = props;
  const {primaryType, labelProperty, maxAnswers, displayMode} = { ...props.questionDefinition };
  const [options, setOptions] = useState();

  // If the display mode is list or select, set a limit to how many entries can be displayed in
  // the list or dropdown.
  // If the real number of resources matching the criteria specified by the question definition
  // excedes this limit, ignore the specified displayMode and use a suggested input instead.
  const MAX_TO_DISPLAY = 50;

  // In when the input is a fallback as explained above, it should not accept custom entries
  const enableUserEntry = !!!displayMode || displayMode.includes("input");

  // In order to determine indentation levels, we need to see if there are any default suggestions
  // (i.e. children of the definition that are of type cards:AnswerOption)
  let defaults = props.defaults || Object.values(props.questionDefinition)
    .filter(value => value['jcr:primaryType'] == 'cards:AnswerOption');
  let singleEntryInput = maxAnswers === 1;
  let isNested = !!(singleEntryInput && defaults?.length);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    const url = (
      primaryType
      ? `/query?query=select * from [${primaryType}] as n order by n.'cards:defaultOrder', n.'${labelProperty}'&limit=${MAX_TO_DISPLAY}`
      : undefined
    );
    if (!enableUserEntry && !defaults?.length && url) {
      // NB: if there are too many matching resources, we default to a suggested input
      // (by resetting `options` to []) regardless of the display mode
      fetchWithReLogin(globalLoginDisplay, url)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => setOptions(
          (json?.totalrows == json?.returnedrows) &&
          json?.rows?.map(row => [row[labelProperty] || row["@name"], row["@path"], true])
          || []
        ))
    } else {
      setOptions([]);
    }
  }, []);

  return (
    <Question
      disableInstructions
      {...props}
      >
      { options ?
        <MultipleChoice
          customInput = {options.length == 0 ? ResourceQuery : undefined}
          customInputProps = {{
            questionDefinition: props.questionDefinition,
            focusAfterSelecting: !singleEntryInput,
            isNested: isNested,
            variant: "labeled",
            clearOnClick: !singleEntryInput,
            enableUserEntry: enableUserEntry,
          }}
          valueType="String"
          defaults={props.defaults || (options.length > 0 ? options : undefined)}
          {...rest}
          />
        : <CircularProgress />
      }
    </Question>);
}

ResourceQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    maxAnswers: PropTypes.number,
    displayMode: PropTypes.string,
    primaryType: PropTypes.string,
    labelProperty: PropTypes.string,
    propertiesToSearch: PropTypes.string,
  }).isRequired,
};

const StyledResourceQuestion = withStyles(QuestionnaireStyle)(ResourceQuestion)
export default StyledResourceQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "resource") {
    return [StyledResourceQuestion, 50];
  }
});
