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
  Divider,
  Fab,
  Grid,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Paper,
  Typography,
} from '@mui/material';
import makeStyles from '@mui/styles/makeStyles';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import NextStepIcon from '@mui/icons-material/ChevronRight';
import DoneIcon from '@mui/icons-material/Done';
import WarningIcon from '@mui/icons-material/Warning';
import SurveyIcon from '@mui/icons-material/Assignment';

import { DateTime } from "luxon";

import Form from "../questionnaire/Form.jsx";
import Header from "./Header.jsx";
import DateQuestionUtilities from "../questionnaire/DateQuestionUtilities";
import FormattedText from "../components/FormattedText.jsx";
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
    width: "100%",
    "& > .MuiGrid-item" : {
      paddingLeft: 0,
    },
    "& h4, h6, .MuiTypography-paragraph" : {
      textAlign: "center",
    }
  },
  surveyPreviewComponent : {
    background: theme.palette.action.selected,
    padding: theme.spacing(2),
    "& > .MuiTypography-root h1" : {
      fontSize: "1.5em",
    },
    "& > .MuiButton-root" : {
      display: "block",
      margin: "auto",
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
  },
  survey : {
    alignItems: "stretch",
    justify: "space-between",
    flexWrap: "nowrap",
    "& form" : {
      maxWidth: "780px",
      margin: "auto",
    },
  },
}));

function QuestionnaireSet(props) {
  const { subject, username, displayText, contentOffset, config } = props;

  // Form content offset, used for sticky elements
  // Should be the contentOffset passed as a prop + the Header height
  const [ formContentOffset, setFormContentOffset ] = useState(contentOffset || 0);

  // Identifier of the questionnaire set used for the visit
  const [ id, setId ] = useState();
  // Questionnaire set title, intro text, and ending text, to display to the patient user
  const [ title, setTitle ] = useState();
  const [ intro, setIntro ] = useState();
  const [ ending, setEnding ] = useState();
  const [ tokenLifetime, setTokenLifetime ] = useState();
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
  // Previews of the data entered for each survey
  const [ previews, setPreviews ] = useState({});

  // Visit information form
  const [ visitInformation, setVisitInformation ] = useState();
  // Did the user make it to the last screen?
  const [ endReached, setEndReached ] = useState();
  // Configuration specifying if the patient sees a review
  // screen after completing the surveys.
  // It can be set globally in Survey Instructions,
  // and overridden by each QuestionnaireSet definition.
  const [ enableReviewScreen, setEnableReviewScreen ] = useState();
  // Is the user reviewing an already complete form?
  const [ reviewMode, setReviewMode ] = useState(false);
  // Has everything been filled out?
  const [ isComplete, setComplete ] = useState();
  // Flag for whether the user is permitted to submit incomplete forms
  // based on configuration specifying how long in advance (if permitted)
  // and on the event date. Defaults to false.
  const [ canSubmitIncomplete, setCanSubmitIncomplete ] = useState(false);
  // Flag for whether the user decided to submit incomplete surveys in the current session
  const [ submittingIncomplete, setSubmittingIncomplete ] = useState(false);
  // Did the user just click on Submit and we're waiting
  // for the request to complete?
  const [ submissionInProgress, setSubmissionInProgress ] = useState(false);
  // Has the user submitted their answers?
  const [ isSubmitted, setSubmitted ] = useState(false);

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
  // Subtype for non-survey screens
  const [screenSubtype, setScreenSubtype ] = useState();

  const classes = useStyles();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const visitInformationFormTitle = "Visit information";

  const isFormComplete = (questionnaireId) => {
    return subjectData?.[questionnaireId] && !subjectData[questionnaireId].statusFlags?.includes("INCOMPLETE");
  }

  const isFormSubmitted = (questionnaireId) => {
    return subjectData?.[questionnaireId]?.statusFlags?.includes("SUBMITTED");
  }

  const getVisitInformation = (questionName) => {
    let question = visitInformation?.questionnaire?.[questionName]?.["jcr:uuid"];
    let answer = Object.values(visitInformation).find(value => value.question?.["jcr:uuid"] == question)?.value || null;
    return answer;
  }

  const getVisitDate = () => {
    let dateAnswer = getVisitInformation("time");
    return DateQuestionUtilities.toPrecision(DateQuestionUtilities.stripTimeZone(dateAnswer));
  }

  // If the `enableReviewScreen` state is not already defined, initialize it with the value passed via config
  useEffect(() => {
    typeof(enableReviewScreen) == "undefined" && setEnableReviewScreen(config?.enableReviewScreen);
  }, [config?.enableReviewScreen]);

  // Initialize the `canSubmitIncomplete` setting based on the config and the visit date
  useEffect(() => {
    if (!Number.isNaN(+(config?.daysRelativeToEventWhenIncompleteSurveysCanBeSubmitted)) && visitInformation) {
      let visitDate = getVisitDate();
      let dateIncompleteSubmissionEnabled = visitDate.plus({
        days: config.daysRelativeToEventWhenIncompleteSurveysCanBeSubmitted
      }).endOf('day');
      const diff = dateIncompleteSubmissionEnabled.diffNow(["days"]);
      if (Math.floor(diff["days"]) <= 0) {
        setCanSubmitIncomplete(true);
      }
    }
  }, [config?.daysRelativeToEventWhenIncompleteSurveysCanBeSubmitted, visitInformation]);

  // Determine the screen type (and style) based on the step number
  useEffect(() => {
    setScreenType(crtStep >= 0 && crtStep < questionnaireIds?.length ? "survey" : "screen");
  }, [crtStep]);

  // Once we start rendering forms, update the formContentOffset
  useEffect(() => {
    (crtStep == 1) && setFormContentOffset((contentOffset || 0) + (document?.getElementById('patient-portal-header')?.clientHeight || 0));
  }, [crtStep]);

  useEffect(() => {
    setScreenSubtype(
      screenType == "screen" ?
        isComplete ?
          isSubmitted ?
            "summaryScreen"
            :
            "reviewScreen"
        : "incompleteScreen"
      : ""
    )
  }, [screenType, isComplete, isSubmitted]);

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
    if (crtStep == -1 && (endReached || !config?.enableStartScreen)) {
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
    loadPreviews();
  }, [crtStep]);

  // Determine if all surveys have been filled out
  useEffect(() => {
    if (!subjectData || !questionnaireIds) return;
    setComplete(Object.keys(subjectData || {}).filter(q => isFormComplete(q)).length == questionnaireIds.length);
  }, [subjectDataLoadCount]);

  // Automatically log out the user at the end
  useEffect(() => {
    if (isSubmitted) {
      window.addEventListener("beforeunload", (e) => { fetch('/system/sling/logout', {"redirect": "manual"}); }, true);
    }
  }, [isSubmitted]);

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

  // When the user lands on a completed visit that has not been submitted, proceed to the last step
  useEffect(() => {
    if(isComplete && !isSubmitted && questionnaireIds?.length > 0 && crtStep == -1) {
      setCrtStep(questionnaireIds.length)
    }
  }, [isComplete, isSubmitted])

  // At the last step, if the configuration specifies to skip the review, automatically submit
  useEffect(() => {
    if (isComplete && !isSubmitted && endReached && !enableReviewScreen) {
      onSubmit();
    }
  }, [isComplete, isSubmitted, endReached, enableReviewScreen]);

  const loadExistingData = () => {
    setComplete(undefined);
    fetchWithReLogin(globalLoginDisplay, `${subject}.data.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        if (!questionnaires) {
          setSubjectData(json);
          setVisitInformation(json[visitInformationFormTitle]?.[0] || {});
          let clinicPath = Object.values(json[visitInformationFormTitle]?.[0]).find(o => o?.question?.["@name"] == "clinic")?.value;
          return fetchWithReLogin(globalLoginDisplay, `${clinicPath}.deep.json`)
            .then((response) => response.ok ? response.json() : Promise.reject(response))
            .then((json) => {
              setId(json["survey"]);
              setTokenLifetime(json.daysRelativeToEventWhileSurveyIsValid);
            });
        }
        selectDataForQuestionnaireSet(json, questionnaires, questionnaireSetIds);
      })
      .catch(() => setError("Your survey could not be loaded at this time. Please try again later or contact the sender of the survey for further assistance."));
  }

  const loadQuestionnaireSet = () => {
    if (!!!id) {
      return;
    }
    fetchWithReLogin(globalLoginDisplay, `/Survey/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        parseQuestionnaireSet(json);
      })
      .catch((response) => {
        if (response.status == 404) {
          setError("The survey you are trying to access does not exist. Please contact the sender of the survey for further assistance.");
        } else {
          setError("Your survey could not be loaded at this time. Please try again later or contact the sender of the survey for further assistance.");
        }
        setQuestionnaires(null);
      });
  }

  let parseQuestionnaireSet = (json) => {
    // Extract the title, intro, and ending
    setTitle(json.name);
    setIntro(json.intro || "");
    setEnding(json.ending || "");
    // If the questionnaire set specifies a value for `enableReviewScreen`, overwrite the curently stored value
    typeof(json.enableReviewScreen) != "undefined" && setEnableReviewScreen(json.enableReviewScreen);

    // Map the relevant questionnaire info
    let data = {};
    Object.entries(json || {})
      .filter(([key, value]) => value['jcr:primaryType'] == 'cards:QuestionnaireRef')
      .forEach(([key, value]) => {
        let addons = Object.values(value).filter(filterValue => ENTRY_TYPES.includes(filterValue['jcr:primaryType']));
        data[value.questionnaire['@name']] = {
          'title': value.questionnaire?.title || key,
          'alias': key,
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

  // Load the markdown serialization of the survey responses to display at the review step
  let loadPreviews = () => {
    (questionnaireIds || []).forEach(q => {
      let formId = subjectData?.[q]?.["@name"];
      // Fetch the markdown serialization of the forms
      fetchWithReLogin(globalLoginDisplay, `/Forms/${formId}.md`)
        .then(response => response.ok ? response.text() : Promise.reject(response))
        .then(text => setPreviews( oldPreviews => {
          let newPreviews = Object.assign({}, oldPreviews);
          newPreviews[formId] = text;
          return newPreviews;
        }))
        .catch(() => setError("Your responses cannot be previewed at this time. Please try again later or contact the sender of the survey for further assistance."));
    })
  }

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

  const getMessageScreen = (message) => (
    <>
      <Header
        key="header"
        greeting={username}
        withSignout={!!(config?.PIIAuthRequired)}
        progress={0}
      />
      <QuestionnaireSetScreen className={classes.screen}>
        { message }
      </QuestionnaireSetScreen>
    </>
  );

  if (!id) {
    return (
      getMessageScreen(
        <Typography variant="h4" color="textSecondary">You do not have any pending surveys</Typography>
      )
    );
  }

  if (error) {
    return (
      getMessageScreen(
        <Typography variant="subtitle1" color="error">{error}</Typography>
      )
    );
  }

  if (!questionnaireIds || !questionnaires || !subjectData) {
    return (
      getMessageScreen(<>
        <Typography variant="h4" color="textSecondary">Loading...</Typography>
        <CircularProgress />
      </>)
    );
  }

  let stepIndicator = (step, withTotal) => {
    return (
      step >=0 && questionnaireIds?.length > 1 && step < questionnaireIds?.length ?
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
      setSubmissionInProgress(true);
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
        .then(response => response.ok ? response.text() : Promise.reject(response))
        .then(() => setSubmitted(true))
        .catch(() => setError("Recording the submission of your responses has failed. Please try again later or contact the sender of the survey for further assistance."))
        .finally(() => setSubmissionInProgress(false));
    }
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
        // The error is not important enough to display to the user
        console.log(`Failed to check in form with error code ${response.status}: ${response.statusText}`);
      });
  }

  let surveyIndicator = <Avatar className={classes.stepIndicator}><SurveyIcon /></Avatar>;

  let doneIndicator = <Avatar className={classes.doneIndicator}><DoneIcon /></Avatar>;

  let incompleteIndicator = <Avatar className={classes.incompleteIndicator}><WarningIcon /></Avatar>;


  const greet = (name) => {
    let hourOfDay = (new Date()).getHours();
    let timeOfDay = hourOfDay < 12 ? "morning" : hourOfDay < 18 ? "afternoon" : "evening";
    return `Good ${timeOfDay}` + (name ? `, ${name}` : '');
  }

  const appointmentDate = () => {
    let date = getVisitDate();
    return !date?.isValid ? "" : date.toLocaleString(DateTime.DATETIME_MED_WITH_WEEKDAY);
  }

  let appointmentAlert = () => {
    const eventLabel = displayText("eventLabel", AlertTitle);
    const time = appointmentDate();
    let location = getVisitInformation("location");
    let provider = getVisitInformation("provider");
    provider = provider && provider.length > 1 ? provider.join(", ") : provider;
    return (eventLabel && (time || location || provider)) ?
      <Alert severity="info" key="appointment-notification">
        {eventLabel}
        {time ? <> {time} </> : null}
        {location ? <> at {location}</> : null}
        {provider ? <> with {provider}</> : null}
      </Alert>
      : null
  }

  const diffString = (division, result, diffs) => {
    let diff = Math.floor(diffs[division]);
    if (diff > 0) {
      result.push(diff + " " + (diff == 1 && division[division.length - 1] == "s"
        ? division.substring(0, division.length -1)
        : division));
    }
  }

  const expiryDate = () => {
    let result = "";
    let date = getVisitDate();
    if (date?.isValid) {
      // Compute the moment the token expired: the configured number of days after the visit, at midnight
      date = date.plus({days: tokenLifetime ?? config?.daysRelativeToEventWhileSurveyIsValid ?? 0}).endOf('day');

      // Get the date difference in the format: X days, Y hours and Z minutes,
      // skipping any time division that has a value of 0
      const diffs = date.diffNow(['days', 'hours', 'minutes']).toObject();
      let diffStrings = [];
      diffString("days", diffStrings, diffs);
      diffString("hours", diffStrings, diffs);
      diffString("minutes", diffStrings, diffs);

      if (diffStrings.length > 0) {
        result = " This survey link will expire in " + diffStrings[0] + ".";
      }
    } else {
      // Visit date could not be retrieved, this token will expire 1 hour from creation.
      result = " This session will expire in 1 hour."
    }

    return result;
  }

  // The questionnaire set intro may reference data from the visit information form, with default values
  // For example @{visit.location:-UHN} specifies that the `location` field needs to be displayed,
  // and it should default to "UHN" if the field is empty.
  let pattern = /@\{visit\.([a-zA-z0-9_]*)(\:\-(.+))?\}/g;
  // First, find the field name and default value
  // It is necessary to find them separately first, as we need to call a function on the field name to
  // obtain its value for replacement, and this won't work in one go directly in `replaceAll`, as
  // the function will end up being called before the matching groups are identified.
  let pieces = pattern.exec(intro);
  // Replace the occurrence of the pattern with the value
  let introMessage = intro.replaceAll(pattern, getVisitInformation(pieces?.[1]) || pieces?.[2] || "");

  let welcomeScreen = (isComplete && isSubmitted || questionnaireIds?.length == 0) ? [
    <Typography variant="h4" key="welcome-greeting">{ greet(username) }</Typography>,
    appointmentAlert(),
    displayText("noSurveysMessage", Typography, {color: "textSecondary", variant: "subtitle1", key: "survey-info"}),
  ] : [
    <Typography variant="h4" key="welcome-greeting">{ greet(username) }</Typography>,
    appointmentAlert(),
    introMessage ? <FormattedText paragraph key="intro-message">{introMessage}</FormattedText> : displayText("surveyIntro", Typography, {paragraph: true, key: "welcome-message"}),
    <List key="welcome-surveys">
    { (questionnaireIds || []).map((q, i) => (
      <ListItem key={q+"Welcome"}>
        <ListItemAvatar>{isFormComplete(q) ? doneIndicator : questionnaireIds.length == 1 ? surveyIndicator : stepIndicator(i)}</ListItemAvatar>
        <ListItemText
          primary={questionnaires[q]?.title}
          secondary={isFormSubmitted(q) ? "Submitted" :
            !isFormComplete(q) && (displayEstimate(q)
            + (["patient", "guest-patient"].includes(subjectData?.[q]?.["jcr:lastModifiedBy"]) ? " (in progress)" : ""))}
        />
      </ListItem>
    ))}
    </List>,
    nextQuestionnaire && <Fab variant="extended" color="primary" onClick={launchNextForm} key="welcome-action">Begin</Fab>,
    <Typography paragraph key="expiry-message" color="textSecondary">
        {expiryDate()}
    </Typography>,
    displayText("surveyDraftInfo", FormattedText, {paragraph: true, variant: "body2", key: "draft-info"}),
  ];

  let formScreen = [
        <Form
          key={crtStep}
          id={crtFormId}
          mode="edit"
          requireCompletion={canSubmitIncomplete ? false : undefined}
          disableHeader
          questionnaireAddons={nextQuestionnaire?.questionnaireAddons}
          doneIcon={nextQuestionnaire ? <NextStepIcon /> : <DoneIcon />}
          doneLabel={nextQuestionnaire ? "Next survey" : enableReviewScreen ? "Review" : "Submit my answers"}
          onDone={nextQuestionnaire ? launchNextForm : nextStep}
          doneButtonStyle={{position: "relative", right: 0, bottom: "unset", textAlign: "center"}}
          contentOffset={formContentOffset}
        />
  ];

  let submitButton = (label) => (
    <Fab variant="extended" disabled={submissionInProgress} color="primary" onClick={() => {onSubmit()}} key="review-submit">
      {submissionInProgress ? "Submitting...." : (label ?? "Submit")}
    </Fab>
  );

  let reviewScreen = !enableReviewScreen ? [
    <Grid alignItems="center" justifyContent="center">
      <Grid item key="review-loading"><CircularProgress/></Grid>
    </Grid>
  ] : [
    <Typography variant="h4" key="review-title">Review and Submit</Typography>,
    <Divider/>,
    submitButton("Submit now"),
    <Divider/>,
    <Grid container direction="column" spacing={8} key="review-list">
      {(questionnaireIds || []).filter(q => !isFormSubmitted(q)).map((q, i) => (
      <Grid item key={q+"Review"}>
      { previews?.[subjectData?.[q]?.["@name"]] ?
        <Grid container direction="column" alignItems="stretch" spacing={0}>
        <Grid item>
         <Paper elevation={0} className={classes.surveyPreviewComponent}>
          <FormattedText>{ previews?.[subjectData?.[q]?.["@name"]] }</FormattedText>
         </Paper>
        </Grid>
        <Grid item>
         <Paper elevation={0} className={classes.surveyPreviewComponent}>
          <Button
            variant="contained"
            color="secondary"
            onClick={() => {setReviewMode(true); setCrtFormId(subjectData?.[q]?.["@name"]); setCrtStep(i)}}>
              Change
          </Button>
         </Paper>
        </Grid>
        </Grid>
        :
        <CircularProgress />
      }
      </Grid>
      ))}
    </Grid>,
    submitButton("Submit survey")
  ];

  // Are there any response interpretations to display to the patient?
  let hasInterpretations = (questionnaireIds || []).some(q => questionnaires?.[q]?.hasInterpretation);

  // Replace any occurence of a visit information field with its value per current visit
  pieces = pattern.exec(ending);
  // Replace the occurrence of the pattern with the value
  let endingMessage = ending.replaceAll(pattern, getVisitInformation(pieces?.[1]) || pieces?.[2] || "");

  let finalInstructions = (
      endingMessage ? <FormattedText paragraph key="summary-instructions">{endingMessage}</FormattedText> :
      displayText("summaryInstructions", FormattedText, {color: "textSecondary", key: "summary-instructions", paragraph: true})
  );

  let disclaimer = (
      displayText("disclaimer", Alert, {severity: "warning", key: "disclaimer"})
  );

  let summaryScreen = hasInterpretations ? [
      <Typography variant="h4" key="summary-title">Thank you</Typography>,
      finalInstructions,
      disclaimer,
      <Typography variant="h4" key="summary-intro">Interpreting your results</Typography>,
      displayText("interpretationInstructions", Typography, {color: "textSecondary", key: "summary-interpretation-instructions"}),
      <Grid container direction="column" spacing={3} key="summary-list">
      { (questionnaireIds || []).map((q, i) => (
        <Grid item key={q+"Summary"}>
        {
          questionnaires?.[q]?.hasInterpretation ? <Form
              id={subjectData?.[q]?.['@name']}
              mode="summary"
              questionnaireAddons={questionnaires?.[q]?.questionnaireAddons}
              disableHeader
              disableButton
              contentOffset={formContentOffset}
            />
            :<></>
        }
        </Grid>
      ))}
      </Grid>,
    ] : [
      <Typography variant="h4" key="summary-title">Thank you</Typography>,
      finalInstructions,
      disclaimer,
    ];

  let loadingScreen = [ <CircularProgress key="exit-loading"/> ];

  let incompleteScreen = [
        <List key="incomplete-list">
        { (questionnaireIds || []).map((q, i) => (
          <ListItem key={q+"Exit"}>
            <ListItemAvatar>{isFormComplete(q) ? doneIndicator : incompleteIndicator}</ListItemAvatar>
            <ListItemText
              primary={questionnaires[q]?.title}
              secondary={!isFormComplete(q) && "Incomplete" || isFormSubmitted(q) && "Submitted"}
            />
          </ListItem>
        ))}
        </List>,
        <Typography color="error" key="incomplete-message">Your answers are incomplete. Please update your answers by responding to all mandatory questions.</Typography>,
        <>
        { canSubmitIncomplete ?
          <Grid container spacing={2} direction="row">
            <Grid item>
              <Button variant="contained" color="primary" onClick={() => {setCrtStep(-1)}} key="incomplete-button">Update my answers</Button>
            </Grid>
            <Grid item>
              <Button variant="outlined" color="primary" onClick={() => setSubmittingIncomplete(true)}>Proceed anyway</Button>
            </Grid>
          </Grid>
          :
          <Fab variant="extended" color="primary" onClick={() => {setCrtStep(-1)}} key="incomplete-button">Update my answers</Fab>
        }
        </>
  ];

  let exitScreen = (
    typeof(isComplete) == 'undefined'
    ? loadingScreen
    : ( isComplete || submittingIncomplete
        ? (isSubmitted ? summaryScreen : reviewScreen)
        : incompleteScreen
      )
  );

  const progress = 100.0 * (crtStep + 1) / ((questionnaireIds?.length || 0) + 1);

  return (<>
      <Header
        key="title"
        title={title}
        greeting={username}
        withSignout={!!(config?.PIIAuthRequired)}
        progress={progress}
        subtitle={questionnaires[questionnaireIds[crtStep]]?.title}
        step={stepIndicator(crtStep, true)}
      />
      <QuestionnaireSetScreen className={classes[screenType] + (screenSubtype && classes[screenSubtype] ? (" " + classes[screenSubtype]) : "")} key="screen">
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
