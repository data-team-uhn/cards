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
import { IconButton, Typography, withStyles } from "@material-ui/core";
import GetApp from '@material-ui/icons/GetApp';
import { Link } from "react-router-dom";
import PropTypes from "prop-types";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";
import AnswerComponentManager from "./AnswerComponentManager";
import moment from "moment";

// Component to display the name of the file stored under the answer, along with the creator and creation date.

function SomaticVariantsQuestion(props) {
  let { existingAnswer, isEdit, classes, ...rest } = props;
  let file = existingAnswer?.[1][Object.keys(existingAnswer[1]).find(key => existingAnswer[1][key]["jcr:primaryType"] === "nt:file")];

  // If the form is in the view mode
  if (existingAnswer?.[1]["displayedValue"] && !isEdit) {
    let prettyPrintedAnswers = existingAnswer[1]["displayedValue"];
    // The value can either be a single value or an array of values; force it into an array
    prettyPrintedAnswers = Array.of(prettyPrintedAnswers).flat();

    return (
      <Question
        {...rest}
        >
        {prettyPrintedAnswers.map((answerValue, idx) => {
          let prefix = idx > 0 ? ", " : ""; // Seperator space between different files
          return <>{prefix}<a key={answerValue} href={existingAnswer[1]["value"][idx]} target="_blank" rel="noopener" download>{answerValue}</a></>
        })}
      </Question>
    );
  }

  return (
    <Question
      {...rest}
      >
      { file && file['jcr:createdBy'] && file['jcr:created'] ?
        <Typography variant="body1" className={classes.fileInfo}>
          File <IconButton size="small" color="primary"><a href={file["@path"]} download><GetApp /></a></IconButton><a href={file["@path"]} download>{file["@name"]}</a> uploaded by {file["jcr:createdBy"]} on {moment(file["jcr:created"]).format("dddd, MMMM Do YYYY")}
        </Typography>
        :
        <Typography variant="caption" color="textSecondary">
          You can upload variants through the <Link to="/content.html/Variants">Variant File Uploader</Link>.
        </Typography>
        }
    </Question>);
}

SomaticVariantsQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  isEdit: PropTypes.bool,
};

const StyledSomaticVariantsQuestion = withStyles(QuestionnaireStyle)(SomaticVariantsQuestion)
export default StyledSomaticVariantsQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "somaticVariantsFile") {
    return [StyledSomaticVariantsQuestion, 50];
  }
});
