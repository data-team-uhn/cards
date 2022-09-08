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
import React, { useState, useEffect } from "react";
import ReactDOM from "react-dom";
import { Router, Route, Redirect, Switch } from "react-router-dom";
import { createBrowserHistory } from "history";
import { ThemeProvider, StyledEngineProvider } from '@mui/material/styles';
import { appTheme } from "../themePalette.jsx";
import QuestionnaireSet from "./QuestionnaireSet.jsx";
import PatientIdentification from "./PatientIdentification.jsx";
import PromsFooter from "./Footer.jsx";
import ErrorPage from "../components/ErrorPage.jsx";

import { DEFAULT_INSTRUCTIONS, SURVEY_INSTRUCTIONS_PATH } from "./SurveyInstructionsConfiguration.jsx"

const CONFIG = "/Proms/PatientIdentification.json";
const TOKENLESS_AUTH_ENABLED_PROP = "tokenlessAuthEnabled";
const AUTH_TOKEN_PARAM = "auth_token";
const EXPIRY_OFFSET_PARAM = "allowedPostVisitCompletionTime";

function PromsHomepage (props) {
  // Current user and associated subject
  const [ username, setUsername ] = useState("");
  const [ subject, setSubject ] = useState();
  // Patient Survey UI texts from Patient Portal Survey Instructions
  const [ surveyInstructions, setSurveyInstructions ] = useState();
  const [ unableToProceed, setUnableToProceed ] = useState();
  const [ expiryOffset, setExpiryOffset ] = useState();

  // Fetch saved settings for Patient Portal Survey Instructions
  useEffect(() => {
    fetch(`${SURVEY_INSTRUCTIONS_PATH}.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setSurveyInstructions(Object.assign(DEFAULT_INSTRUCTIONS, json));
      })
      .catch((response) => {
        console.error(`Loading the Patient Portal Survey Instructions failed with error code ${response.status}: ${response.statusText}`);
      });
  }, []);

  // Fetch tokenless auth
  useEffect(() => {
    fetch(CONFIG)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setExpiryOffset(json[EXPIRY_OFFSET_PARAM]);

        let auth_token = new URLSearchParams(window.location.search).get(AUTH_TOKEN_PARAM);
        if (!(json[TOKENLESS_AUTH_ENABLED_PROP] || auth_token)) {
          // The user cannot continue without an authentication token
          setUnableToProceed(true);
        }
      });
  }, []);

  let displayText = (key, Component, props) => (
    surveyInstructions?.[key] ?
      Component ?
        <Component {...props}>{surveyInstructions[key]}</Component>
      : surveyInstructions[key]
    : null
  );

  let onPatientIdentified = (p) => {
    setUsername(`${p?.first_name || ""} ${p?.last_name || ""}`.trim());
    setSubject(p?.subject);
  }

  if (unableToProceed) {
    let appName = document.querySelector('meta[name="title"]')?.content;
    let message = (surveyInstructions.welcomeMessage)?.replaceAll("APP_NAME", appName) || '';
    message = `${message}\n\n### To fill out surveys, please follow the personalized link that was emailed to you.`;
    return (
      <ErrorPage
        sx={{maxWidth: 500, margin: "0 auto"}}
        title=""
        message={message}
        messageColor="textPrimary"
      />)
  }

  if (!subject) {
    return (<>
      <PatientIdentification onSuccess={onPatientIdentified} displayText={displayText}/>
      <PromsFooter />
    </>);
  }

  return (<>
    <QuestionnaireSet subject={subject} username={username} displayText={displayText} expiryOffset={expiryOffset}/>
    <PromsFooter />
  </>);
}

const hist = createBrowserHistory();
hist.listen(({action, location}) => window.dispatchEvent(new Event("beforeunload")));
ReactDOM.render(
  <StyledEngineProvider injectFirst>
    <ThemeProvider theme={appTheme}>
      <Router history={hist}>
        <Switch color="secondary">
          <Route path="/Proms.html/" component={PromsHomepage} />
          <Redirect from="/Proms" to="/Proms.html/"/>
        </Switch>
      </Router>
    </ThemeProvider>
  </StyledEngineProvider>,
  document.querySelector('#proms-container')
);

export default PromsHomepage;
