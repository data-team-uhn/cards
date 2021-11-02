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
import PropTypes from "prop-types";

import FormattedText from "../components/FormattedText";
import { QUESTION_TYPES, SECTION_TYPES, ENTRY_TYPES } from "./FormEntry.jsx";

import {
  Chip,
  Tooltip,
  Typography,
} from "@material-ui/core";

// Display the questions/question found within sections
export function displayQuestion(entryDefinition, data, key, classes) {
  const existingQuestionAnswer = data && Object.entries(data)
    .find(([key, value]) => value["sling:resourceSuperType"] == "cards/Answer"
      && value["question"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);

  // question title, to be used when 'previewing' the form
  const questionTitle = entryDefinition["text"];
  // check the display mode and don't display if "hidden"
  const isHidden = (entryDefinition.displayMode == "hidden");

  if (typeof(existingQuestionAnswer?.[1]?.value) != "undefined") {
    let prettyPrintedAnswers = existingQuestionAnswer[1]["displayedValue"];
    // The value can either be a single value or an array of values; force it into an array
    prettyPrintedAnswers = Array.of(prettyPrintedAnswers).flat();

    let content = "";
    switch(entryDefinition["dataType"]) {
      case "file":
        // The value can either be a single value or an array of values; force it into an array
        let paths = Array.of(existingQuestionAnswer[1]["value"]).flat();
        content = <>
          {prettyPrintedAnswers.map((answerValue, idx) => {
            // Encode the filename to ensure special charactars don't result in a broken link
            let path = paths[idx].slice(0, paths[idx].lastIndexOf(answerValue)) + encodeURIComponent(answerValue);
            return (
                <Tooltip key={answerValue} title={"Download " + answerValue}>
                  <Chip
                    icon={<FileIcon />}
                    label={<a href={path} target="_blank" rel="noopener" download={answerValue}>{answerValue}</a>}
                    color="primary"
                    variant="outlined"
                    size="small"
                  />
                </Tooltip>
            );
          })}
          </>
        break;
      case "pedigree":
        if (!prettyPrintedAnswers) {
          // Display absolutely nothing if the value does not exist
          return null;
        } else {
          // Display Pedigree: yes if the value does exist
          content = "Yes";
        }
        break;
      case "computed":
        content = prettyPrintedAnswers.join(", ");
        // check the display mode; if formatted, display accordingly
        if (entryDefinition.displayMode == "formatted") {
          content = <FormattedText variant="body2">{content}</FormattedText>;
        } else {
          content = <>{content}</>;
        }
        break;
      default:
        if (entryDefinition.isRange) {
          let limits = prettyPrintedAnswers.slice(0, 2);
          // In case of invalid data (only one limit of the range is available)
          if (limits.length == 1) limits.push("");
          content = <>{ limits.join(" - ") }</>
        } else {
          content = <>{ prettyPrintedAnswers.join(", ") }</>
        }
        break;
    }
    return (
      isHidden ? null :
      <Typography variant="body2" className={classes.formPreviewQuestion} key={key}>
        {questionTitle}
        <span className={classes.formPreviewSeparator}>–</span>
        <div className={classes.formPreviewAnswer}>{content}</div>
      </Typography>
    );
  }
  else return null;
};

// Handle questions and sections differently
export function handleDisplay(entryDefinition, data, key, handleDisplayQuestion) {
    if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
      return handleDisplayQuestion(entryDefinition, data, key);
    } else if (SECTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
      // If a section is found, filter questions inside the section
      let currentSection = entryDefinition;
      if (data.questionnaire) {
        currentSection = Object.entries(data.questionnaire)
          .filter(([key, value]) => SECTION_TYPES.includes(value['jcr:primaryType'])
                               && value["@name"] == entryDefinition["@name"])[0]
        currentSection = currentSection ? currentSection[1] : "";
      }

      let currentAnswers = Object.entries(data)
        .filter(([key, value]) => value["sling:resourceType"] == "cards/AnswerSection"
                               && value["section"]["@name"] == entryDefinition["@name"])[0];
      currentAnswers = currentAnswers ? currentAnswers[1] : "";
      return Object.entries(currentSection)
        .filter(([key, value]) => QUESTION_TYPES.includes(value['jcr:primaryType']) || SECTION_TYPES.includes(value['jcr:primaryType']))
        .map(([key, entryDefinition]) => handleDisplay(entryDefinition, currentAnswers, key, handleDisplayQuestion))
  }
}
