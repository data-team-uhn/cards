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
import {
  AppBar,
  DialogContent,
  Link,
  Toolbar,
  Typography,
  makeStyles,
} from "@material-ui/core";
import { createBrowserHistory } from "history";
import { MuiThemeProvider } from '@material-ui/core/styles';
import { appTheme } from "./themePalette.jsx";
import QuestionnaireSet from "./QuestionnaireSet.jsx";
import PatientIdentification from "./MockPatientIdentification.jsx";
import FormattedText from "./components/FormattedText.jsx";
import ResponsiveDialog from "./components/ResponsiveDialog";

const useStyles = makeStyles(theme => ({
  appbar : {
    margin: theme.spacing(-1, -1, 4),
    padding: theme.spacing(0, 1),
    boxSizing: "content-box",
  },
  toolbar : {
    display: "flex",
    justifyContent: "space-between",
  },
  logo : {
    maxHeight: theme.spacing(4),
  },
  tou : {
    verticalAlign: "text-bottom",
    left: theme.spacing(0.5),
  }
}));

function PromsHomepage (props) {
  // Current user and associated subject
  const [ username, setUsername ] = useState("");
  const [ subject, setSubject ] = useState();
  const [ showTou, setShowTou ] = useState(false);

  const classes = useStyles();

  const tou = require('./TOU.json');

  let onPatientIdentified = (p) => {
    setUsername(`${p.first_name} ${p.last_name}`);
    setSubject(p.subject);
  }

  if (!subject) {
    return <PatientIdentification onSuccess={onPatientIdentified} />;
  }

  // Obtain the id of the questionnaire set to display
  const promId = /Proms.html\/([^.\/]+)/.exec(location.pathname)?.[1];

  return (<>
    <AppBar position="static" className={classes.appbar}>
      <Toolbar variant="dense" className={classes.toolbar}>
        <div>
          <img src="/libs/cards/resources/logo.png" alt="logo" className={classes.logo} />
          <Link
            component="button"
            color="inherit"
            variant="body2"
            underline="always"
            onClick={() => {setShowTou(true);}}
            className={classes.tou}
          >
            Terms of Use
          </Link>
        </div>
        { username &&
          <Typography variant="h6" color="inherit">
            Hello, {username}
          </Typography>
        }
      </Toolbar>
    </AppBar>
    <QuestionnaireSet id={promId} subject={subject} />
    <ResponsiveDialog
      title="Terms of Use"
      open={showTou}
      withCloseButton
      width="lg"
      onClose={() => {setShowTou(false);}}
    >
     <DialogContent dividers>
       <FormattedText variant="caption">{tou.text}</FormattedText>
     </DialogContent>
   </ResponsiveDialog>
  </>);
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
