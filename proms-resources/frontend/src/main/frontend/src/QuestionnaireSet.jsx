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
import { MuiThemeProvider } from '@material-ui/core/styles';
import { appTheme } from "./themePalette.jsx";

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
  survey : {
    alignItems: "stretch",
    justify: "space-between",
    flexWrap: "nowrap",
  },
}));

function QuestionnaireSet(props) {
  const { id, subject, contentOffset } = props;

  // Questionnaire set title, to display to the patient user
  const [ title, setTitle ] = useState();
  // The ids of the questionnaires in this set
  const [ questionnaireIds, setQuestionnaireIds ] = useState();
  // Map questionnaire id -> title, path and optional time estimate (in minutes) for filling it out
  const [ questionnaires, setQuestionnaires ] = useState();

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
  const [ screenType, setScreenType ] = useState ();

  const classes = useStyles();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // Determine the screen type (and style) based on the step number
  useEffect(() => {
    setScreenType(crtStep >= 0 && crtStep < questionnaireIds.length ? "survey" : "screen");
  }, [crtStep]);

  // Determine the next questionnaire that needs to be filled out
  useEffect(() => {
    if (!questionnaires) return;
    setNextQuestionnaire(crtStep < questionnaireIds?.length-1 ? questionnaires[questionnaireIds?.[crtStep + 1]] : null);
  }, [crtStep, questionnaires]);

  // When the uuid of the next form is set, launch the form
  useEffect(() => {
    launchCrtForm()
  }, [crtFormId]);

  const loadQuestionnaireSet = () => {
    fetchWithReLogin(globalLoginDisplay, `/Proms/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        parseQuestionnaireSet(json);
      })
      .catch((response) => {
        setError(`Loading the survey failed with error code ${response.status}: ${response.statusText}`);
        setQuestionnaireIds(null);
      });
  }

  let parseQuestionnaireSet = (json) => {
    // Extract the title
    setTitle(json.name);

    // Extract the ids
    setQuestionnaireIds(
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
        'estimate': value.estimate
       }});
    setQuestionnaires(data);
  };

  let launchNextForm = () => {
    setCrtFormId(uuidv4())
    // ^This will trigger the effect that calls launchCrtForm
  }

  let launchCrtForm = () => {
    if (!crtFormId) return;
    // Make a POST request to create a new form, with a randomly generated UUID
    const URL = "/Forms/" + crtFormId;
    var request_data = new FormData();
    request_data.append('jcr:primaryType', 'cards:Form');
    request_data.append('questionnaire', nextQuestionnaire["@path"]);
    request_data.append('questionnaire@TypeHint', 'Reference');
    request_data.append('subject', subject);
    request_data.append('subject@TypeHint', 'Reference');
    fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
      .then( (response) => {
        if (response.ok) {
          // Advance to the next step
          setCrtStep((s) => (s+1));
        } else {
          return(Promise.reject(response));
        }
      })
      .catch((response) => {
        setError(`New form request failed with error code ${response.status}: ${response.statusText}`);
      });
  }

  // Load all questionnaires that need to be filled out
  if (typeof questionnaireIds === "undefined") {
    loadQuestionnaireSet();
  }

  if (!id) {
    return (
      <QuestionnaireSetScreen className={classes.screen} items={[
        <Typography variant="h4" color="textSecondary">You do not have any pending surverys</Typography>
      ]}>
      </QuestionnaireSetScreen>
    );
  }

  if (error) {
    return (
      <QuestionnaireSetScreen className={classes.screen} items={[
        <Typography variant="h4" color="error">{error}</Typography>
      ]}>
      </QuestionnaireSetScreen>
    );
  }

  if (!questionnaires) {
    return (
      <QuestionnaireSetScreen className={classes.screen} items={[
        <CircularProgress />
      ]}>
      </QuestionnaireSetScreen>
    );
  }

  let stepIndicator = (step, withTotal) => {
    return (
      questionnaireIds?.length > 1 ?
      <Avatar className={classes.stepIndicator}>{step + 1}{withTotal ? ("/" + questionnaireIds?.length) : ""}</Avatar>
      : <></>);
  }

  let welcomeScreen = [
    <Typography variant="h4">{title}</Typography>,
    <List>
    { (questionnaireIds || []).map((q, i) => (
      <ListItem key={q}>
        <ListItemAvatar>{stepIndicator(i)}</ListItemAvatar>
        <ListItemText primary={questionnaires[q]?.title} secondary={questionnaires[q]?.estimate && `${questionnaires[q].estimate} minutes`} />
      </ListItem>
    ))}
    </List>,
    nextQuestionnaire && <Fab variant="extended" color="primary" onClick={launchNextForm}>Start</Fab>
  ];

  let formScreen = [
    <Grid container direction="column" spacing={4} className={classes.survey}>
      <ResourceHeader
        title={questionnaires[questionnaireIds[crtStep]]?.title || ""}
        breadcrumbs={[<>{title}</>]}
        separator=":"
        action={stepIndicator(crtStep, true)}
        contentOffset={contentOffset}
       />
      <Grid item>
        <Form
          key={crtStep}
          id={crtFormId}
          mode="edit"
          disableHeader
          doneIcon={nextQuestionnaire ? <NextStepIcon /> : <DoneIcon />}
          doneLabel={nextQuestionnaire ? `Continue to ${nextQuestionnaire?.title}` : "Finish"}
          onDone={nextQuestionnaire ? launchNextForm : (() => setCrtStep(s => s+1))}
          doneButtonStyle={{position: "relative", right: 0, bottom: "unset", textAlign: "center"}}
          contentOffset={contentOffset}
        />
      </Grid>
    </Grid>
  ];

  let exitScreen = [
    <Typography variant="h4">{title}</Typography>,
    <List>
    { (questionnaireIds || []).map((q, i) => (
      <ListItem key={q}>
        <ListItemAvatar><DoneIcon /></ListItemAvatar>
        <ListItemText primary={questionnaires[q]?.title} />
      </ListItem>
    ))}
    </List>,
    <Typography color="textSecondary">Your data has been submitted.</Typography>,
    <Typography variant="h4">Thank you</Typography>
  ];

  return (
      <QuestionnaireSetScreen
        className={classes[screenType]}
        items={
          crtStep == -1 ? welcomeScreen :
          crtStep < questionnaireIds.length ? formScreen :
          exitScreen
        }
      >
      </QuestionnaireSetScreen>
  )
}

function QuestionnaireSetScreen (props) {
  let { items, ...rest } = props;

  const classes = useStyles();

  return (
  <Paper elevation={0} className={classes.mainContainer}>
    <Grid container direction="column" spacing={4} {...rest}>
      {Array.from(items || []).filter(c => c).map((c, i) => <Grid item key={i} xs={12}>{c}</Grid>)}
    </Grid>
  </Paper>
  );
}

export default QuestionnaireSet;
