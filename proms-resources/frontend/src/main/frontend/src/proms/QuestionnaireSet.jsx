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
import React, { useState, useEffect, useContext }  from 'react';
import { v4 as uuidv4 } from 'uuid';

import {
  Avatar,
  Button,
  CircularProgress,
  Fab,
  Grid,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Paper,
  Typography,
  makeStyles
} from '@material-ui/core';
import { Alert, AlertTitle } from '@material-ui/lab';
import NextStepIcon from '@material-ui/icons/ChevronRight';
import DoneIcon from '@material-ui/icons/Done';
import WarningIcon from '@material-ui/icons/Warning';

import moment from "moment";
import * as jdfp from "moment-jdateformatparser";

import Form from "../questionnaire/Form.jsx";
import PromsHeader from "./Header.jsx";
import DateQuestionUtilities from "../questionnaire/DateQuestionUtilities";
import { ENTRY_TYPES } from "../questionnaire/FormEntry.jsx"

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

const useStyles = makeStyles(theme => ({
  mainContainer: {
    margin: theme.spacing(2),
    "& #cards-resource-footer > .MuiMobileStepper-root" : {
      bottom: theme.spacing(3),
      left: 0,
      right: theme.spacing(1),
    },
    "& #cards-resource-footer .MuiMobileStepper-progress" : {
      width: "100%",
    },
  },
  screen : {
    alignItems: "center",
    margin: "auto",
    maxWidth: "780px",
    "& h4, h6" : {
      textAlign: "center",
    }
  },
  stepIndicator : {
    border: "1px solid " + theme.palette.text.secondary,
    background: "transparent",
    color: theme.palette.text.secondary,
  },
  incompleteIndicator : {
    border: "1px solid " + theme.palette.secondary.main,
    background: "transparent",
    color: theme.palette.secondary.main,
  },
  doneIndicator : {
    background: theme.palette.success.main,
  },
  survey : {
    alignItems: "stretch",
    justify: "space-between",
    flexWrap: "nowrap",
  },
  reviewFab : {
    margin: theme.spacing(1),
    position: "fixed",
    bottom: theme.spacing(2),
    right: theme.spacing(4),
    zIndex: 100,
  }
}));

function QuestionnaireSet(props) {
  const { subject, username, contentOffset } = props;

  // Identifier of the questionnaire set used for the visit
  const [ id, setId ] = useState();
  // Questionnaire set title, to display to the patient user
  const [ title, setTitle ] = useState();
  // Map questionnaire id -> title, path and optional time estimate (in minutes) for filling it out
  const [ questionnaires, setQuestionnaires ] = useState();
  // The ids of the questionnaires in this set, in the order they must be filled in
  const [ questionnaireSetIds, setQuestionnaireSetIds ] = useState();
  // The ids of the questionnaires displayed to the patient
  const [ questionnaireIds, setQuestionnaireIds ] = useState();

  // Data already associated with the subject
  const [ subjectData, setSubjectData ] = useState();
  // Since we cannot use subjectData directly as a dependency for useEffect,
  // but we must re-run effects when subjectData gets refetched,
  // use a simple counter to mark that new data arrived.
  // setSubjectDataLoadCount must be called every time setSubjectData is called!
  const [ subjectDataLoadCount, setSubjectDataLoadCount] = useState(0);

  // Visit information form
  const [ visitInformation, setVisitInformation ] = useState();
  // Did the user make it to the last screen?
  const [ endReached, setEndReached ] = useState();
  // Is the user reviewing an already complete form?
  const [ reviewMode, setReviewMode ] = useState(false);
  // Has the user submitted their answers?
  const [ isSubmitted, setSubmitted ] = useState(false);
  // Has everything been filled out?
  const [ isComplete, setComplete ] = useState();

  // Step -1: haven't started yet, welcome screen
  // Step i = 0 ... # of questionnaires-1 : filling out questionnaire #i
  // Step n = # of questionnaires: all done, exist screen
  const [ crtStep, setCrtStep ] = useState(-1);
  // What questionnaire comes next after the current step
  const [ nextQuestionnaire, setNextQuestionnaire] = useState(null);
  // What form we're currently displaying
  const [ crtFormId, setCrtFormId ] = useState(null);
  // When something goes wrong:
  const [ error, setError ] = useState("");
  // Screen layout props
  const [ screenType, setScreenType ] = useState();

  const classes = useStyles();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const visitInformationFormTitle = "Visit information";

  const isFormComplete = (questionnaireId) => {
    return subjectData?.[questionnaireId] && !subjectData[questionnaireId].statusFlags?.includes("INCOMPLETE");
  }

  // Determine the screen type (and style) based on the step number
  useEffect(() => {
    setScreenType(crtStep >= 0 && crtStep < questionnaireIds.length ? "survey" : "screen");
  }, [crtStep]);

  // Reset the crtFormId when returning to the welcome screen
  useEffect(() => {
    crtStep == -1 && setCrtFormId(null);
  }, [crtStep]);

  // Determine the next questionnaire that needs to be filled out
  useEffect(() => {
    if (!questionnaires || !subjectData) return;
    // Find the next unfilled questionnaire, if any:
    let nextStep = findNextStep(crtStep);
    setNextQuestionnaire(nextStep < questionnaireIds?.length ? questionnaires[questionnaireIds?.[nextStep]] : null);
  }, [crtStep, subjectDataLoadCount]);

  // If we're back to the start because the user was directed to add missing answers,
  // dont't show the welcome screen and skip to the next step without them pressing start
  useEffect(() => {
    if (crtStep == -1 && endReached) {
      nextQuestionnaire && launchNextForm();
    }
  }, [crtStep, endReached, subjectDataLoadCount, nextQuestionnaire?.["@path"]]);

  // When the uuid of the next form is set, move to the next step to launch the form
  useEffect(() => {
    if (reviewMode) {
      setReviewMode(false);
    } else {
      crtFormId && nextStep();
    }
  }, [crtFormId]);

  // At the last step, reload the form data to determine if all the questionnaires have been completed
  useEffect(() => {
    if (!questionnaireIds || crtStep < questionnaireIds.length) return;
    setEndReached(true);
    loadExistingData();
  }, [crtStep]);

  // Determine if all surveys have been filled out
  useEffect(() => {
    if (!subjectData || !questionnaireIds) return;
    setComplete(Object.keys(subjectData || {}).filter(q => isFormComplete(q)).length == questionnaireIds.length);
  }, [subjectDataLoadCount]);

  // Determine if the user has already submitted their forms
  useEffect(() => {
    let submittedQuestionUuid = visitInformation?.questionnaire?.surveys_submitted?.["jcr:uuid"] || null;
    if (!submittedQuestionUuid) return;
    let answer = Object.values(visitInformation).find(value => value.question?.["jcr:uuid"] == submittedQuestionUuid)?.value || 0;
    if (answer == 1) {
      setSubmitted(true);
    } else {
      setSubmitted(false);
    }
  }, [subjectDataLoadCount]);

  // When the user lands on a completed visit that has not been submited, proceed to reviewing their forms
  useEffect(() => {
    if(isComplete && !isSubmitted && questionnaireIds?.length > 0 && crtStep == -1) {
      setCrtStep(questionnaireIds.length)
    }
  }, [isComplete, isSubmitted])

  const loadExistingData = () => {
    setComplete(undefined);
    fetchWithReLogin(globalLoginDisplay, `${subject}.data.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        if (!questionnaires) {
          setSubjectData(json);
          setVisitInformation(json[visitInformationFormTitle]?.[0] || {});
          setId(Object.values(json[visitInformationFormTitle]?.[0]).find(o => o?.question?.text == "Surveys")?.value);
          return;
        }
        selectDataForQuestionnaireSet(json, questionnaires, questionnaireSetIds);
      })
      .catch((response) => {
        setError(`Loading the visit failed with error code ${response.status}: ${response.statusText}`);
      });
  }

  const loadQuestionnaireSet = () => {
    if (!!!id) {
      return;
    }
    fetchWithReLogin(globalLoginDisplay, `/Proms/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        parseQuestionnaireSet(json);
      })
      .catch((response) => {
        if (response.status == 404) {
          setError("The survey you are trying to access does not exist. Please contact your care team for further assistance.");
        } else {
          setError(`Loading the survey failed with error code ${response.status}: ${response.statusText}`);
        }
        setQuestionnaires(null);
      });
  }

  let parseQuestionnaireSet = (json) => {
    // Extract the title
    setTitle(json.name);

    // Map the relevant questionnaire info
    let data = {};
    Object.values(json || {})
      .filter(value => value['jcr:primaryType'] == 'cards:QuestionnaireRef')
      .forEach(value => {
        let addons = Object.values(value).filter(filterValue => ENTRY_TYPES.includes(filterValue['jcr:primaryType']));
        data[value.questionnaire['@name']] = {
          'title': value.questionnaire?.title || value.questionnaire?.['@name'],
          '@path': value.questionnaire?.['@path'],
          '@name': value.questionnaire?.['@name'],
          'hasInterpretation': hasInterpretation(value.questionnaire) || addons.some(hasInterpretation),
          'estimate': value.estimate,
          'questionnaireAddons': addons
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

  let selectDataForQuestionnaireSet = (subjectData, questionnaireSet, questionnaireSetIds) => {
    let ids = [];
    let data = {};
    questionnaireSetIds.forEach(q => {
      if (subjectData[questionnaireSet?.[q]?.title]?.[0]?.['jcr:primaryType'] == "cards:Form") {
        data[q] = subjectData[questionnaireSet?.[q]?.title][0];
        ids.push(q);
      }
    });
    // If questionnaireIds is defined, this is not the first time we're loading the data.
    // The purpose of loading it a second time is to check the completion status of forms.
    // In that case, we do not reassign questionnaireIds to avoid loading this data in a loop
    !questionnaireIds && setQuestionnaireIds(ids);
    setSubjectData(data);
    setSubjectDataLoadCount((counter) => counter+1);
  };

  // Find out if a questionnaire has an interpretation for the patient, i.e. a "summary" section
  let hasInterpretation = (json) => {
     if (json?.displayMode == "summary") {
       return true;
     }
     let result = false;
     Object.values(json || {})
       .filter(value => value['jcr:primaryType'] == 'cards:Section')
       .forEach(section => { result ||= hasInterpretation(section) });
     return result;
  }

  // Find the next step : Skip questionnaires that have already been filled out
  let findNextStep = (step) => {
    let next = step + 1
    // Skip if the corresponding questionnaire has already been filled out:
    while (next < questionnaireIds.length && isFormComplete(questionnaireIds[next])) ++next;
    return next;
  }

  // Advance to the next step
  let nextStep = () => { setCrtStep(findNextStep) }

  let launchNextForm = () => {;
    if (subjectData?.[nextQuestionnaire['@name']]) {
      // Form already exists and is incomplete: prepare to edit it
      setCrtFormId(subjectData[nextQuestionnaire['@name']]['@name']);
    }
  }

  // At first, load the existing subject data to determine which questionnaire set is bound to the visit
  useEffect(loadExistingData, []);
  // After the visit is loaded and we know the questionnaire set identifier, load all questionnaires that need to be filled out
  useEffect(loadQuestionnaireSet, [id]);

  if (!id) {
    return (
      <QuestionnaireSetScreen className={classes.screen}>
        <Typography variant="h4" color="textSecondary">You do not have any pending surverys</Typography>
      </QuestionnaireSetScreen>
    );
  }

  if (error) {
    return (
      <QuestionnaireSetScreen className={classes.screen}>
        <Typography variant="subtitle1" color="error">{error}</Typography>
      </QuestionnaireSetScreen>
    );
  }

  if (!questionnaireIds || !questionnaires || !subjectData) {
    return (
      <QuestionnaireSetScreen className={classes.screen}>
        <CircularProgress />
      </QuestionnaireSetScreen>
    );
  }

  let stepIndicator = (step, withTotal) => {
    return (
      step >=0 && step < questionnaireIds?.length ?
      <Avatar className={classes.stepIndicator}>{step + 1}{withTotal ? ("/" + questionnaireIds?.length) : ""}</Avatar>
      : <></>);
  }

  let displayEstimate = (questionnaireId) => {
    let e = questionnaires[questionnaireId]?.estimate;
    return e ? (e + " minute" + (e != 1 ? "s" : "")) : "";
  }

  let onSubmit = () => {
    postSubmission();
    checkinForms();
  }

  let postSubmission = () => {
    let submittedQuestionUuid = visitInformation?.questionnaire?.surveys_submitted?.["jcr:uuid"] || null;
    let url = visitInformation?.["@path"];

    if (submittedQuestionUuid && url) {
      let answerUuid = Object.values(visitInformation).find(value => value.question?.["jcr:uuid"] == submittedQuestionUuid)?.["@name"] || uuidv4();
      let data = new FormData();
      data.append("./" + answerUuid + "/jcr:primaryType", "cards:BooleanAnswer");
      data.append("./" + answerUuid + "/question", submittedQuestionUuid);
      data.append("./" + answerUuid + "/question@TypeHint", "Reference");
      data.append("./" + answerUuid + "/value", 1);
      data.append("./" + answerUuid + "/value@TypeHint", "Long");
      fetchWithReLogin(
        globalLoginDisplay,
        url,
        { method: 'POST', body: data }
      )
    }

    setSubmitted(true);
  }

  let checkinForms = () => {
    if (!questionnaireIds || questionnaireIds.length < 1) {
      // Nothing to check in
      return;
    }

    // Requests to /Forms get sent to the dataImportServlet and fail to checkin, so send it to a specific form
    const URL = "/Forms/" + subjectData?.[questionnaireIds[0]]["@name"];
    var request_data = new FormData();
    request_data.append(":operation", "checkin");

    questionnaireIds.forEach(q => {
      let id = subjectData?.[q]?.["@name"];
      id && request_data.append(":applyTo", "/Forms/" + id);
    });

    fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
      .then( (response) => {
        if (!response.ok) {
          return(Promise.reject(response));
        }
      })
      .catch((response) => {
        setError(`Failed to check in form with error code ${response.status}: ${response.statusText}`);
      });
  }

  let doneIndicator = <Avatar className={classes.doneIndicator}><DoneIcon /></Avatar>;

  let incompleteIndicator = <Avatar className={classes.incompleteIndicator}><WarningIcon /></Avatar>;


  const greet = (name) => {
    let hourOfDay = (new Date()).getHours();
    let timeOfDay = hourOfDay < 12 ? "morning" : hourOfDay < 18 ? "afternoon" : "evening";
    return `Good ${timeOfDay}` + (name ? `, ${name}` : '');
  }

  const getVisitInformation = (questionName) => {
    let question = visitInformation?.questionnaire?.[questionName]?.["jcr:uuid"];
    let answer = Object.values(visitInformation).find(value => value.question?.["jcr:uuid"] == question)?.value || null;
    return answer;
  }

  const getVisitDate = () => {
    let dateAnswer = getVisitInformation("time");
    return dateAnswer == null ? null : DateQuestionUtilities.amendMoment(DateQuestionUtilities.stripTimeZone(dateAnswer));
  }

  const appointmentDate = () => {
    let date = getVisitDate();
    return date == null ? ""
      : date.formatWithJDF("EEEE, MMMM d, yyyy h:mma");
  }

  let appointmentAlert = () => {
    // TODO: Remove with tokenless authentication
    const time = appointmentDate();
    let location = getVisitInformation("location");
    let provider = getVisitInformation("provider");
    provider = provider && provider.length > 1 ? provider.join(", ") : provider;
    return (time || location || provider) ?
      <Alert severity="info">
        <AlertTitle>Upcoming appointment</AlertTitle>
        {time ? <> {time} </> : null}
        {location ? <> at {location}</> : null}
        {provider ? <> with {provider}</> : null}
      </Alert>
      : null
  }

  const diffString = (startDate, endDate, division, result, modulus = null) => {
    let diff = endDate.diff(startDate, division);
    if (modulus != null) {
      diff = diff % modulus;
    }
    if (diff > 0) {
      result.push(diff + " " + (diff == 1 && division[division.length - 1] == "s"
        ? division.substring(0, division.length -1)
        : division));
    }
  }

  const expiryDate = () => {
    let result = "";
    const date = getVisitDate();
    if (date != null) {
      // If the visit date could be retrieved, this is an emailed token and will expire 2 hours after the visit
      date.add(2, 'hours');

      // Get the date difference in the format: X days, Y hours and Z minutes,
      // skipping any time division that has a value of 0
      const now = moment();
      const diffStrings = [];
      diffString(now, date, "days", diffStrings);
      diffString(now, date, "hours", diffStrings, 24);
      diffString(now, date, "minutes", diffStrings, 60);

      if (diffStrings.length > 1) {
        result = " and " + diffStrings.pop();
      }
      if (diffStrings.length > 0) {
        result = " This survey link will expire in " + diffStrings.join(", ") + result + ".";
      }
    } else {
      // Visit date could not be retrieved, this token will expire 1 hour from creation.
      result = " This session will expire in 1 hour."
    }

    return result;
  }

  let welcomeScreen = (isComplete && isSubmitted || questionnaireIds?.length == 0) ? [
    <Typography variant="h4" key="welcome-greeting">{ greet(username) }</Typography>,
    appointmentAlert(),
    <Typography color="textSecondary" variant="subtitle1" key="welcome-message">
        You have no pending surveys to fill out for your next appointment.
    </Typography>
  ] : [
    <Typography variant="h4" key="welcome-greeting">{ greet(username) }</Typography>,
    appointmentAlert(),
    <Typography paragraph key="welcome-message">
        Tell us about your symptoms prior to your appointment.
    </Typography>,
    <List key="welcome-surveys">
    { (questionnaireIds || []).map((q, i) => (
      <ListItem key={q+"Welcome"}>
        <ListItemAvatar>{isFormComplete(q) ? doneIndicator : stepIndicator(i)}</ListItemAvatar>
        <ListItemText
          primary={questionnaires[q]?.title}
          secondary={!isFormComplete(q) && (displayEstimate(q)
            + (subjectData?.[q]?.["jcr:lastModifiedBy"] === "patient" ? " (in progress)" : ""))}
        />
      </ListItem>
    ))}
    </List>,
    <Typography paragraph key="expiry-message" color="textSecondary">
        {expiryDate()}
    </Typography>,
    nextQuestionnaire && <Fab variant="extended" color="primary" onClick={launchNextForm} key="welcome-action">Begin</Fab>
  ];

  let formScreen = [
        <Form
          key={crtStep}
          id={crtFormId}
          mode="edit"
          disableHeader
          questionnaireAddons={nextQuestionnaire?.questionnaireAddons}
          doneIcon={nextQuestionnaire ? <NextStepIcon /> : <DoneIcon />}
          doneLabel={nextQuestionnaire ? `Continue to ${nextQuestionnaire?.title}` : "Review"}
          onDone={nextQuestionnaire ? launchNextForm : nextStep}
          doneButtonStyle={{position: "relative", right: 0, bottom: "unset", textAlign: "center"}}
          contentOffset={contentOffset || 0}
        />
  ];

  let reviewScreen = [
    <Typography variant="h4">Please review your answers</Typography>,
    <Typography paragraph>You can update the answers for each survey and continue to this review screen before final submission.</Typography>,
    <Grid container direction="column" spacing={8}>
      {(questionnaireIds || []).map((q, i) => (
      <Grid item key={q+"Review"}>
      <Grid container spacing={4}>
        <Grid item>
          <Typography variant="h5">{questionnaires[q].title || questionnaires[q]["@name"]}</Typography>
        </Grid>
        <Grid item>
          <Form
            id={subjectData?.[q]?.['@name']}
            disableHeader
            disableButton
            questionnaireAddons={questionnaires?.[q]?.questionnaireAddons}
            contentOffset={contentOffset || 0}
          />
        </Grid>
        <Grid item>
          <Button
            variant="outlined"
            color="secondary"
            onClick={() => {setReviewMode(true); setCrtFormId(subjectData?.[q]?.["@name"]); setCrtStep(i)}}>
              Update this survey
          </Button>
        </Grid>
      </Grid>
      </Grid>
      ))}
    </Grid>,
    <div className={classes.reviewFab}>
      <Fab variant="extended" color="primary" onClick={() => {onSubmit()}}>Submit my answers</Fab>
    </div>
  ];

  // Are there any response interpretations to display to the patient?
  let hasInterpretations = (questionnaireIds || []).some(q => questionnaires?.[q]?.hasInterpretation);

  let disclaimer = (
      <Alert severity="warning">
Please note that your responses may not be reviewed by your care team until the day of your next appointment. If your symptoms are
worsening while waiting for your next appointment, please proceed to your nearest Emergency Department today, or call 911.
      </Alert>
  )

  let summaryScreen = hasInterpretations ? [
      <Typography variant="h4">Thank you for your submission</Typography>,
      <Typography color="textSecondary">
For your privacy and security, once this screen is closed the information below will not be accessible until the day of
your appointment with your provider. Please print or note this information for your reference.
      </Typography>,
      disclaimer,
      <Typography variant="h4">Interpreting your results</Typography>,
      <Typography color="textSecondary">There are different actions you can take now depending on how you have scored
your symptoms. Please see below for a summary of your scores and suggested actions.</Typography>,
      <Grid container direction="column" spacing={3}>
      { (questionnaireIds || []).map((q, i) => (
        <Grid item>
        {
          questionnaires?.[q]?.hasInterpretation ? <Form
              key={q+"Summary"}
              id={subjectData?.[q]?.['@name']}
              mode="summary"
              questionnaireAddons={questionnaires?.[q]?.questionnaireAddons}
              disableHeader
              disableButton
              contentOffset={contentOffset || 0}
            />
            :<></>
        }
        </Grid>
      ))}
      </Grid>,
      <Fab variant="extended" color="primary" onClick={() => window.location = "/system/sling/logout"}>Close</Fab>
    ] : [
      <Typography variant="h4">Thank you for your submission</Typography>,
      disclaimer,
      <Fab variant="extended" color="primary" onClick={() => window.location = "/system/sling/logout"}>Close</Fab>
    ];

  let loadingScreen = [ <CircularProgress /> ];

  let incompleteScreen = [
        <List>
        { (questionnaireIds || []).map((q, i) => (
          <ListItem key={q+"Exit"}>
            <ListItemAvatar>{isFormComplete(q) ? doneIndicator : incompleteIndicator}</ListItemAvatar>
            <ListItemText
              primary={questionnaires[q]?.title}
              secondary={!isFormComplete(q) && "Incomplete"}
            />
          </ListItem>
        ))}
        </List>,
        <Typography color="error">Your answers are incomplete. Please update your answers by responding to all mandatory questions.</Typography>,
        <Fab variant="extended" color="primary" onClick={() => {setCrtStep(-1)}}>Update my answers</Fab>
  ];

  let exitScreen = (typeof(isComplete) == 'undefined') ? loadingScreen : (isComplete ? (isSubmitted ? summaryScreen : reviewScreen) : incompleteScreen);

  const progress = 100.0 * (crtStep + 1) / ((questionnaireIds?.length || 0) + 1);

  return (<>
      <PromsHeader
        title={title}
        greeting={username}
        progress={progress}
        subtitle={questionnaires[questionnaireIds[crtStep]]?.title}
        step={stepIndicator(crtStep, true)}
      />
      <QuestionnaireSetScreen className={classes[screenType]}>
        {
          crtStep == -1 ? welcomeScreen :
          crtStep < questionnaireIds.length ? formScreen :
          exitScreen
        }
      </QuestionnaireSetScreen>
  </>)
}

function QuestionnaireSetScreen (props) {
  let { children, ...rest } = props;

  const classes = useStyles();

  return (
  <Paper elevation={0} className={classes.mainContainer}>
    <Grid container direction="column" spacing={4} {...rest}>
      {Array.from(children || []).filter(c => c).map((c, i) => <Grid item key={i+"MainItem"} xs={12}>{c}</Grid>)}
    </Grid>
  </Paper>
  );
}

export default QuestionnaireSet;
