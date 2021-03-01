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

import React, { useState, useContext, useEffect } from "react";
import PropTypes from "prop-types";
import moment from "moment";
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

function CustomTimelineConnector(props) {
  let { classes, text } = props;
  return <React.Fragment>
      <div className={classes.circle}>
        <Typography variant="body2">{text}</Typography>
      </div>
      <TimelineConnector />
    </React.Fragment>
}

/**
 * Component that displays a Subject's Timeline Chart.
 *
 * @example
 * <Subject id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a subject; this is the JCR node name
 */
function SubjectTimeline(props) {
  let { id, classes, pageSize, subject } = props;
  let [dateAnswers, setDateAnswers] = useState(null);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

  let fetchFormData = (path, level, names) => {
    return fetchWithReLogin(globalLoginDisplay, `${path}.deep.json`)
      .then(async (response) => {
        let json = await response.json();
        return response.ok ? {response: json, level: level, names: names} : Promise.reject(response)
      })
      // .catch(handleFormError)
  }

  let getFormData = async (baseForms) => {
    let formDataPromises = [];
    for (const form of baseForms) {
      formDataPromises.push(fetchFormData(form.row["@path"], form.level, form.names));
    }
    let results = await Promise.all(formDataPromises);
    return results;
  }

  let fetchForms = (customUrl, level, names) => {
    return Promise.resolve(fetchWithReLogin(globalLoginDisplay, customUrl)
    .then(async (response) => {
      let json = await response.json();
      return response.ok ? {response: json, level: level, names: names} : Promise.reject(response)
    }));
    // .catch(handleTableError);
  };

  let getSubjects = (parent, level, names) => {
    let subjects = [];
    if (!names) {
      names = [];
    }
    let newNames = [...names];
    if (parent) {
      if (parent["jcr:uuid"]) {
        if (level > 0) {
          newNames.push(parent["identifier"]);
        }
        subjects.push({uuid: parent["jcr:uuid"], names: newNames, level: level});
      }
      for (const child of parent["@progeny"] || []) {
        subjects = subjects.concat(getSubjects(child, level+1, newNames));
      }
    }
    return subjects;
  }

  let getForms = async () => {
    let rootSubject = subject;
    // TODO: convert rootSubject to top level parent
    // Will be made easy by LFS-911

    let subjects = getSubjects(rootSubject, 0);
    let formPromises = [];

    for (const subject of subjects) {
      const formUrl = `/Forms.paginate?fieldname=subject&fieldvalue=${encodeURIComponent(subject.uuid)}&includeallstatus=true&limit=1000`;
      formPromises.push(fetchForms(formUrl, subject.level, subject.names));
    }
    let results = await Promise.all(formPromises);
    results = [].concat.apply([], results.map(formData => formData.response.rows.map(row => {return {row: row, level: formData.level, names: formData.names}})));
    return results;
  }

  let dateAnswerData = [];
  let currentFormTitle;
  let currentLevel;
  let currentNames;
  let handleDisplayQuestion = (entryDefinition, data, key) => {
    const existingQuestionAnswer = data && Object.entries(data)
      .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
        && value["question"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);
    if (typeof(existingQuestionAnswer?.[1]?.value) != "undefined") {
      if (existingQuestionAnswer[1]["jcr:primaryType"] === "lfs:DateAnswer") {
        dateAnswerData.push({"date": existingQuestionAnswer[1], followup:[], formTitle: currentFormTitle, level: currentLevel, names: currentNames});
      } else if (dateAnswerData.length > 0 && dateAnswerData[dateAnswerData.length - 1].followup.length < NUM_QUESTIONS) {
        dateAnswerData[dateAnswerData.length - 1].followup.push(displayQuestion(entryDefinition, data, key));
      }
    }
  }

  let getDateQuestions = (formDetails) => {
    formDetails.map(form => {
      currentLevel = form.level;
      currentNames = form.names;
      currentFormTitle = form.response.questionnaire["@name"];
      Object.entries(form.response.questionnaire)
      .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
      .map(([key, entryDefinition]) => {
        handleDisplay(entryDefinition, form.response, key, handleDisplayQuestion);
      })
    });
    setDateAnswers(dateAnswerData.sort((a, b) => {
      return a.date.value > b.date.value ? 1 : (a.date.value === b.date.value ? 0 : -1)
    }));
  }

  useEffect(() => {
    console.log("SubjectTimeline");
    console.log(subject);
    getForms().then(baseForms => getFormData(baseForms))
    .then(formDetails => getDateQuestions(formDetails));
  }, [subject]);

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
  };

  let nextDate;
  return <Timeline align="alternate">
    {
      dateAnswers && dateAnswers.map((dateAnswer, index) => {
        nextDate = (index + 1 < dateAnswers.length) ? dateAnswers[index + 1] : null;
        return DateTimelineEntry(classes, dateAnswer, index, dateAnswers.length, nextDate);
      })
    }
  </Timeline>;
}

function DateTimelineEntry(classes, data, index, length, nextDateAnswer) {
  let dateAnswer = data.date;
  let dateText = formatDateAnswer(dateAnswer.question?.dateFormat, dateAnswer.value);
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

  // Compute the displayed difference
  // TODO: Move to DateQuestionUtilities after rebase
  let difference = null;
  if (nextDateAnswer?.date?.value) {
    let currentDate = moment(dateAnswer.value);
    let nextDate = moment(nextDateAnswer?.date?.value);
    let divisions = ["years", "months", "days"];
    for(const division of divisions) {
      let diff = nextDate.diff(currentDate, division);
      if (diff > 0) {
        console.log(division);
        difference = diff + division.charAt(0);
        break;
      }
    }
  }

  let questionPath = dateAnswer && dateAnswer.question && dateAnswer.question["@path"];
  let formIndex = dateAnswer && dateAnswer["@path"].indexOf("/", "/Forms/".length+1);
  let formPath = dateAnswer && dateAnswer["@path"].substring(0, formIndex)
  let formTitle = `${data.names?.length > 0 ? data.names.join(" / ") + ": " : ""}${data.formTitle}`;

  return <TimelineItem key={index}>
      <TimelineOppositeContent>
        <Typography color="textSecondary">{dateText}</Typography>
      </TimelineOppositeContent>
      <TimelineSeparator>
        <TimelineDot color={data.level == 0 ? "primary" : (data.level == 1 ? "secondary" : "default")}/>
        {index !== (length - 1) && (difference ? <CustomTimelineConnector classes={classes} text={difference}/> : <TimelineConnector />)}
      </TimelineSeparator>
        <TimelineContent>
          <Paper elevation={3} className={classes.timelinePaper}>
            <Typography variant="h6" component="h1">
              {questionTitle} (<Link href={`/content.html${formPath}#${questionPath}`}>{formTitle}</Link>)
            </Typography>
            {data.followup}
          </Paper>
        </TimelineContent>
    </TimelineItem>;
}

SubjectTimeline.propTypes = {
  id: PropTypes.string
}

SubjectTimeline.defaultProps = {
  maxDisplayed: 4,
  pageSize: 10,
}

export default withStyles(QuestionnaireStyle)(SubjectTimeline);
