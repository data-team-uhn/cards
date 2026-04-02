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
import { useState, useEffect, useContext } from "react";

import SurveyIcon from '@mui/icons-material/Assignment';
import DoneIcon from '@mui/icons-material/Done';
import EventNoteIcon from '@mui/icons-material/EventNote';
import LockIcon from '@mui/icons-material/Lock';
import WarningIcon from '@mui/icons-material/Warning';
import {
  Alert,
  AlertTitle,
  Avatar,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  List,
  ListItem,
  ListItemAvatar,
  ListItemButton,
  ListItemText,
  Typography,
} from "@mui/material";
import { DateTime } from "luxon";
import { useNavigate } from "react-router";
import { makeStyles } from 'tss-react/mui';

import SurveyLinkButton from "./SurveyLinkButton";
import FormattedText from "../components/FormattedText";
import EditButton from "../dataHomepage/EditButton";
import PrintButton from "../dataHomepage/PrintButton";
import SubjectLockAction from "../locking/SubjectLockAction";
import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";
import actionMenuStyles from "../questionnaire/actionMenuStyles.jsx";
import { FORM_ENTRY_CONTAINER_PROPS } from "../questionnaire/questionnaireConstants.jsx";
import ResourceHeader from "../questionnaire/ResourceHeader";
import statusFlagStyles from "../questionnaire/statusFlagStyles.jsx";
import { getSubjectIdFromPath, getHierarchyAsList, getTextHierarchy } from "../questionnaire/SubjectIdentifier";

const useStyles = makeStyles()(theme => ({
  ...actionMenuStyles(theme),
  ...statusFlagStyles(theme),
  formItem: {
    "& .MuiListItemAvatar-root" : {
      marginTop: 6,
      zoom: 1,
    },
  },
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
  const id = getSubjectIdFromPath(location.pathname);
  const match = /^([^/]+)\/([^/]+)$/.exec(id);
  const patientUuid = match?.[1];
  const visitUuid = match?.[2];

  // Identifier of the questionnaire set used for the visit
  const [ questionnaireSetId, setQuestionnaireSetId ] = useState();
  // Map questionnaire id -> title, path and optional time estimate (in minutes) for filling it out
  const [ questionnaires, setQuestionnaires ] = useState();
  // The ids of the questionnaires in this set, in the order they must be filled in
  const [ questionnaireSetIds, setQuestionnaireSetIds ] = useState();
  // The ids of the questionnaires displayed to the patient
  const [ questionnaireIds, setQuestionnaireIds ] = useState();
  // All data already associated with this visit
  const [ visit, setVisit ] = useState();
  // Survey data already associated with the subject
  const [ surveyData, setSurveyData ] = useState();
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
  // If the current visit is locked
  const [ isLocked, setLocked ] = useState(false);

  const VISIT_INFORMATION_FORM_TITLE = "Visit information";

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const navigate = useNavigate();

  const { classes } = useStyles();

  // --------------------------------------------------------------------------------------------------------------------------
  // Loading and parsing:

  const loadExistingData = () => {
    fetchWithReLogin(globalLoginDisplay, `/Subjects/${patientUuid}/${visitUuid}.data.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setVisit(json);
        if (!questionnaires) {
          setVisitNumber(json["identifier"]);
          setVisitPath(json["@path"]);
          setParents(json["parents"]);
          setVisitInformation(json[VISIT_INFORMATION_FORM_TITLE]?.[0] || {});
          let clinicPath = json[VISIT_INFORMATION_FORM_TITLE]?.[0]?.clinic;
          if (!clinicPath) {
            setError("Clinic is missing for this visit.");
            return;
          } else {
            return fetchWithReLogin(globalLoginDisplay, `${clinicPath}.deep.json`)
              .then((response) => response.ok ? response.json() : Promise.reject(response))
              .then((json) => {
                setQuestionnaireSetId(json["survey"]);
              });
          }
        }
        selectDataForQuestionnaireSet(questionnaires, questionnaireSetIds);
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
          setError("The survey could not be loaded at this time. Please try again later or contact the administrator for further assistance.");
        }
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
          'targetUserType': value.targetUserType,
          '@path': value.questionnaire?.['@path'],
        }
      });
    setQuestionnaires(data);

    let qids = Object.values(json || {})
      .filter(value => value['jcr:primaryType'] == 'cards:QuestionnaireRef')
      .sort((a, b) => (a.order - b.order))
      .map(value => value.questionnaire['@name'])
    setQuestionnaireSetIds(qids);

    selectDataForQuestionnaireSet(data, qids);
  };

  const selectDataForQuestionnaireSet = (questionnaireSet, questionnaireSetIds) => {
    let ids = [];
    let data = {};
    questionnaireSetIds.forEach(q => {
      if (visit?.[questionnaireSet?.[q]?.title]?.[0]?.['jcr:primaryType'] == "cards:Form") {
        data[q] = {
          ...visit[questionnaireSet?.[q]?.title][0],
          targetUserType: questionnaireSet?.[q]?.targetUserType
        };
        ids.push(q);
      }
    });
    !questionnaireIds && setQuestionnaireIds(ids);
    setSurveyData(data);
  };

  // -----------------------------------------------------------------------------------------------------------

  // First, load the existing visit data to determine which questionnaire set is bound to the visit
  useEffect(loadExistingData, []);

  // After the visit is loaded and we know the questionnaire set identifier, load all questionnaires that need to be filled out
  useEffect(loadQuestionnaireSet, [questionnaireSetId]);

  // When a visit is loaded, record if it is locked
  useEffect(() => {
    setLocked(visit?.statusFlags && visit.statusFlags.includes("LOCKED"))
  }, [visit]);


  // --------------------------------------------------------------------------------------------------------------
  // Message screens are displayed if the data isn't loaded yet or if there's an error

  const displayMessageScreen = (message, type, icon) => (
    <Grid container {...FORM_ENTRY_CONTAINER_PROPS}>
      <Grid>
        <Alert severity={type} icon={icon}>{message}</Alert>
      </Grid>
    </Grid>
  );

  if (error) {
    return displayMessageScreen(error, "error");
  }

  if (!questionnaireSetId || !questionnaireIds || !questionnaires || !visit) {
    return displayMessageScreen(
      <AlertTitle>Loading...</AlertTitle>,
      "info",
      <CircularProgress size={24} />
    );
  }

  // -----------------------------------------------------------------------------------------------------------
  // Visit information - extract and format relevant info from the Visit information form associated with this visit

  const getVisitField = (qName) => {
    return visitInformation?.[qName];
  }

  const displayVisitDateTime = () => {
    let dateTimeAnswer = getVisitField("time");
    if (dateTimeAnswer == null) return null;
    const dt = DateTime.fromISO(dateTimeAnswer)
    return dt.isValid
      ? dt.toLocaleString(DateTime.DATETIME_MED_WITH_WEEKDAY)
      : null;
  }

  const displayVisitInfo = () => {
    const dateTime = displayVisitDateTime();
    let location = getVisitField("location");
    let provider = getVisitField("provider");
    provider = provider && provider.length > 1 ? provider.join(", ") : provider;
    return (dateTime || location || provider) ?
      <Alert variant="outlined" severity="info" icon={<EventNoteIcon/>} sx={{ mt: -2 }}>
        <strong>
          {dateTime ? <> {dateTime} </> : null}
          {location ? <> at {location}</> : null}
          {provider ? <> with {provider}</> : null}
        </strong>
      </Alert>
      : null
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Prepare the display of the survey list, including the status of each survey

  const surveyIndicator = <Avatar className={classes.stepIndicator}><SurveyIcon /></Avatar>;
  const doneIndicator = <Avatar className={classes.doneIndicator}><DoneIcon /></Avatar>;
  const incompleteIndicator = <Avatar className={classes.incompleteIndicator}><WarningIcon /></Avatar>;
  const lockedIndicator = <Avatar className={classes.stepIndicator}><LockIcon /></Avatar>;

  const isFormComplete = (questionnaireId) => {
    return surveyData?.[questionnaireId] && !surveyData[questionnaireId].statusFlags?.includes("INCOMPLETE");
  }

  const isFormSubmitted = (questionnaireId) => {
    return surveyData?.[questionnaireId]?.statusFlags?.includes("SUBMITTED");
  }

  const isFormLocked = (questionnaireId) => {
    return surveyData?.[questionnaireId]?.statusFlags?.includes("LOCKED");
  }

  const isFormNavigable = (questionnaireId) => {
    return (surveyData?.[questionnaireId]?.questionnaire?.paginationVariant == "navigable");
  }

  const isPageComplete = (questionnaireId, section) => {
    let answerSection = Object.values(surveyData?.[questionnaireId] || {})
      .find(e => (e?.section?.["jcr:uuid"] == section?.["jcr:uuid"]));
    return answerSection && !answerSection.statusFlags?.includes("INCOMPLETE");
  }

  const displayFlag = flag => (
    <Chip
      label={flag.substring(0,1).toUpperCase() + flag.substring(1).toLowerCase()}
      variant="outlined"
      size="small"
      className={classes[flag + "Flag"] || classes.DefaultFlag}
      sx={{ mr: 1 }}
      key={flag}
    />
  )

  const displayFlags = q => (
    (surveyData?.[q]?.statusFlags ?? [])
      .filter(f => ["INCOMPLETE", "SUBMITTED", "LOCKED"].includes(f))
      .map(displayFlag)
  );

  // ----------------------------------------------------------------------------------------------------------------_
  // Prepare the display of the forms for this visit

  const  clinicQIds = questionnaireIds.filter(q => surveyData[q].targetUserType == "clinician");
  const patientQIds = questionnaireIds.filter(q => surveyData[q].targetUserType != "clinician");

  const listForms = (qIds, title, withAction) => (qIds.length > 0 &&
    <>
      <Divider><Typography variant="h6">{title}</Typography></Divider>
      <List>
        { qIds.map((q, i) => (
          <ListItem
            className={classes.formItem}
            key={q}
            disablePadding
            secondaryAction={withAction && !isFormLocked(q) && <EditButton entryPath={surveyData?.[q]?.["@path"]}/>}
          >
            <ListItemButton onClick={() => navigate(`/content.html${surveyData?.[q]?.["@path"]}`)}>
              <ListItemAvatar sx={{ alignSelf: "baseline", zoom: 1.2 }}>
                { isFormLocked(q) ? lockedIndicator : (
                  isFormComplete(q) ? doneIndicator : (
                    isFormSubmitted(q) ? incompleteIndicator : surveyIndicator
                  )
                )}
              </ListItemAvatar>
              <ListItemText
                primary={questionnaires[q]?.title}
                secondary={<>
                  { displayFlags(q) }
                  { !isFormComplete(q) && isFormNavigable(q) && listPages(q) }
                </>}
                slotProps={{ 'secondary': { 'component': 'div' } }}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </>
  );

  // For navigable forms, list pages with their completion status
  const listPages = (qId) => (
    <List dense disablePadding sx={{ width: "fit-content" }}>
      { Object.values(surveyData?.[qId]?.questionnaire || {})
        .filter(c => c?.["jcr:primaryType"] == "cards:Section")
        .map(s => {
          let isComplete = isPageComplete(qId, s);
          return (
            <ListItem
              disablePadding
              sx={{ pr: 6 }}
              secondaryAction={ isComplete && <DoneIcon color="success"/> }
              key={ s["@name"] }
            >
              <ListItemText
                disableTypography
                primary={
                  <FormattedText variant="caption" sx={isComplete ? { opacity: .5 } : {}}>
                    {s.label || s["@name"]}
                  </FormattedText>}
              />
            </ListItem>
          );
        })
      }
    </List>
  )

  // -----------------------------------------------------------------------------------------------------------------
  // Render the visit:
  // * Resource header (sticky to the top) with a simplified menu
  // * Visit information
  // * List of forms for this visit, as specified by the associated QuestionnaireSet

  return (
    <Grid container {...FORM_ENTRY_CONTAINER_PROPS}>
      <ResourceHeader
        title={`Visit ${visitNumber}`}
        breadcrumbs={(parents && getHierarchyAsList(parents, true) || "")}
        action={
          <div className={classes.actionsMenu}>
            { !isLocked && <>
              <SurveyLinkButton visitURL={`/Subjects/${patientUuid}/${visitUuid}`} size="medium"/>
              <SubjectLockAction subject={visit} reloadSubject={loadExistingData} size="medium"/>
            </>}
            <PrintButton
              size="medium"
              resourcePath={visitPath}
              resourceData={visit}
              breadcrumb={getTextHierarchy(visit, true)}
              date={DateTime.fromISO(visit['jcr:created']).toLocaleString(DateTime.DATE_MED)}
            />
          </div>
        }
        tags={visit?.statusFlags?.map(displayFlag)}
      />
      <Grid>{ displayVisitInfo() }</Grid>
      <Grid>
        { listForms(clinicQIds, "Clinical examination", true) }
        { listForms(patientQIds, "Patient surveys") }
      </Grid>
    </Grid>
  );
}

export default Visit;
