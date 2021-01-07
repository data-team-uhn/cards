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

import React, { useState, useContext } from "react";
import PropTypes from "prop-types";
import { Link, Paper } from '@material-ui/core'
import Timeline from '@material-ui/lab/Timeline';
import TimelineItem from '@material-ui/lab/TimelineItem';
import TimelineSeparator from '@material-ui/lab/TimelineSeparator';
import TimelineConnector from '@material-ui/lab/TimelineConnector';
import TimelineContent from '@material-ui/lab/TimelineContent';
import TimelineDot from '@material-ui/lab/TimelineDot';
import TimelineOppositeContent from '@material-ui/lab/TimelineOppositeContent';

import { formatDateAnswer } from "./DateQuestion.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import { displayQuestion, handleDisplay, ENTRY_TYPES } from "./Subject.jsx";

import {
  Typography,
  withStyles,
} from "@material-ui/core";

const NUM_QUESTIONS = 2;
const STRIPPED_STRINGS = ["date of", "date"]

/**
 * Component that displays a Subject's Patient Chart.
 *
 * @example
 * <Subject id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a subject; this is the JCR node name
 */

function PatientTimeline(props) {
  let { id, classes, pageSize, subject } = props;
  let [dateAnswers, setDateAnswers] = useState(null);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

  let fetchFormData = (path) => {
    return fetchWithReLogin(globalLoginDisplay, `${path}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      // .catch(handleFormError)
  }

  let getFormData = async (baseForms) => {
    let formDataPromises = [];
    for (const form of baseForms) {
      formDataPromises.push(fetchFormData(form["@path"]));
    }
    let results = await Promise.all(formDataPromises);
    console.log("getFormData");
    console.log(results);
    return results;
  }

  let fetchForms = (customUrl) => {
    return fetchWithReLogin(globalLoginDisplay, customUrl)
    .then((response) => response.ok ? response.json() : Promise.reject(response));
    // .catch(handleTableError);
  };

  let getSubjects = (parent) => {
    let subjects = [];
    console.log("Getting subjects");
    console.log(parent);
    console.log(parent["@progeny"]);
    if (parent) {
      if (parent["jcr:uuid"]) {
        subjects.push(parent["jcr:uuid"]);
      }
      for (const child of parent["@progeny"] || []) {
        subjects = subjects.concat(getSubjects(child));
      }
    }
    return subjects;
  }

  let getForms = async () => {
    let rootSubject = subject;
    // TODO: convert rootSubject to top level parent
    // TODO: add subject hierarchy to questionnaire title
    // Will be made easy by LFS-911

    let subjects = getSubjects(rootSubject);
    console.log("getSubjects");
    console.log(subjects);
    let formPromises = [];

    for (const subjectId of subjects) {
      const formUrl = `/Forms.paginate?fieldname=subject&fieldvalue=${encodeURIComponent(subjectId)}&includeallstatus=true&limit=1000`;
      formPromises.push(fetchForms(formUrl));
    }
    let results = await Promise.all(formPromises);
    results = [].concat.apply([], results.map(formData => formData.rows))
    console.log("getForms");
    console.log(results);
    return results;
  }

  let dateAnswerData = [];
  let currentFormTitle;
  let handleDisplayQuestion = (entryDefinition, data, key) => {
    const existingQuestionAnswer = data && Object.entries(data)
      .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
        && value["question"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);
    if (typeof(existingQuestionAnswer?.[1]?.value) != "undefined") {
      if (existingQuestionAnswer[1]["jcr:primaryType"] === "lfs:DateAnswer") {
        dateAnswerData.push({"date": existingQuestionAnswer[1], followup:[], formTitle: currentFormTitle});
      } else if (dateAnswerData.length > 0 && dateAnswerData[dateAnswerData.length - 1].followup.length < NUM_QUESTIONS) {
        dateAnswerData[dateAnswerData.length - 1].followup.push(displayQuestion(entryDefinition, data, key));
      }
    }
  }

  let getDateQuestions = (formDetails) => {
    formDetails.map(form => {
      currentFormTitle = form.questionnaire["@name"];
      Object.entries(form.questionnaire)
      .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
      .map(([key, entryDefinition]) => {
        handleDisplay(entryDefinition, form, key, handleDisplayQuestion);
      })
    });
    setDateAnswers(dateAnswerData.sort((a, b) => {
      return a.date.value > b.date.value ? 1 : (a.date.value === b.date.value ? 0 : -1)
    }));
  }

  if (dateAnswers === null) {
    setDateAnswers(false);
    getForms().then(baseForms => getFormData(baseForms))
    .then(formDetails => getDateQuestions(formDetails));
  }

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
  };

  return <Timeline align="alternate">
    { dateAnswers && dateAnswers.map((dateAnswer, index) => DateTimelineEntry(classes, dateAnswer, index, dateAnswers.length)) }
  </Timeline>;
}

function DateTimelineEntry(classes, data, index, length) {
  let dateAnswer = data.date;
  let date = formatDateAnswer(dateAnswer.question?.dateFormat, dateAnswer.value);
  let questionTitle = dateAnswer.question?.text;

  // Strip unwanted strings from the question title
  for (const str of STRIPPED_STRINGS) {
    let dateIndex = questionTitle.toLowerCase().indexOf(str);
    if (dateIndex > -1) {
      questionTitle = questionTitle.substring(0,dateIndex).concat(questionTitle.substring(dateIndex + str.length));
    }
  }
  questionTitle = questionTitle.trim();
  // Make sure the title starts with a capital
  if (questionTitle.length > 0) {
    questionTitle = questionTitle[0].toUpperCase().concat(questionTitle.substring(1));
  }

  let questionPath = dateAnswer && dateAnswer.question && dateAnswer.question["@path"];
  let formIndex = dateAnswer && dateAnswer["@path"].indexOf("/", "/Forms/".length+1);
  let formPath = dateAnswer && dateAnswer["@path"].substring(0, formIndex)

  // TODO: Implement level. Will be made easier when 911 is complete
  let level = 0;
  let formTitle = data.formTitle;

  return <TimelineItem key={index}>
      <TimelineOppositeContent>
        <Typography color="textSecondary">{date}</Typography>
      </TimelineOppositeContent>
      <TimelineSeparator>
        <TimelineDot color={level == 0 ? "primary" : (level == 1 ? "secondary" : "default")}/>
        {index !== (length - 1) && <TimelineConnector />}
      </TimelineSeparator>
        <TimelineContent>
          <Paper elevation={3} className={classes.timelinePaper}>
            <Typography variant="h6" component="h1">
              {questionTitle} (<Link href={`/content.html${formPath}#${questionPath}`}>{formTitle}</Link>)
            </Typography>
            {/* TODO: fix entered on displaying wrong date */}
            <Typography variant="caption">Entered on {formatDateAnswer("yyyy-mm-DD", dateAnswer["jcr:created"])} by {dateAnswer["jcr:createdBy"]}</Typography>
            {data.followup}
          </Paper>
        </TimelineContent>
    </TimelineItem>;
}

PatientTimeline.propTypes = {
  id: PropTypes.string
}

PatientTimeline.defaultProps = {
  maxDisplayed: 4,
  pageSize: 10,
}

export default withStyles(QuestionnaireStyle)(PatientTimeline);
