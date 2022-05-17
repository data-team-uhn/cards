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
import React, { useEffect, useState } from "react";
import ReactDOM from "react-dom";
import { Router, Route, Redirect, Switch } from "react-router-dom";
import { Paper, Grid, Button, Typography } from '@mui/material';
import makeStyles from '@mui/styles/makeStyles';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import { createBrowserHistory } from "history";
import { ThemeProvider, StyledEngineProvider, styled } from '@mui/material/styles';
import { appTheme } from "../themePalette.jsx";
import PromsFooter from "./Footer.jsx";
import ErrorPage from "../components/ErrorPage.jsx";

const PREFIX = 'Unsubscribe';

const classes = {
  paper: `${PREFIX}-paper`,
  logo: `${PREFIX}-logo`
};

const StyledUnsubscribe = styled(Unsubscribe)((
  {
    theme
  }
) => ({
  [`& .${classes.paper}`]: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: theme.spacing(12, 3, 3),
    textAlign: "center",
    "& .MuiGrid-item" : {
      textAlign: "center",
    },
  },

  [`& .${classes.logo}`]: {
    maxWidth: "240px",
  }
}));

function Unsubscribe (props) {
  // Current user and associated subject
  const [ confirmed, setConfirmed ] = useState(null);
  const [ error, setError ] = useState();
  const [ alreadyUnsubscribed, setAlreadyUnsubscribed ] = useState(false);


  let unsubscribe = (value) => {
    let request_data = new FormData();
    request_data.append("unsubscribe", value);
    fetch("/Proms.unsubscribe", { method: 'POST', body: request_data })
      .then( (response) => response.ok ? response.json() : Promise.reject(response) )
      .then( json => json.status == "success" ? (setConfirmed(json.unsubscribed), setAlreadyUnsubscribed(null)) : Promise.reject(json.error))
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

  useEffect(() => {
    fetch("/Proms.unsubscribe", {method: 'GET'})
      .then( (response) => response.ok ? response.json() : Promise.reject(response) )
      .then( json => json.status == "success" ? setAlreadyUnsubscribed(json.unsubscribed) : Promise.reject(json.error))
      .catch((response) => {
        let errMsg = "Cannot unsubscribe: ";
        setError(errMsg + (response.status ? response.statusText : response));
      });
  }, []);

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={appTheme}>
          <Paper className={classes.paper} elevation={0}>
            <Grid
              container
              direction="column"
              spacing={7}
              alignItems="center"
              alignContent="center"
            >
              <Grid item>
                <img src={document.querySelector('meta[name="logoLight"]').content} alt="" className={classes.logo}/>
              </Grid>
              <Grid item>
                { error && <Alert severity="error">
                  <AlertTitle>An error occurred</AlertTitle>
                   {error}
                  </Alert>
                }
                { alreadyUnsubscribed ?
                  <>
                    <Alert icon={false} severity="info">You are already unsubscribed.</Alert>
                    <Button
                      type="submit"
                      variant="contained"
                      color="primary"
                      className={classes.submit}
                      onClick={() => unsubscribe(0)}
                      >
                      Resubscribe
                    </Button>
                  </>
                  : confirmed !== null ?
                  <Alert icon={false} severity="info">
                    You have been {confirmed ? "unsubscribed" : "resubscribed"}.
                  </Alert>
                  :
                  <><Typography>This will unsubscribe you from receiving all emails via DATA-PRO. If you wish to unsubscribe from all UHN communication, please contact your care team.</Typography>
                  <Button
                    type="submit"
                    variant="contained"
                    color="primary"
                    className={classes.submit}
                    onClick={() => unsubscribe(1)}
                    >
                    Unsubscribe
                  </Button>
                  </>
                }
              </Grid>
            </Grid>
          </Paper>
        </ThemeProvider>
    </StyledEngineProvider>
  );
}

ReactDOM.render(
  <StyledUnsubscribe />,
  document.querySelector('#proms-unsubscribe-container')
);

export default Unsubscribe;
