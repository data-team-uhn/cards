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

import DateQuestionUtilities from "./DateQuestionUtilities.jsx";
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

let dateDifference = (firstDate, secondDate) => {
  // Compute the displayed difference
  let difference = null;
  if (firstDate && secondDate) {
    let currentDate = moment(firstDate);
    let nextDate = moment(secondDate);
    let divisions = ["years", "months", "days"];
    for(const division of divisions) {
      let diff = nextDate.diff(currentDate, division);
      if (diff > 0) {
        difference = diff + division.charAt(0);
        break;
      }
    }
  }
  return difference;
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
  let [dateEntries, setDateEntries] = useState(null);
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

  let getRelatedSubjects = (parent, previousLevel, names) => {
    let subjects = [];
    if (!names) {
      names = [];
    }
    let newNames = [...names];
    if (parent) {
      let newLevel = (previousLevel > -1 || parent["jcr:uuid"] === subject["jcr:uuid"]) ? previousLevel + 1 : previousLevel;
      if (parent["jcr:uuid"]) {
        if (newLevel > 0) {
          newNames.push(parent.type.label + " " + parent["identifier"]);
        }
        subjects.push({uuid: parent["jcr:uuid"], names: newNames, level: newLevel});
      }
      const childSubjects = Object.values(parent)
        .filter(value => value["jcr:primaryType"] == "lfs:Subject");
      for (const child of childSubjects) {
        subjects = subjects.concat(getRelatedSubjects(child, newLevel, newNames));
      }
    }
    return subjects;
  }

  let fetchSubject = async (path) => {
    let result = await fetchWithReLogin(globalLoginDisplay, `${path}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .catch(handleError);
    return result;
  }

  let getForms = async () => {
    let rootSubject = subject;
    if (subject.ancestors && subject.ancestors.length > 0) {
      let newPath = subject.ancestors[subject.ancestors.length - 1]["@path"];
      rootSubject = await fetchSubject(newPath);
    }

    let subjects = getRelatedSubjects(rootSubject, -1);
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
  let handleDisplayAnswer = (entryDefinition, data, key) => {
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

  let getDateAnswers = (formDetails) => {
    formDetails.map(form => {
      currentLevel = form.level;
      currentNames = form.names;
      currentFormTitle = form.response.questionnaire.title;
      Object.entries(form.response.questionnaire)
      .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
      .map(([key, entryDefinition]) => {
        handleDisplay(entryDefinition, form.response, key, handleDisplayAnswer);
      })
    });

    return dateAnswerData.sort((a, b) => {
      return a.date.value > b.date.value ? 1 : (a.date.value === b.date.value ? 0 : -1)
    });
  }

  let simplifyDateAnswer = (dateAnswer) => {
    return {
      followup: dateAnswer.followup,
      formTitle: dateAnswer.formTitle,
      level: dateAnswer.level,
      names: dateAnswer.names,
      questionPath: dateAnswer.date.question["@path"],
      questionText: dateAnswer.date.question.text,
      answerPath: dateAnswer.date["@path"]
    }
  }

  let getDateEntries = (dateAnswers) => {
    let newDateEntries = [];
    let previousDate = null;

    for (const dateAnswer of dateAnswers) {
      let diff = dateDifference(previousDate, dateAnswer.date.value);
      if (newDateEntries.length === 0 || diff) {
        newDateEntries.push({
          date: dateAnswer.date.value,
          level: dateAnswer.level,
          questions: []
        })
      } else {
        let oldLevel = newDateEntries[newDateEntries.length - 1].level;
        if (oldLevel === -1 || (dateAnswer.level !== -1 && dateAnswer.level < oldLevel)) {
          newDateEntries[newDateEntries.length - 1].level = dateAnswer.level;
        }
      }
      newDateEntries[newDateEntries.length - 1].questions.push(simplifyDateAnswer(dateAnswer));
      previousDate = dateAnswer.date.value;
    }
    setDateEntries(newDateEntries);
  }

  useEffect(() => {
    getForms().then(baseForms => getFormData(baseForms))
    .then(formDetails => getDateAnswers(formDetails))
    .then(dateAnswers => getDateEntries(dateAnswers));
  }, [subject]);

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
  };

  return <Timeline align="alternate">
    {
      dateEntries && dateEntries.map((dateEntry, index) => {
        let nextEntry = (index + 1 < dateEntries.length) ? dateEntries[index + 1] : null;
        return TimelineEntry(classes, dateEntry, index, dateEntries.length, nextEntry);
      })
    }
  </Timeline>;
}

function DateQuestionDisplay(classes, questionData, index, length, rootLevel) {
  let questionTitle = questionData.questionText;
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

  // Find the first '/' after "/Forms/" in the path
  let formPath = questionData.answerPath.substring(0, questionData.answerPath.indexOf("/", "/Forms/".length+1))
  let formTitle = `${questionData.names?.length > 0 ? questionData.names.join(" / ") + ": " : ""}${questionData.formTitle}`;
  let divClasses = [classes.timelineDateEntry];
  if (questionData.level === -1 && rootLevel !== -1) {
    divClasses.push(classes.timelineAncestor)
  }
  if (index === length - 1) {
    divClasses.push(classes.timelineDateEntryFinal)
  }

  return <div key={index} className={divClasses.join(",")}>
    <Typography variant="h6" component="h1">
      {questionTitle} (<Link href={`/content.html${formPath}#${questionData.questionPath}`}>{formTitle}</Link>)
    </Typography>
    {questionData.followup}
  </div>
}

function TimelineEntry(classes, dateEntry, index, length, nextEntry) {
  let dateText = DateQuestionUtilities.formatDateAnswer("yyyy-MM-dd", dateEntry.date);
  let diff = dateDifference(dateEntry.date, nextEntry && nextEntry.date);

  let paperClasses = [classes.timelinePaper];
  if (dateEntry.level < 0) {
    paperClasses.push(classes.timelineAncestor);
  }

  return <TimelineItem key={index}>
      <TimelineOppositeContent>
        <Typography color="textSecondary">{dateText}</Typography>
      </TimelineOppositeContent>
      <TimelineSeparator className={dateEntry.level < 0 ? classes.timelineAncestor : null}>
        <TimelineDot color={dateEntry.level == 0 ? "primary" : (dateEntry.level == 1 ? "secondary" : "grey")}/>
        {index !== (length - 1)
          ? (diff ? <CustomTimelineConnector classes={classes} text={diff}/> : <TimelineConnector />)
          : null
        }
      </TimelineSeparator>
      <TimelineContent>
        <Paper elevation={3} className={paperClasses.join(",")}>
          {dateEntry.questions.map((question, index) => {
            return DateQuestionDisplay(classes, question, index, dateEntry.questions.length, dateEntry.level)
          })}
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
