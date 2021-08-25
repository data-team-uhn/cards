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
import { MuiThemeProvider } from '@material-ui/core/styles';
import { appTheme } from "./themePalette.jsx";
import QuestionnaireSet from "./QuestionnaireSet.jsx"
import PatientIdentification from "./MockPatientIdentification.jsx"

function PromsHomepage (props) {
  // Current subject
  const [ subject, setSubject ] = useState();

  let onPatientIdentified = (p) => {
    setSubject(p.subject);
  }

  if (!subject) {
    return <PatientIdentification onSuccess={onPatientIdentified} />;
  }

  // Obtain the id of the questionnaire set to display
  const promId = /Proms.html\/([^.\/]+)/.exec(location.pathname)?.[1];

  return (
    <QuestionnaireSet id={promId} subject={subject} />
  );
}

const hist = createBrowserHistory();
hist.listen(({action, location}) => window.dispatchEvent(new Event("beforeunload")));
ReactDOM.render(
  <MuiThemeProvider theme={appTheme}>
  <Router history={hist}>
    <Switch>
      <Route path="/Proms.html/" component={PromsHomepage} />
      <Redirect from="/Proms" to="/Proms.html/"/>
    </Switch>
  </Router>
  </MuiThemeProvider>,
  document.querySelector('#proms-container')
);

export default PromsHomepage;
