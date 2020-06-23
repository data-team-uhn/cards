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
import PropTypes from "prop-types";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";
import AnswerComponentManager from "./AnswerComponentManager";
import moment from "moment";

// Component to display the name of the file stored under the answer, along with the creator and creation date.

function SomaticVariantsQuestion(props) {
  let { existingAnswer, classes, ...rest } = props;
  let file = existingAnswer[1][Object.keys(existingAnswer[1]).find(key => existingAnswer[1][key]["jcr:primaryType"] === "nt:file")];

  return (
    <Question
      {...rest}
      >
      { file && file['jcr:createdBy'] && file['jcr:created'] ?
        <Typography variant="body1" className={classes.fileInfo}>
          File <IconButton size="small" color="primary"><a href={file["@path"]} download><GetApp /></a></IconButton><a href={file["@path"]} download>{file["@name"]}</a> uploaded by {file["jcr:createdBy"]} on {moment(file["jcr:created"]).format("dddd, MMMM Do YYYY")}
        </Typography>
        : "" }
    </Question>);
}

SomaticVariantsQuestion.propTypes = {
  classes: PropTypes.object.isRequired
};

const StyledSomaticVariantsQuestion = withStyles(QuestionnaireStyle)(SomaticVariantsQuestion)
export default StyledSomaticVariantsQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "somaticVariantsFile") {
    return [StyledSomaticVariantsQuestion, 50];
  }
});
