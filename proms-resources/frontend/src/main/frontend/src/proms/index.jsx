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
import React, { useState } from "react";
import ReactDOM from "react-dom";
import { Router, Route, Redirect, Switch } from "react-router-dom";
import { createBrowserHistory } from "history";
import { ThemeProvider, StyledEngineProvider } from '@mui/material/styles';
import { appTheme } from "../themePalette.jsx";
import QuestionnaireSet from "./QuestionnaireSet.jsx";
import PatientIdentification from "./PatientIdentification.jsx";
import PromsFooter from "./Footer.jsx";
import ErrorPage from "../components/ErrorPage.jsx";

function PromsHomepage (props) {
  // Current user and associated subject
  const [ username, setUsername ] = useState("");
  const [ subject, setSubject ] = useState();

  let onPatientIdentified = (p) => {
    setUsername(`${p?.first_name || ""} ${p?.last_name || ""}`.trim());
    setSubject(p?.subject);
  }

  if (!subject) {
    return (<>
      <PatientIdentification onSuccess={onPatientIdentified} />
      <PromsFooter />
    </>);
  }

  return (<>
    <QuestionnaireSet subject={subject} username={username}/>
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
