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
import { useState, useEffect } from "react";

import createCache from "@emotion/cache";
import { CacheProvider } from "@emotion/react";
import { ThemeProvider } from '@mui/material/styles';
import { createBrowserHistory } from "history";
import { createRoot } from 'react-dom/client';
import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router";

import Footer from "./Footer.jsx";
import PatientIdentification from "./PatientIdentification.jsx";
import { portalTheme } from "./portalTheme.jsx";
import QuestionnaireSet from "./QuestionnaireSet.jsx";
import { DEFAULT_INSTRUCTIONS, SURVEY_INSTRUCTIONS_PATH } from "./SurveyInstructionsConfiguration.jsx"

const CONFIG = "/Survey/PatientAccess.json";

function PatientPortalHomepage (props) {
  // Current user and associated subject
  const [ username, setUsername ] = useState("");
  const [ subject, setSubject ] = useState();
  // Patient Survey UI texts from Patient Portal Survey Instructions
  const [ surveyInstructions, setSurveyInstructions ] = useState();
  const [ accessConfig, setAccessConfig ] = useState({});

  // Fetch saved settings for Patient Portal Survey Instructions
  useEffect(() => {
    fetch(`${SURVEY_INSTRUCTIONS_PATH}.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let instructions = { ...json };
        Object.entries(instructions).forEach(([k,v]) => !v && delete instructions[k]);
        setSurveyInstructions({ ...DEFAULT_INSTRUCTIONS, ...instructions });
      })
      .catch((response) => {
        console.error(
          `Loading the Patient Portal Survey Instructions failed with error code ` +
          `${response.status}: ${response.statusText}`
        );
      });
  }, []);

  // Fetch the patient access configuration
  useEffect(() => {
    fetch(CONFIG)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setAccessConfig(json);
      });
  }, []);

  let displayText = (key, Component, { key: propKey, ...rest } = {}) => (
    surveyInstructions?.[key] ?
      Component ?
        <Component key={propKey} {...rest}>{surveyInstructions[key]}</Component>
        : surveyInstructions[key]
      : null
  );

  let onPatientIdentified = (p) => {
    setUsername(`${p?.first_name || ""} ${p?.last_name || ""}`.trim());
    setSubject(p?.subject);
  }

  if (!subject) {
    return (<>
      <PatientIdentification onSuccess={onPatientIdentified} displayText={displayText} config={accessConfig}/>
      <Footer />
    </>);
  }

  return (<>
    <QuestionnaireSet subject={subject} username={username} displayText={displayText} config={{
      ...accessConfig,
      ...surveyInstructions
    }} />
    <Footer />
  </>);
}

const cache = createCache({
  key: 'tss',
  // Enable style speedy insertion mode
  speedy: true
});

const hist = createBrowserHistory();
hist.listen(({ action, location }) => window.dispatchEvent(new Event("beforeunload")));
const root = createRoot(document.querySelector('#patient-portal-container'));
root.render(
  <CacheProvider value={cache}>
    <ThemeProvider theme={portalTheme}>
      <Router history={hist}>
        <Routes>
          <Route path="/Survey.html/" element={<PatientPortalHomepage />}/>
          <Route path="/Survey" element={<Navigate replace to="/Survey.html/" />}/>
          <Route path="/" element={<Navigate replace to="/Survey.html/" />}/>
        </Routes>
      </Router>
    </ThemeProvider>
  </CacheProvider>
);

export default PatientPortalHomepage;
