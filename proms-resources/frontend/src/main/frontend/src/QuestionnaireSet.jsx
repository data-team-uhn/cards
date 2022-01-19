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
import NextStepIcon from '@material-ui/icons/ChevronRight';
import DoneIcon from '@material-ui/icons/Done';
import WarningIcon from '@material-ui/icons/Warning';

import Form from "./questionnaire/Form.jsx";
import ResourceHeader from "./questionnaire/ResourceHeader.jsx";

import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

const useStyles = makeStyles(theme => ({
  mainContainer: {
    margin: theme.spacing(2),
  },
  screen : {
    alignItems: "center",
    justify: "center",
    "& h4, h6" : {
      textAlign: "center",
    }
  },
  stepIndicator : {
    background: theme.palette.primary.main,
  },
  incompleteIndicator : {
    border: "1px solid " + theme.palette.secondary.main,
    background: "transparent",
    color: theme.palette.secondary.main,
  },
  doneIndicator : {
    border: "1px solid " + theme.palette.primary.main,
    background: "transparent",
    color: theme.palette.primary.main,
  },
  survey : {
    alignItems: "stretch",
    justify: "space-between",
    flexWrap: "nowrap",
  },
  formSpacer : {
    marginTop: "1em"
  },
  formTitle : {
    marginTop: "1em",
  },
  updateButton : {
    marginTop: "1em",
    marginBottom: "4em",
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
  const { id, subject, contentOffset } = props;

  // Questionnaire set title, to display to the patient user
  const [ title, setTitle ] = useState();
  // The ids of the questionnaires in this set
  const [ questionnaireSetIds, setQuestionnaireSetIds ] = useState();
  // The ids of the questionnaires displayed to the patient
  const [ questionnaireIds, setQuestionnaireIds ] = useState();
  // Map questionnaire id -> title, path and optional time estimate (in minutes) for filling it out
  const [ questionnaires, setQuestionnaires ] = useState();

  // Data already associated with the subject
  const [ subjectData, setSubjectData ] = useState();
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

  // Find the data already associated with the subject
  useEffect(() => {
    if (!questionnaires) return;
    loadExistingData();
  }, [questionnaires]);

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
  }, [crtStep, questionnaires, subjectData]);

  // If we're back to the start because the user was directed to add missing answers,
  // dont't show the welcome screen and skip to the next step without them pressing start
  useEffect(() => {
    if (crtStep == -1 && endReached) {
      nextQuestionnaire && launchNextForm();
    }
  }, [crtStep, endReached, nextQuestionnaire]);

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
  }, [crtStep, questionnaireSetIds]);

  // Determine if all surveys have been filled out
  useEffect(() => {
    if (!subjectData || !questionnaireIds) return;
    setComplete(Object.keys(subjectData || {}).filter(q => isFormComplete(q)).length == questionnaireIds.length);
  }, [subjectData, questionnaireIds]);

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
  }, [visitInformation]);

  // When the user lands on a completed visit that has not been submit, proceed to reviewing their forms
  useEffect(() => {
    if(isComplete && !isSubmitted && questionnaireIds && crtStep == -1) {
      setCrtStep(questionnaireIds.length)
    }
  }, [isComplete, isSubmitted, questionnaireIds])

  const loadExistingData = () => {
    setComplete(undefined);
    fetchWithReLogin(globalLoginDisplay, `${subject}.data.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let ids = [];
        let data = {};
        questionnaireSetIds.forEach(q => {
          if (json[questionnaires[q]?.title]?.[0]?.['jcr:primaryType'] == "cards:Form") {
            data[q] = json[questionnaires[q]?.title][0];
            ids.push(q);
          }
        });
        setVisitInformation(json[visitInformationFormTitle]?.[0] || {});
        setQuestionnaireIds(ids);
        setSubjectData(data);
      })
      .catch((response) => {
        setError(`Loading the visit failed with error code ${response.status}: ${response.statusText}`);
      });
  }

  const loadQuestionnaireSet = () => {
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
        setQuestionnaireSetIds(null);
      });
  }

  let parseQuestionnaireSet = (json) => {
    // Extract the title
    setTitle(json.name);

    // Extract the ids
    setQuestionnaireSetIds(
      Object.values(json || {})
        .filter(value => value['jcr:primaryType'] == 'cards:QuestionnaireRef')
        .sort((a, b) => (a.order - b.order))
        .map(value => value.questionnaire['@name'])
    );

    // Map the relevant questionnaire info
    let data = {};
    Object.values(json || {})
      .filter(value => value['jcr:primaryType'] == 'cards:QuestionnaireRef')
      .forEach(value => { data[value.questionnaire['@name']] = {
        'title': value.questionnaire?.title || value.questionnaire?.['@name'],
        '@path': value.questionnaire?.['@path'],
        '@name': value.questionnaire?.['@name'],
        'estimate': value.estimate
       }});
    setQuestionnaires(data);
  };

  // Find the next step : Skip questionnaires that have already been filled out
  let findNextStep = (step) => {
    let next = step + 1
    // Skip if the corresponding questionnaire has already been filled out:
    while (next < questionnaireIds.length && isFormComplete(questionnaireIds[next])) ++next;
    return next;
  }

  // Advance to the next step
  let nextStep = () => { setCrtStep(findNextStep) }

  let isFormComplete = (questionnaireId) => {
    return subjectData?.[questionnaireId] && !subjectData[questionnaireId].statusFlags?.includes("INCOMPLETE");
  }

  let launchNextForm = () => {;
    if (subjectData[nextQuestionnaire['@name']]) {
      // Form already exists and is incomplete: prepare to edit it
      setCrtFormId(subjectData[nextQuestionnaire['@name']]['@name']);
    }
  }

  // Load all questionnaires that need to be filled out
  if (typeof questionnaireIds === "undefined") {
    loadQuestionnaireSet();
  }

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

  if (!questionnaires || !subjectData) {
    return (
      <QuestionnaireSetScreen className={classes.screen}>
        <CircularProgress />
      </QuestionnaireSetScreen>
    );
  }

  let stepIndicator = (step, withTotal) => {
    return (
      questionnaireIds?.length > 1 ?
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
    questionnaireIds.forEach(q => {
      let id = subjectData[q]?.["@name"];
      const URL = "/Forms/" + id;
      var request_data = new FormData();
      request_data.append(":checkin", "true");
      fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
        .then( (response) => {
          if (!response.ok) {
            return(Promise.reject(response));
          }
        })
        .catch((response) => {
          setError(`Failed to check in form with error code ${response.status}: ${response.statusText}`);
        });
    });
  }

  let doneIndicator = <Avatar className={classes.doneIndicator}><DoneIcon /></Avatar>;

  let incompleteIndicator = <Avatar className={classes.incompleteIndicator}><WarningIcon /></Avatar>;

  let welcomeScreen = [
    <Typography variant="h4">{title}</Typography>,
    <List>
    { (questionnaireIds || []).map((q, i) => (
      <ListItem key={q+"Welcome"}>
        <ListItemAvatar>{isFormComplete(q) ? doneIndicator : stepIndicator(i)}</ListItemAvatar>
        <ListItemText
          primary={questionnaires[q]?.title}
          secondary={!isFormComplete(q) && (displayEstimate(q)
            + (subjectData[q]?.["jcr:lastModifiedBy"] === "patient" ? " (in progress)" : ""))}
        />
      </ListItem>
    ))}
    </List>,
    isComplete && isSubmitted ? <Typography color="textSecondary" variant="h4">You already completed the survey</Typography> :
    nextQuestionnaire && <Fab variant="extended" color="primary" onClick={launchNextForm}>Start</Fab>
  ];

  let formScreen = [
    <Grid container direction="column" spacing={4} className={classes.survey}>
      <ResourceHeader
        title={questionnaires[questionnaireIds[crtStep]]?.title || ""}
        breadcrumbs={[<>{title}</>]}
        separator=":"
        action={stepIndicator(crtStep, true)}
        contentOffset={contentOffset || 0}
       />
      <Grid item>
        <Form
          key={crtStep}
          id={crtFormId}
          mode="edit"
          disableHeader
          doneIcon={nextQuestionnaire ? <NextStepIcon /> : <DoneIcon />}
          doneLabel={nextQuestionnaire ? `Continue to ${nextQuestionnaire?.title}` : "Review my answers"}
          onDone={nextQuestionnaire ? launchNextForm : nextStep}
          doneButtonStyle={{position: "relative", right: 0, bottom: "unset", textAlign: "center"}}
          contentOffset={contentOffset || 0}
        />
      </Grid>
    </Grid>
  ];

  let reviewScreen = <>
    <Typography variant="h4">Please review your answers before final submission</Typography>
      {(questionnaireIds || []).map((q, i) => (
        <Grid item key={q+"Review"}>
          <Typography variant="h5" className={classes.formTitle}>{questionnaires[q].title || questionnaires[q]["@name"]}</Typography>
          <Form
            className={classes.formSpacer}
            id={subjectData[q]['@name']}
            disableHeader
            disableButton
            contentOffset={contentOffset || 0}
          />
          <Button
            variant="outlined"
            color="secondary"
            className={classes.updateButton}
            onClick={() => {setReviewMode(true); setCrtFormId(subjectData[q]["@name"]); setCrtStep(i)}}>
              Update this survey
          </Button>
      </Grid>
      ))}
    <div className={classes.reviewFab}>
      <Fab variant="extended" color="primary" onClick={() => {onSubmit()}}>Submit my Answers</Fab>
    </div>
  </>

  let summaryScreen = [
      <Typography variant="h4">Thank you for your submission.</Typography>,
      <Typography color="textSecondary">Please note:</Typography>,
      <ul>
        <li key="0"><Typography color="textSecondary">
For your privacy and security, once this screen is closed the information below will not be accessible until the day of
your appointment with your provider. Please print or note this information for your reference.
        </Typography></li>
        <li key="1"><Typography color="textSecondary">
Your responses may not be reviewed by your care team until the day of your next appointment. If your symptoms are
worsening while waiting for your next appointment, please proceed to your nearest Emergency Department today, or call
911.
        </Typography></li>
      </ul>,
      <Typography variant="h4">Interpreting your results</Typography>,
      <Typography color="textSecondary">There are different actions you can take now depending on how you have scored
your symptoms. Please see below for a summary of your scores and suggested actions.</Typography>,
      <Grid item>
      { (questionnaireIds || []).map((q, i) => (
        <Form
          className={classes.formSpacer}
          key={q+"Summary"}
          id={subjectData[q]['@name']}
          mode="summary"
          disableHeader
          disableButton
          contentOffset={contentOffset || 0}
        />
      ))}
      </Grid>
    ];

  let exitScreen = (typeof(isComplete) == 'undefined') ? [
    <CircularProgress />
  ] : [
    isComplete ? (isSubmitted ? summaryScreen : reviewScreen)
      : [
        <Typography variant="h4">{title}</Typography>,
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
        <Typography color="error">Your answers are incomplete. Please return to the main screen and check for any mandatory questions you may have missed.</Typography>,
        <div className={classes.updateButton}>
          <Fab variant="extended" color="primary" onClick={() => {setCrtStep(-1)}}>Update answers</Fab>
        </div>
      ]
  ];

  return (
      <QuestionnaireSetScreen className={classes[screenType]}>
        {
          crtStep == -1 ? welcomeScreen :
          crtStep < questionnaireIds.length ? formScreen :
          exitScreen
        }
      </QuestionnaireSetScreen>
  )
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
