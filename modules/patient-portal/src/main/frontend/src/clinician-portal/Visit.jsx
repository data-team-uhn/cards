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

import { withRouter, useHistory } from "react-router-dom";

import {
  Alert,
  AlertTitle,
  Avatar,
  CircularProgress,
  Grid,
  List,
  ListItem,
  ListItemAvatar,
  ListItemButton,
  ListItemText,
} from "@mui/material";

import DoneIcon from '@mui/icons-material/Done';
import WarningIcon from '@mui/icons-material/Warning';
import SurveyIcon from '@mui/icons-material/Assignment';
import EventNoteIcon from '@mui/icons-material/EventNote';

import makeStyles from '@mui/styles/makeStyles';
import withStyles from '@mui/styles/withStyles';

import { DateTime } from "luxon";

import SurveyLinkButton from "./SurveyLinkButton";
import EditButton from "../dataHomepage/EditButton";
import PrintButton from "../dataHomepage/PrintButton";
import ResourceHeader from "../questionnaire/ResourceHeader";
import { getHierarchyAsList, getTextHierarchy } from "../questionnaire/SubjectIdentifier";
import DateQuestionUtilities from "../questionnaire/DateQuestionUtilities";
import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "../questionnaire/QuestionnaireStyle";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

const useStyles = makeStyles(theme => ({
  stepIndicator : {
    border: "1px solid " + theme.palette.action.disabled,
    background: "transparent",
    color: theme.palette.text.disabled,
    fontSize: "small",
    fontWeight: "bold",
  },
  incompleteIndicator : {
    border: "1px solid " + theme.palette.error.main,
    background: "transparent",
    color: theme.palette.error.main,
  },
  doneIndicator : {
    border: "1px solid " + theme.palette.success.main,
    background: "transparent",
    color: theme.palette.success.main,
  }
}));

function Visit(props) {
  const { classes } = props;

  const patientUuid = props.match.params.patientId;
  const visitUuid = props.match.params.visitId;

  // Identifier of the questionnaire set used for the visit
  const [ questionnaireSetId, setQuestionnaireSetId ] = useState();
  // Map questionnaire id -> title, path and optional time estimate (in minutes) for filling it out
  const [ questionnaires, setQuestionnaires ] = useState();
  // The ids of the questionnaires in this set, in the order they must be filled in
  const [ questionnaireSetIds, setQuestionnaireSetIds ] = useState();
  // The ids of the questionnaires displayed to the patient
  const [ questionnaireIds, setQuestionnaireIds ] = useState();
  // Data already associated with the subject
  const [ subjectData, setSubjectData ] = useState();
  // The visit subject identifier (which coincides with the visit number)
  const [ visitNumber, setVisitNumber ] = useState();
  // The visit subject node path
  const [ visitPath, setVisitPath ] = useState();
  // The parent nodes of the visit subject (expected: one parent, a patient subject)
  const [ parents, setParents ] = useState();
  // When something goes wrong:
  const [ error, setError ] = useState("");
  // Visit information form
  const [ visitInformation, setVisitInformation ] = useState();

  const VISIT_INFORMATION_FORM_TITLE = "Visit information";

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const history = useHistory();

  const styles = useStyles();

  // --------------------------------------------------------------------------------------------------------------------------
  // Loading and parsing:

  const loadExistingData = () => {
    fetchWithReLogin(globalLoginDisplay, `/Subjects/${patientUuid}/${visitUuid}.data.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        if (!questionnaires) {
          setSubjectData(json);
          setVisitNumber(json["identifier"]);
          setVisitPath(json["@path"]);
          setParents(json["parents"]);
          setVisitInformation(json[VISIT_INFORMATION_FORM_TITLE]?.[0] || {});
          let clinicPath = Object.values(json[VISIT_INFORMATION_FORM_TITLE]?.[0]).find(o => o?.question?.["@name"] == "clinic")?.value;
          return fetchWithReLogin(globalLoginDisplay, `${clinicPath}.deep.json`)
            .then((response) => response.ok ? response.json() : Promise.reject(response))
            .then((json) => {
              setQuestionnaireSetId(json["survey"]);
            });
        }
        selectDataForQuestionnaireSet(json, questionnaires, questionnaireSetIds);
      })
      .catch(() => setError("The survey data could not be loaded for this visit. Please try again later or contact the administrator for further assistance."));
  }

  const loadQuestionnaireSet = () => {
    if (!!!questionnaireSetId) {
      return;
    }
    fetchWithReLogin(globalLoginDisplay, `/Survey/${questionnaireSetId}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        parseQuestionnaireSet(json);
      })
      .catch((response) => {
        if (response.status == 404) {
          setError("The survey you are trying to access does not exist. Please contact the administrator for further assistance.");
        } else {
          //setError("The survey could not be loaded at this time. Please try again later or contact the administrator for further assistance.");
        }
        //setQuestionnaires(null);
      });
  }

  const parseQuestionnaireSet = (json) => {
    // Map the relevant questionnaire info
    let data = {};
    Object.entries(json || {})
      .filter(([key, value]) => value['jcr:primaryType'] == 'cards:QuestionnaireRef')
      .forEach(([key, value]) => {
        data[value.questionnaire['@name']] = {
          'title': value.questionnaire?.title || key,
          'alias': key,
          '@path': value.questionnaire?.['@path'],
        }
       });
    setQuestionnaires(data);

    let qids = Object.values(json || {})
      .filter(value => value['jcr:primaryType'] == 'cards:QuestionnaireRef')
      .sort((a, b) => (a.order - b.order))
      .map(value => value.questionnaire['@name'])
    setQuestionnaireSetIds(qids);

    selectDataForQuestionnaireSet(subjectData, data, qids);
  };

  const selectDataForQuestionnaireSet = (subjectData, questionnaireSet, questionnaireSetIds) => {
    let ids = [];
    let data = {};
    questionnaireSetIds.forEach(q => {
      if (subjectData[questionnaireSet?.[q]?.title]?.[0]?.['jcr:primaryType'] == "cards:Form") {
        data[q] = subjectData[questionnaireSet?.[q]?.title][0];
        ids.push(q);
      }
    });
    !questionnaireIds && setQuestionnaireIds(ids);
    setSubjectData(data);
  };

  // -----------------------------------------------------------------------------------------------------------

  // First, load the existing visit data to determine which questionnaire set is bound to the visit
  useEffect(loadExistingData, []);

  // After the visit is loaded and we know the questionnaire set identifier, load all questionnaires that need to be filled out
  useEffect(loadQuestionnaireSet, [questionnaireSetId]);


  // --------------------------------------------------------------------------------------------------------------
  // Message screens are displayed if the data isn't loaded yet or if there's an error

  const displayMessageScreen = (message, type, icon) => (
    <Grid container {...FORM_ENTRY_CONTAINER_PROPS}>
      <Grid item>
        <Alert severity={type} icon={icon}>{message}</Alert>
      </Grid>
    </Grid>
  );

  if (error) {
    return displayMessageScreen(error, "error");
  }

  if (!questionnaireSetId || !questionnaireIds || !questionnaires || !subjectData) {
    return displayMessageScreen(
      <AlertTitle>Loading...</AlertTitle>,
      "info",
      <CircularProgress size={24} />
    );
  }

  // -----------------------------------------------------------------------------------------------------------
  // Visit information - extract and format relevant info from the Visit information form associated with this visit

  const getVisitField = (qName) => {
    let question = visitInformation?.questionnaire?.[qName]?.["jcr:uuid"];
    let answer = Object.values(visitInformation).find(value => value.question?.["jcr:uuid"] == question)?.value || null;
    return answer;
  }

  const displayVisitDateTime = () => {
    let dateTimeAnswer = getVisitField("time");
    let dateTime = DateQuestionUtilities.toPrecision(DateQuestionUtilities.stripTimeZone(dateTimeAnswer));
    return !dateTime?.isValid ? "" : dateTime.toLocaleString(DateTime.DATETIME_MED_WITH_WEEKDAY);
  }

  const displayVisitInfo = () => {
    const dateTime = displayVisitDateTime();
    let location = getVisitField("location");
    let provider = getVisitField("provider");
    provider = provider && provider.length > 1 ? provider.join(", ") : provider;
    return (dateTime || location || provider) ?
      <Alert severity="info" icon={<EventNoteIcon/>}>
        {dateTime ? <> {dateTime} </> : null}
        {location ? <> at {location}</> : null}
        {provider ? <> with {provider}</> : null}
      </Alert>
      : null
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Prepare the display of the survey list, including the status of each survey

  const surveyIndicator = <Avatar className={styles.stepIndicator}><SurveyIcon /></Avatar>;
  const doneIndicator = <Avatar className={styles.doneIndicator}><DoneIcon /></Avatar>;
  const incompleteIndicator = <Avatar className={styles.incompleteIndicator}><WarningIcon /></Avatar>;

  const isFormComplete = (questionnaireId) => {
    return subjectData?.[questionnaireId] && !subjectData[questionnaireId].statusFlags?.includes("INCOMPLETE");
  }

  const isFormSubmitted = (questionnaireId) => {
    return subjectData?.[questionnaireId]?.statusFlags?.includes("SUBMITTED");
  }

  const isFormLocked = (questionnaireId) => {
    return subjectData?.[questionnaireId]?.statusFlags?.includes("LOCKED");
  }

  const displayFlags = q => (
    (subjectData?.[q]?.statusFlags ?? [])
      .filter(f => ["INCOMPLETE", "SUBMITTED", "LOCKED"].includes(f))
      .map(f => f.substring(0,1).toUpperCase() + f.substring(1).toLowerCase())
      .join(", ")
  );

  // -----------------------------------------------------------------------------------------------------------------
  // Render the visit:
  // * Resource header (sticky to the top) with a simplified menu
  // * Visit information
  // * List of firms for this visit, as specified by the associated QuestionnaireSet

  return (
    <Grid container {...FORM_ENTRY_CONTAINER_PROPS}>
      <ResourceHeader
        title={`Visit ${visitNumber}`}
        breadcrumbs={(parents && getHierarchyAsList(parents, true) || "")}
        action={
          <div className={classes.actionsMenu}>
            <SurveyLinkButton visitURL={`/Subjects/${patientUuid}/${visitUuid}`} />
            <PrintButton
              resourcePath={visitPath}
              resourceData={subjectData}
              breadcrumb={getTextHierarchy(subjectData, true)}
              date={DateTime.fromISO(subjectData['jcr:created']).toLocaleString(DateTime.DATE_MED)}
            />
          </div>
        }
      />
      <Grid item>{ displayVisitInfo() }</Grid>
      <Grid item>
        <List>
        { (questionnaireIds || []).map((q, i) => (
          <ListItem
            key={q}
            disablePadding
            secondaryAction={!isFormSubmitted(q) && !isFormLocked(q) && <EditButton entryPath={subjectData?.[q]?.["@path"]}/>}
          >
            <ListItemButton onClick={() => history.push(`/content.html${subjectData?.[q]?.["@path"]}`)}>
              <ListItemAvatar>{isFormComplete(q) ? doneIndicator : (isFormSubmitted(q) ? incompleteIndicator : surveyIndicator)}</ListItemAvatar>
              <ListItemText primary={questionnaires[q]?.title} secondary={displayFlags(q)} />
            </ListItemButton>
          </ListItem>
        ))}
        </List>
      </Grid>
    </Grid>
  );
}

export default withStyles(QuestionnaireStyle)(withRouter(Visit));
