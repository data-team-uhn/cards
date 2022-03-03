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
import { Paper, Grid, Button, Typography, makeStyles } from '@material-ui/core';
import { Alert, AlertTitle } from "@material-ui/lab";
import { createBrowserHistory } from "history";
import { MuiThemeProvider } from '@material-ui/core/styles';
import { appTheme } from "../themePalette.jsx";
import PromsFooter from "./Footer.jsx";
import ErrorPage from "../components/ErrorPage.jsx";

const useStyles = makeStyles(theme => ({
  paper: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: theme.spacing(12, 3, 3),
    textAlign: "center",
    "& .MuiGrid-item" : {
      textAlign: "center",
    },
  },
  logo: {
    maxWidth: "240px",
  },
}));

function Unsubscribe (props) {
  // Current user and associated subject
  const [ confirmed, setConfirmed ] = useState(false);
  const [ error, setError ] = useState();
  const classes = useStyles();
  let unsubscribe = () => {
    fetch("/Proms.unsubscribe.html", {method: 'POST'})
      .then( (response) => response.ok ? response.json() : Promise.reject(response) )
      .then( json => json.status == "success" ? setConfirmed(true) : Promise.reject(json.error))
      .catch((response) => {
        let errMsg = "Unsubscribing failed";
        setError(errMsg + (response.status ? ` with error code ${response.status}: ${response.statusText}` : response));
      });
  }

  if (!("hasSessionSubject" in document.getElementById("proms-unsubscribe-container").dataset)) {
    return (
      <ErrorPage
        title="Invalid access"
        message="This page can only be accessed by opening an invitation to fill in a survey"
        buttonLink="/content.html/Questionnaires/User"
        buttonLabel="Go to the dashboard"
      />
    );
  }

  return <MuiThemeProvider theme={appTheme}>
      <Paper className={classes.paper} elevation={0}>
        <Grid
          container
          direction="column"
          spacing={7}
          alignItems="center"
          alignContent="center"
        >
          <Grid item>
            <img src="/libs/cards/resources/logo_light_bg.png" alt="this.state.title" className={classes.logo}/>
          </Grid>
          <Grid item>
            { error && <Alert severity="error">
              <AlertTitle>An error occurred</AlertTitle>
               {error}
              </Alert>
            }
            { confirmed ?
              <Alert icon={false} severity="info">
                You have been unsubscribed.
              </Alert>
              :
              <><Typography>This will unsubscribe you from receiving all emails via DATA-PRO. If you wish to unsubscribe from all UHN communication, please contact your care team.</Typography>
              <Button
                type="submit"
                variant="contained"
                color="primary"
                className={classes.submit}
                onClick={unsubscribe}
                >
                Unsubscribe
              </Button>
              </>
            }
          </Grid>
        </Grid>
      </Paper>
    </MuiThemeProvider>
}

ReactDOM.render(
  <Unsubscribe />,
  document.querySelector('#proms-unsubscribe-container')
);

export default Unsubscribe;
