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
import {
  CircularProgress,
  Link,
  Paper,
  Tooltip
} from "@mui/material";

import {
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot,
  TimelineOppositeContent
} from "@mui/lab";

import DateTimeUtilities from "./DateTimeUtilities.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import { displayQuestion } from "./Subject.jsx";
import { ENTRY_TYPES, QUESTION_TYPES, SECTION_TYPES } from "./FormEntry.jsx"

import {
  Typography
} from "@mui/material";

import withStyles from '@mui/styles/withStyles';

const NUM_QUESTIONS = 2;
const STRIPPING_REGEX = [/^date of +/i, / +date$/i]

function DateAnswerDisplay(classes, questionData, index, length, rootLevel) {
  let questionTitle = questionData.questionText;
  // Strip unwanted strings from the question title
  for (const regexp of STRIPPING_REGEX) {
    questionTitle = questionTitle.replace(regexp, '');
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
      {questionTitle} (<Link href={`/content.html${formPath}#${questionData.questionPath}`} underline="hover">{formTitle}</Link>)
    </Typography>
    {questionData.followup}
  </div>
}

function CustomTimelineConnector(props) {
  let { classes, shortText, longText, className} = props;

  let divClasses = [classes.timelineConnectorGroup];
  if (className) {
    divClasses.push(className);
  }

  return <div className={divClasses.join(",")}>
      <Tooltip title={longText}>
        <div className={classes.timelineCircle}>
          <Typography variant="body2">{shortText}</Typography>
        </div>
      </Tooltip>
      <TimelineConnector className={classes.timelineConnectorLine}/>
    </div>
}

function TimelineEntry(classes, dateEntry, index, length, nextEntry) {
  let dateText = DateTimeUtilities.formatDateAnswer(DateTimeUtilities.VIEW_DATE_FORMAT, dateEntry.date);
  let diff = DateTimeUtilities.dateDifference(dateEntry.date, nextEntry && nextEntry.date);

  let paperClasses = [classes.timelinePaper];
  if (dateEntry.level < 0) {
    paperClasses.push(classes.timelineAncestor);
  }

  let separatorClasses = [classes.timelineSeparator];
  let connectorIsAncestor = false;
  if (dateEntry.level < 0) {
    // Grey out connector + dot
    separatorClasses.push(classes.timelineAncestor);
  } else if (nextEntry && nextEntry.level < 0) {
    // Only grey out connector
    connectorIsAncestor = true;
  }

  return <TimelineItem key={index}>
      <TimelineOppositeContent className={classes.timelineContent}>
        <Typography color="textSecondary" className={classes.timelineDate}>{dateText}</Typography>
      </TimelineOppositeContent>
      <TimelineSeparator className={separatorClasses.join(",")}>
        <TimelineDot color={dateEntry.level == 0 ? "primary" : (dateEntry.level == 1 ? "secondary" : "grey")}/>
        {index !== (length - 1)
          ? (
            diff.short
            ? <CustomTimelineConnector
              classes={classes}
              shortText={diff.short}
              longText={diff.long}
              className={connectorIsAncestor ? classes.timelineAncestor : null}/>
            : <TimelineConnector className={classes.timelineConnectorLine}/>)
          : null
        }
      </TimelineSeparator>
      <TimelineContent className={classes.timelineContent}>
        <Paper elevation={3} className={paperClasses.join(",")}>
          {dateEntry.questions.map((question, index) => {
            return DateAnswerDisplay(classes, question, index, dateEntry.questions.length, dateEntry.level)
          })}
        </Paper>
      </TimelineContent>
    </TimelineItem>;
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
  let { classes, subject } = props;
  let [dateEntries, setDateEntries] = useState(null);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  let globalLoginDisplay = useContext(GlobalLoginContext);

  // Fetch the forms and answers for a specific subject
  let fetchSubjectData = (subject, level, subjectNames) => {
    // Fetch a subject with it's forms (.data) and those forms' answers (.deep)
    // Combining .data and .deep only fetches the current subject's forms, not children's forms.
    return Promise.resolve(fetchWithReLogin(globalLoginDisplay, subject["@path"] + ".deep.data.json")
    .then(async (response) => {
      let json = await response.json();
      return response.ok ? {response: json, level: level, names: subjectNames} : Promise.reject(response)
    }));
  };

  // Recursively fetch all submitted forms for a subject and it's child subjects
  let getSubjectForms = (parent, previousLevel, parentSubjectNames) => {
    // Increment level once the user selected subject is found
    let level = previousLevel;
    if (previousLevel > -1 || parent["jcr:uuid"] === subject["jcr:uuid"]) {
      level++;
    }

    // Don't add subject names until after the user selected subject (level 0)
    let subjectNames = [...parentSubjectNames];
    if (level > 0) {
      subjectNames.push(parent.type.label + " " + parent["identifier"]);
    }

    // Fetch the current subjects data
    let promises = [fetchSubjectData(parent, level, subjectNames)];

    // Recursively get the data for the current subject's children
    for (const child of Object.values(parent)) {
      if (child["jcr:primaryType"] == "cards:Subject") {
        promises = promises.concat(getSubjectForms(child, level, subjectNames));
      }
    }

    return promises;
  }

  // Fetch a subject for a given URL
  let fetchSubject = async (path) => {
    let result = await fetchWithReLogin(globalLoginDisplay, `${path}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .catch(handleError);
    return result;
  }

  // Fetch all the submitted forms associated with subject
  let getForms = async () => {
    let rootSubject = subject;

    // If the subject has parents, fetch forms associated with the root subject instead
    if (subject.parents) {
      let newPath = subject.parents["@path"];
      rootSubject = await fetchSubject(newPath, true);
    }

    // Get forms for the subject and all it's children, recursively
    let subjectDataPromises = getSubjectForms(rootSubject, -1, [])
    let results = await Promise.all(subjectDataPromises);

    // Get all the form data into a single array
    results = results.map(formData =>
      Object.values(formData.response).filter(entry => Array.isArray(entry)).flat().filter(entry => entry["jcr:primaryType"] == "cards:Form").map(entry => {
        return {form: entry, level: formData.level, names: formData.names}
      })
    ).flat();
    return results;
  }

  let handleDisplayNodes = (entries, data, formData) => {
    let currentSectionData = [];
    let childSectionData = [];
    entries.forEach(([key, entryDefinition]) => {
      if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
        const existingQuestionAnswer = Object.entries(data)
          .find(([key, value]) => value["sling:resourceSuperType"] == "cards/Answer"
            && value["question"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);

      if (typeof(existingQuestionAnswer?.[1]?.value) != "undefined") {
        if (existingQuestionAnswer[1]["jcr:primaryType"] === "cards:DateAnswer") {
          // Push a new date answer
          currentSectionData.push({
            "date": existingQuestionAnswer[1],
            followup:[],
            formTitle: formData.title,
            level: formData.level,
            names: formData.names
          });
        } else if (currentSectionData.length > 0
          && currentSectionData[currentSectionData.length - 1].followup.length < NUM_QUESTIONS
        ) {
          // Append the non-date answer to the previous date answer,
          // if a previous date answer exists and hasn't met the followup question limit.
          currentSectionData[currentSectionData.length - 1].followup.push(displayQuestion(entryDefinition, data, key, classes));
        }
      }

      } else if (SECTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
        // If a section is found, filter questions inside the section
        let currentSection = entryDefinition;
        if (data.questionnaire) {
          currentSection = Object.entries(data.questionnaire)
            .filter(([key, value]) => SECTION_TYPES.includes(value['jcr:primaryType'])
                                  && value["@name"] == entryDefinition["@name"])[0]
          currentSection = currentSection ? currentSection[1] : "";
        }

        let currentAnswers = Object.entries(data.form ? data.form : data)
          .filter(([key, value]) => value["sling:resourceType"] == "cards/AnswerSection"
                                  && value["section"]["@name"] == entryDefinition["@name"])[0];
        currentAnswers = currentAnswers ? currentAnswers[1] : "";
        childSectionData = childSectionData.concat(handleDisplayNodes(Object.entries(currentSection), currentAnswers, formData));
      }
    })
    return currentSectionData.concat(childSectionData);
  }

  // Create a list of all date answers, with all data required for displaying these answers.
  // This includes question, form and subject data, date entered and any followup questions
  let getDateAnswers = (formData) => {
    let dateAnswerData = [];
    // For every form
    formData.forEach((formEntry, index) => {
      // For every node that has a primary type that should be traversed
      dateAnswerData = dateAnswerData.concat(
        handleDisplayNodes(
          Object.entries(formEntry.form.questionnaire).filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType'])),
          formEntry,
          {
            level: formEntry.level,
            names: formEntry.names,
            title: formEntry.form.questionnaire.title,
            dateAnswerData: dateAnswerData,
            index: index
          }
      ))
    });
    return dateAnswerData.sort((a, b) => {
      return a.date.value > b.date.value ? 1 : (a.date.value === b.date.value ? 0 : -1)
    });
  }

  // Given a fully expanded dateAnswer, strip and simplify it to the required data
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

  // Convert a list of date answers to a list of displayed groups, grouping answers by date.
  let getDateEntries = (dateAnswers) => {
    let newDateEntries = [];
    let previousDate = null;

    for (const dateAnswer of dateAnswers) {
      let diff = DateTimeUtilities.dateDifference(previousDate, dateAnswer.date.value);
      if (newDateEntries.length === 0 || diff.short) {
        // Create a new paper
        newDateEntries.push({
          date: dateAnswer.date.value,
          level: dateAnswer.level,
          questions: []
        })
      } else {
        // Data will be grouped into the previous paper: Update the paper's level if required
        let oldLevel = newDateEntries[newDateEntries.length - 1].level;
        if (oldLevel === -1 || (dateAnswer.level !== -1 && dateAnswer.level < oldLevel)) {
          newDateEntries[newDateEntries.length - 1].level = dateAnswer.level;
        }
      }
      // Add the current date answer to the current paper
      newDateEntries[newDateEntries.length - 1].questions.push(simplifyDateAnswer(dateAnswer));
      previousDate = dateAnswer.date.value;
    }
    setDateEntries(newDateEntries);
  }

  useEffect(() => {
    if (subject) {
      getForms().then(formData => getDateAnswers(formData))
      .then(dateAnswers => getDateEntries(dateAnswers));
    }
  }, [subject]);

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
  };

  if (!dateEntries) {
    return <CircularProgress/>
  }

  return ( dateEntries?.length ?
    <div className={classes.timelineContainer}><Timeline position="alternate" className={classes.timeline}>
    {
      dateEntries.map((dateEntry, index) => {
        let nextEntry = (index + 1 < dateEntries.length) ? dateEntries[index + 1] : null;
        return TimelineEntry(classes, dateEntry, index, dateEntries.length, nextEntry);
      })
    }
    </Timeline></div>
    :
    <Typography color="textSecondary" variant="caption">No timeline data available</Typography>
  )
}

SubjectTimeline.propTypes = {
  id: PropTypes.string
}

SubjectTimeline.defaultProps = {
  maxDisplayed: 4,
}

export default withStyles(QuestionnaireStyle)(SubjectTimeline);
