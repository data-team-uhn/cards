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
import { useState, useEffect, useContext, useMemo } from 'react';


import SurveyIcon from '@mui/icons-material/Assignment';
import NextStepIcon from '@mui/icons-material/ChevronRight';
import DoneIcon from '@mui/icons-material/Done';
import OptionalIcon from '@mui/icons-material/Remove';
import WarningIcon from '@mui/icons-material/Warning';
import {
  Alert,
  AlertTitle,
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
} from '@mui/material';
import { DateTime } from "luxon";
import { makeStyles } from 'tss-react/mui';
import { v4 as uuidv4 } from 'uuid';

import Footer from "./Footer.jsx";
import Header from "./Header.jsx";
import DateTimeUtilities from "../components/DateTimeUtilities";
import FormattedText from "../components/FormattedText.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";
import Form from "../questionnaire/Form.jsx";
import { ENTRY_TYPES } from "../questionnaire/FormEntry.jsx"

const useStyles = makeStyles()(theme => ({
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
    alignItems: "flex-start",
    margin: "auto",
    maxWidth: "780px",
    width: "100%",
    "& > .mainItem" : {
      paddingLeft: 0,
    },
  },
  surveyPreviewComponent : {
    background: theme.palette.action.hover,
    padding: theme.spacing(2, 4),
    "&.incomplete" : {
      border: "1px solid " + theme.palette.error.main,
      color: theme.palette.error.main,
    },
    "& .wmde-markdown > h1" : {
      fontSize: "1.5em",
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

  /////////////////////
  // Props and state //
  /////////////////////

  const { subject, username, displayText, contentOffset, config } = props;

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
  // This holds the entire subject.data.json
  const [ subjectData, setSubjectData ] = useState();
  // This holds just the form data, indexed by questionnaire ID
  const [ formData, setFormData ] = useState();
  // Since we cannot use formData directly as a dependency for useEffect,
  // but we must re-run effects when formData gets refetched,
  // use a simple counter to mark that new data arrived.
  // setFormDataLoadCount must be called every time setFormData is called!
  const [ formDataLoadCount, setFormDataLoadCount] = useState(0);
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
  // What form we're currently displaying
  const [ crtFormId, setCrtFormId ] = useState(null);
  // When something goes wrong:
  const [ error, setError ] = useState("");

  const { classes } = useStyles();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const visitInformationFormTitle = "Visit information";

  //////////////////////////
  // Function definitions //
  //////////////////////////

  const isFormComplete = (questionnaireId) => {
    return formData?.[questionnaireId] && !formData[questionnaireId].statusFlags?.includes("INCOMPLETE");
  }

  const isFormSubmitted = (questionnaireId) => {
    return formData?.[questionnaireId]?.statusFlags?.includes("SUBMITTED");
  }

  // Whether a form is for a questionnaire that was marked optional in the questionnaire set.
  // This is recorded on the form itself via the OPTIONAL status flag when the form is created,
  // so we don't need to cross-reference the questionnaire set definition here.
  const isFormOptional = (questionnaireId) => {
    return formData?.[questionnaireId]?.statusFlags?.includes("OPTIONAL");
  }

  // Title shown to the patient: optional questionnaires are flagged as such in the UI.
  const getDisplayTitle = (questionnaireId) => {
    const title = questionnaires?.[questionnaireId]?.title || questionnaireId;
    if (!title) return null;
    return title + (isFormOptional(questionnaireId) ? " (Optional)" : "");
  }

  // Whether a form has been handled for flow purposes (offered to the patient and dealt with).
  // Regular forms are done once they are no longer INCOMPLETE. "Optional" forms have no mandatory
  // questions, so they are never INCOMPLETE and would otherwise be auto-skipped from creation; instead
  // they are considered handled only once submitted, ensuring the patient is offered them at least once.
  const isFormDone = (questionnaireId) => {
    return isFormOptional(questionnaireId)
      ? isFormSubmitted(questionnaireId)
      : isFormComplete(questionnaireId);
  }

  const getVisitInformation = (questionName, formatted) => {
    let question = visitInformation?.questionnaire?.[questionName];
    if (!question) return null;
    let answer = Object.values(visitInformation).find(entry => entry.question?.["jcr:uuid"] == question?.["jcr:uuid"]);
    if ((typeof answer?.value != "undefined") && formatted) {
      // Special cases for formatting:
      if (question.dataType == "date") {
        // There may be one or two date values
        let dates = Array.of(answer.value || []).flat();
        return dates.map(d => {
          // Format dates to be human readable: January 1, 2000
          let dateAnswer = DateTime.fromISO(d);
          return dateAnswer.isValid ? dateAnswer.toLocaleString(DateTime.DATE_FULL) : "";
          // Retain only valid dates and join intervals by ' - '
        }).filter(v => v).join(" - ");
      } else if (typeof question.maxAnswers != "undefined" && question.maxAnswers != 1) {
        // Join multivalued fiels as a comma separated list
        return answer.displayedValue?.join(", ");
      }
      // For everything else, return the displayedValue
      return answer.displayedValue;
    }
    // Return the answer value by default
    return answer?.value;
  }

  const getVisitDate = () => {
    let dateAnswer = getVisitInformation("time");
    return DateTimeUtilities.toPrecision(dateAnswer);
  }

  // Flag for whether the user is permitted to submit incomplete forms
  // based on configuration specifying how long in advance (if permitted)
  // and on the event date. Defaults to false.
  // Initialize the `canSubmitIncomplete` setting based on the config and the visit date
  const canSubmitIncomplete = useMemo(() => {
    if (!Number.isNaN(+(config?.daysRelativeToEventWhenIncompleteSurveysCanBeSubmitted)) && visitInformation) {
      let visitDate = getVisitDate();
      let dateIncompleteSubmissionEnabled = visitDate.plus({
        days: config.daysRelativeToEventWhenIncompleteSurveysCanBeSubmitted
      }).endOf('day');
      const diff = dateIncompleteSubmissionEnabled.diffNow(["days"]);
      if (Math.floor(diff["days"]) <= 0) {
        return true;
      }
    }
    return false;
  }, [config?.daysRelativeToEventWhenIncompleteSurveysCanBeSubmitted, visitInformation]);

  // Determine the screen type (and style) based on the step number
  const screenType = useMemo(() => {
    const questionnaireIdsLength = questionnaireIds?.length || 0;
    return crtStep >= 0 && crtStep < questionnaireIdsLength ? "survey" : "screen";
  }, [crtStep, questionnaireIds]);

  // Screen layout props

  // Form content offset, used for sticky elements
  // Should be the contentOffset passed as a prop + the Header height
  // Once we start rendering forms, update the formContentOffset
  const formContentOffset = useMemo(() => {
    if (crtStep == 1) {
      return (contentOffset || 0) + (document?.getElementById('patient-portal-header')?.clientHeight || 0);
    }
    return contentOffset || 0;
  }, [crtStep, contentOffset]);

  // Find the next step : Skip questionnaires that have already been filled out
  const findNextStep = (step) => {
    let next = step + 1;
    // Skip if the corresponding questionnaire has already been handled:
    while (next < questionnaireIds.length && isFormDone(questionnaireIds[next])) ++next;
    return next;
  }

  // Advance to the next step
  const goToNextStep = () => setCrtStep(findNextStep(crtStep))

  // Determine the next questionnaire that needs to be filled out
  const nextQuestionnaire = useMemo(() => {
    if (!questionnaires || !formData) return null;
    // Find the next unfilled questionnaire, if any:
    let nextStep = findNextStep(crtStep);
    return nextStep < questionnaireIds?.length ? questionnaires[questionnaireIds?.[nextStep]] : null;
  }, [crtStep, questionnaires, formData, questionnaireIds]);

  // Whether every questionnaire in the set has been handled (see isFormDone). Used to decide whether
  // the patient can skip straight to the exit/review screen without going through the surveys.
  const allFormsDone = useMemo(() => (
    !!questionnaireIds?.length && questionnaireIds.every(q => isFormDone(q))
  ), [questionnaireIds, formDataLoadCount]);

  const launchNextForm = () => {
    if (formData?.[nextQuestionnaire['@name']]) {
      // Form already exists and is incomplete: prepare to edit it
      setCrtFormId(formData[nextQuestionnaire['@name']]['@name']);
    }
  }

  const selectDataForQuestionnaireSet = (subjectData, questionnaireSet, questionnaireSetIds) => {
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
    setFormData(data);
    setFormDataLoadCount((counter) => counter+1);
  };

  const loadExistingData = () => {
    setComplete(undefined);
    fetchWithReLogin(globalLoginDisplay, `${subject}.data.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        // If we don't yet have the survey identifier and all the questionnaire definitions,
        // we temporarily cache the whole subject JSON, and start fetching the survey definition,
        // and once that is loaded, we'll extract the form data from the cached subject JSON.
        if (!questionnaires) {
          // Since we only need subjectData to temporarily hold the data while we don't yet have the survey definition,
          // we don't need to update it every time.
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

  // Load the markdown serialization of the survey responses to display at the review step
  const loadPreviews = () => {
    (questionnaireIds || []).forEach(q => {
      let formId = formData?.[q]?.["@name"];
      // Fetch the markdown serialization of the forms
      fetchWithReLogin(globalLoginDisplay, `/Forms/${formId}.md`)
        .then(response => response.ok ? response.text() : Promise.reject(response))
        .then(text => setPreviews( oldPreviews => {
          let newPreviews = Object.assign({}, oldPreviews);
          newPreviews[formId] = text;
          return newPreviews;
        }))
        .catch(() => setError("Your responses cannot be previewed at this time. Please try again later or contact the sender of the survey for further assistance."));
    });
  }

  const postSubmission = () => {
    let submittedQuestionUuid = visitInformation?.questionnaire?.surveys_submitted?.["jcr:uuid"] || null;
    let url = visitInformation?.["@path"];

    if (submittedQuestionUuid && url) {
      setSubmissionInProgress(true);
      let answerUuid = Object.values(visitInformation)
        .find(value => value.question?.["jcr:uuid"] == submittedQuestionUuid)?.["@name"] || uuidv4();
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

  const checkinForms = () => {
    if (!questionnaireIds || questionnaireIds.length < 1) {
      // Nothing to check in
      return;
    }

    // Requests to /Forms get sent to the dataImportServlet and fail to checkin, so send it to a specific form
    const URL = "/Forms/" + formData?.[questionnaireIds[0]]["@name"];
    let request_data = new FormData();
    request_data.append(":operation", "checkin");

    questionnaireIds.forEach(q => {
      let id = formData?.[q]?.["@name"];
      if (id) {
        request_data.append(":checkin", `/Forms/${id}`);
      }
    });

    fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
      .then(response => response.ok ? response.text() : Promise.reject(response))
      .catch(() => console.error("Failed to check in forms"));
  }

  const onSubmit = () => {
    postSubmission();
    checkinForms();
  }

  // Find out if a questionnaire has an interpretation for the patient, i.e. a "summary" section
  const hasInterpretation = (json) => {
    if (json?.displayMode == "summary") {
      return true;
    }
    let result = false;
    Object.values(json || {})
      .filter(value => value['jcr:primaryType'] == 'cards:Section')
      .forEach(section => { result = result || hasInterpretation(section) });
    return result;
  }

  const parseQuestionnaireSet = (json) => {
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
      .filter(([key, value]) => (!value.targetUserType || value.targetUserType == 'patient'))
      .forEach(([key, value]) => {
        let addons = Object.values(value).filter(filterValue => ENTRY_TYPES.includes(filterValue['jcr:primaryType']));
        data[value.questionnaire['@name']] = {
          // Raw questionnaire title; also used as a key to look up form data in the subject JSON
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

  /////////////
  // Effects //
  /////////////

  // If the `enableReviewScreen` state is not already defined, initialize it with the value passed via config
  useEffect(() => {
    typeof(enableReviewScreen) == "undefined" && setEnableReviewScreen(config?.enableReviewScreen);
  }, [config?.enableReviewScreen]);

  // Reset the crtFormId when on the welcome screen or review screen so that "Update my Answers" triggers page loads correctly
  useEffect(() => {
    (crtStep == -1 || crtStep >= questionnaireIds?.length) && setCrtFormId(null);
  }, [crtStep]);

  // If we're back to the start because the user was directed to add missing answers,
  // dont't show the welcome screen and skip to the next step without them pressing start
  useEffect(() => {
    if (crtStep == -1 && (endReached || !config?.enableStartScreen)) {
      nextQuestionnaire && launchNextForm();
    }
  }, [crtStep, endReached, formDataLoadCount, nextQuestionnaire?.["@path"]]);

  // When the uuid of the next form is set, move to the next step to launch the form
  useEffect(() => {
    if (reviewMode) {
      setReviewMode(false);
    } else {
      crtFormId && goToNextStep();
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
    if (!formData || !questionnaireIds) return;
    setComplete(Object.keys(formData).filter(q => isFormComplete(q)).length == questionnaireIds.length);
  }, [formDataLoadCount]);

  // Automatically log out the user at the end
  useEffect(() => {
    if (isSubmitted) {
      window.addEventListener("beforeunload", (e) => fetch('/system/sling/logout', { "redirect": "manual" }), true);
    }
  }, [isSubmitted]);

  // Determine if the user has already submitted their forms
  useEffect(() => {
    let submittedQuestionUuid = visitInformation?.questionnaire?.surveys_submitted?.["jcr:uuid"] || null;
    if (!submittedQuestionUuid) return;
    let answer = Object.values(visitInformation)
      .find(value => value.question?.["jcr:uuid"] == submittedQuestionUuid)?.value || 0;
    if (answer == 1) {
      setSubmitted(true);
    } else {
      setSubmitted(false);
    }
  }, [formDataLoadCount]);

  // When the user lands on a fully handled visit that has not been submitted, proceed to the last step.
  // Uses allFormsDone rather than isComplete so that optional surveys the patient has not yet gone
  // through (which are not INCOMPLETE, but not submitted either) are still offered instead of skipped.
  useEffect(() => {
    if(allFormsDone && !isSubmitted && questionnaireIds?.length > 0 && crtStep == -1) {
      setCrtStep(questionnaireIds.length);
    }
  }, [allFormsDone, isSubmitted]);

  // At the last step, if the configuration specifies to skip the review, automatically submit
  useEffect(() => {
    if ((isComplete || submittingIncomplete) && !isSubmitted && endReached && !enableReviewScreen) {
      onSubmit();
    }
  }, [isComplete, submittingIncomplete, isSubmitted, endReached, enableReviewScreen]);

  // At first, load the existing subject data to determine which questionnaire set is bound to the visit
  useEffect(loadExistingData, []);
  // After the visit is loaded and we know the questionnaire set identifier, load all questionnaires that need to be filled out
  useEffect(loadQuestionnaireSet, [id]);

  ///////////////
  // Rendering //
  ///////////////

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

  if (!questionnaireIds || !questionnaires || !formData) {
    return (
      getMessageScreen(<>
        <Typography variant="h4" color="textSecondary">Loading...</Typography>
        <CircularProgress />
      </>)
    );
  }

  const stepIndicator = (step, withTotal) => {
    return (
      step >=0 && questionnaireIds?.length > 1 && step < questionnaireIds?.length ?
        <Avatar className={classes.stepIndicator}>{step + 1}{withTotal ? ("/" + questionnaireIds?.length) : ""}</Avatar>
        : <></>);
  }

  const displayEstimate = (questionnaireId) => {
    let e = questionnaires[questionnaireId]?.estimate;
    return e ? (e + " minute" + (e != 1 ? "s" : "")) : "";
  }

  const surveyIndicator = <Avatar className={classes.stepIndicator}><SurveyIcon /></Avatar>;

  const doneIndicator = <Avatar className={classes.doneIndicator}><DoneIcon /></Avatar>;

  const incompleteIndicator = <Avatar className={classes.incompleteIndicator}><WarningIcon /></Avatar>;

  const optionalIndicator = <Avatar className={classes.stepIndicator}><OptionalIcon /></Avatar>;

  const greet = (name) => {
    let greeting = displayText("greeting", Typography, { variant: "h6", key: "welcome-greeting" });
    if (!greeting) {
      let hourOfDay = (new Date()).getHours();
      let timeOfDay = hourOfDay < 12 ? "morning" : hourOfDay < 18 ? "afternoon" : "evening";
      let greetingMessage = `Good ${timeOfDay}` + (name ? `, ${name}` : '');
      greeting = <Typography variant="h6" key="welcome-greeting">{ greetingMessage }</Typography>;
    }
    return greeting;
  }

  const appointmentDate = () => {
    let date = getVisitDate();
    return !date?.isValid ? "" : date.toLocaleString(DateTime.DATETIME_MED_WITH_WEEKDAY);
  }

  const appointmentAlert = () => {
    const eventLabel = displayText("eventLabel", AlertTitle);
    const time = appointmentDate();
    let location = getVisitInformation("location");
    let provider = getVisitInformation("provider", true);
    return (eventLabel && (time || location || provider)) ?
      <Alert severity="info" key="appointment-notification">
        {eventLabel}
        {time ? <> {time} </> : null}
        {location ? <> at {location}</> : null}
        {provider ? <> with {provider}</> : null}
      </Alert>
      : null;
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
      date = date.plus({ days: tokenLifetime ?? config?.daysRelativeToEventWhileSurveyIsValid ?? 0 }).endOf('day');

      // Get the date difference in the format: X days, Y hours and Z minutes,
      // skipping any time division that has a value of 0
      const diffs = date.diffNow(['days', 'hours', 'minutes']).toObject();
      let diffStrings = [];
      diffString("days", diffStrings, diffs);
      diffString("hours", diffStrings, diffs);
      diffString("minutes", diffStrings, diffs);

      if (diffStrings.length > 0) {
        result = "**This survey link will expire in " + diffStrings[0] + ".**";
      }
    } else {
      // Visit date could not be retrieved, this token will expire 1 hour from creation.
      result = "**This session will expire in 1 hour.**"
    }

    return result;
  }

  // The questionnaire set intro may reference data from the visit information form, with default values
  // For example @{visit.location:-UHN} specifies that the `location` field needs to be displayed,
  // and it should default to "UHN" if the field is empty.
  const fillInVisitData = (text) => {
    let pattern = /@\{visit\.([A-z0-9_]+)(:-(.+?))?\}/g;
    let match, result = text;
    while ((match = pattern.exec(text)) !== null) {
      result = result.replace(match[0], getVisitInformation(match[1], true) || match[3] || "");
    }
    return result;
  }

  // Replace all occurrences of the visit information pattern with the value from the Visit information form
  const introMessage = fillInVisitData(intro);

  const welcomeScreen = (isSubmitted || questionnaireIds?.length == 0) ? [
    greet(username),
    appointmentAlert(),
    displayText(
      isSubmitted ? "surveySubmittedMessage" : "noSurveysMessage",
      Typography,
      { color: "textSecondary", variant: "subtitle1", key: "survey-info" }
    ),
  ] : [
    greet(username),
    appointmentAlert(),
    (introMessage
      ? <FormattedText key="intro-message">{introMessage}</FormattedText>
      : displayText("surveyIntro", Typography, { key: "welcome-message" })
    ),
    <List key="welcome-surveys" disablePadding>
      { (questionnaireIds || []).map((q, i) => (
        <ListItem key={q+"Welcome"} disablePadding>
          <ListItemAvatar>
            {isFormDone(q) ? doneIndicator : questionnaireIds.length == 1 ? surveyIndicator : stepIndicator(i)}
          </ListItemAvatar>
          <ListItemText
            primary={getDisplayTitle(q)}
            secondary={isFormSubmitted(q) ? "Submitted" :
              !isFormDone(q) && (displayEstimate(q)
            + (["patient", "guest-patient"].includes(formData?.[q]?.["jcr:lastModifiedBy"]) ? " (in progress)" : ""))}
          />
        </ListItem>
      ))}
    </List>,
    nextQuestionnaire &&
      <Fab variant="extended" color="primary" onClick={launchNextForm} key="welcome-action" sx={{ px: 5 }}>Begin</Fab>,
    <FormattedText key="expiry-message" color="textSecondary">
      {expiryDate()}
    </FormattedText>,
    displayText("surveyDraftInfo", FormattedText, { key: "draft-info" }),
  ];

  const formScreen = [
    <Form
      key={crtStep}
      id={crtFormId}
      mode="edit"
      requireCompletion={canSubmitIncomplete ? false : undefined}
      disableHeader
      questionnaireAddons={nextQuestionnaire?.questionnaireAddons}
      doneIcon={nextQuestionnaire ? <NextStepIcon /> : <DoneIcon />}
      doneLabel={nextQuestionnaire ? "Next survey" : enableReviewScreen ? "Review" : "Submit my answers"}
      onDone={nextQuestionnaire ? launchNextForm : goToNextStep}
      doneButtonStyle={{ position: "relative", right: 0, bottom: "unset", textAlign: "center" }}
      contentOffset={formContentOffset}
    />
  ];

  const submitButton = (label) => (
    <Fab
      variant="extended"
      disabled={submissionInProgress}
      color="primary"
      onClick={() => onSubmit()}
      key={"review-submit"+label}
    >
      {submissionInProgress ? "Submitting...." : (label || "Submit")}
    </Fab>
  );

  const reviewScreen = !enableReviewScreen ? [
    <CircularProgress key="review-loading"/>
  ] : [
    <Typography variant="h4" key="review-title">Review and Submit</Typography>,
    submitButton("Submit now"),
    <Grid container direction="column" spacing={8} key="review-list">
      {(questionnaireIds || []).filter(q => !isFormSubmitted(q)).map((q, i) => (
        <Grid key={q+"Review"}>
          { previews?.[formData?.[q]?.["@name"]] ?
            <Paper elevation={0} className={classes.surveyPreviewComponent + (!isFormComplete(q) ? " incomplete" : "")}>
              <Grid container direction="column" spacing={2}>
                <Grid key="form-preview">
                  <FormattedText>{ previews?.[formData?.[q]?.["@name"]] }</FormattedText>
                </Grid>
                <Grid alignSelf="center" key="change-button">
                  <Button
                    variant="outlined"
                    onClick={() => {setReviewMode(true); setCrtFormId(formData?.[q]?.["@name"]); setCrtStep(i)}}>
                    Change
                  </Button>
                </Grid>
              </Grid>
            </Paper>
            :
            <CircularProgress />
          }
        </Grid>
      ))}
    </Grid>,
    submitButton("Submit survey")
  ];

  // Are there any response interpretations to display to the patient?
  const hasInterpretations = questionnaireIds.some(q => questionnaires?.[q]?.hasInterpretation);

  // Replace any occurence of a visit information field with its value per current visit
  const endingMessage = fillInVisitData(ending);

  const finalInstructions = (
    endingMessage ? <FormattedText key="summary-instructions">{endingMessage}</FormattedText> :
      displayText("summaryInstructions", FormattedText, { color: "textSecondary", key: "summary-instructions" })
  );

  const disclaimer = (
    displayText("disclaimer", Alert, { severity: "warning", key: "disclaimer" })
  );

  const summaryScreen = hasInterpretations ? [
    <Typography variant="h4" key="summary-title">Thank you</Typography>,
    finalInstructions,
    disclaimer,
    <Typography variant="h4" key="summary-intro">Interpreting your results</Typography>,
    displayText("interpretationInstructions", Typography, { color: "textSecondary", key: "summary-interpretation-instructions" }),
    <Grid container direction="column" spacing={3} key="summary-list">
      { (questionnaireIds || []).map((q, i) => (
        <Grid key={q+"Summary"}>
          {
            questionnaires?.[q]?.hasInterpretation ? <Form
              id={formData?.[q]?.['@name']}
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

  const loadingScreen = [ <CircularProgress key="exit-loading"/> ];

  const incompleteScreen = [
    <List key="incomplete-list" disablePadding>
      { (questionnaireIds || []).map((q, i) => (
        <ListItem key={q+"Exit"} disablePadding>
          <ListItemAvatar>
            {isFormDone(q) ? doneIndicator : isFormOptional(q) ? optionalIndicator : incompleteIndicator}
          </ListItemAvatar>
          <ListItemText
            primary={getDisplayTitle(q)}
            secondary={!isFormDone(q) && !isFormOptional(q) && "Incomplete" || isFormSubmitted(q) && "Submitted"}
          />
        </ListItem>
      ))}
    </List>,
    <Alert severity="error" key="incomplete-message">
      Your answers are incomplete. Please update your answers by responding to all mandatory questions.
    </Alert>,
    <Grid container spacing={2} justifyContent="flex-end" key="incomplete-actions">
      { canSubmitIncomplete &&
            <Grid>
              <Button variant="outlined" onClick={() => setSubmittingIncomplete(true)}>Proceed anyway</Button>
            </Grid>
      }
      <Grid>
        <Button variant="contained" onClick={() => setCrtStep(-1)} key="incomplete-button">Update my answers</Button>
      </Grid>
    </Grid>
  ];

  const exitScreen = (
    typeof(isComplete) == 'undefined'
      ? loadingScreen
      : ( isComplete || submittingIncomplete
        ? (isSubmitted ? summaryScreen : reviewScreen)
        : incompleteScreen
      )
  );

  const progress = Math.min(100, 100.0 * (crtStep + 1) / (questionnaireIds?.length || 1));

  const screenContent = (
    <>
      <Header
        key="title"
        title={title}
        greeting={username}
        withSignout={!!(config?.PIIAuthRequired)}
        progress={progress}
        subtitle={getDisplayTitle(questionnaireIds[crtStep])}
        step={stepIndicator(crtStep, true)}
      />
      <QuestionnaireSetScreen
        className={classes[screenType]}
        key="screen"
      >
        {
          crtStep == -1 ? welcomeScreen :
            crtStep < questionnaireIds.length ? formScreen :
              exitScreen
        }
      </QuestionnaireSetScreen>
      { (screenType != "survey") && <Footer /> }
    </>
  )

  // If we're on a screen displaying survey questions,
  // wrap the Header and the content in a Paper component,
  // to keep them from being spaced evenly across the screen.
  // The survey content will appear vertically aligned to the
  // top, while the information screens (welcome message,
  // review screen, final "Thank you" message will appear
  // vertically aligned to the middle.
  return (
    crtStep >= 0 && crtStep < questionnaireIds.length
      ? <Paper elevation={0}>{ screenContent }</Paper>
      : screenContent
  )
}

function QuestionnaireSetScreen (props) {
  let { children, ...rest } = props;

  const { classes } = useStyles();

  const isElementCentered = key => (
    ["welcome-action", "expiry-message", "exit-loading"].includes(key) || key?.startsWith("review-")
  );

  return (
    <Paper elevation={0} className={classes.mainContainer}>
      <Grid container direction="column" spacing={4} {...rest}>
        {Array.from(children || []).filter(c => c).map((c, i) =>
          <Grid
            key={i+"MainItem"}
            size={!isElementCentered(c.key) && 12}
            alignSelf={isElementCentered(c.key) && "center"}
            className={classes.mainItem}
          >
            {c}
          </Grid>)
        }
      </Grid>
    </Paper>
  );
}

export default QuestionnaireSet;
