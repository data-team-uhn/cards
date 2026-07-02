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
import { StrictMode, useEffect, useState } from "react";

import {
  Alert,
  AlertTitle,
  Button,
  Divider,
  Grid,
  Paper,
  Stack,
  Typography
} from '@mui/material';
import { ThemeProvider, StyledEngineProvider } from '@mui/material/styles';
import { createRoot } from 'react-dom/client';
import { makeStyles } from 'tss-react/mui';

import { portalTheme } from "./portalTheme.jsx";
import ErrorPage from "../components/ErrorPage.jsx";
import Logo from "../components/Logo.jsx";

const useStyles = makeStyles()(theme => ({
  paper: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'stretch',
    padding: theme.spacing(12, 0, 3),
    margin: "0 auto",
    width: theme.width.compact,
    // Magic number = content width + 16px on each side
    [theme.breakpoints.down(theme.width.compact + 32)]: {
      // 8px on each side are the `body` padding
      // subtract 16 more to achieve smooth transition when resizing the window below the content width
      width: "calc(100% - 16px)",
    },
  },
}));

// The two informational lines shown before the subscribe / unsubscribe action.
// The bold headline is passed as children; the resubscribe hint is always the same.
const StatusMessage = ({ children }) => (
  <div>
    <Typography variant="subtitle1" color="primary" sx={{ fontWeight: "bold" }}>
      { children }
    </Typography>
    <Typography variant="subtitle1" color="textSecondary">
      You can unsubscribe or resubscribe any time using this link.
    </Typography>
  </div>
);

// Submit-style action button; all variants share the same type, color and top margin.
const SubmitButton = ({ variant = "contained", onClick, children }) => (
  <Button type="submit" variant={variant} color="primary" onClick={onClick} sx={{ mt: 4 }}>
    { children }
  </Button>
);

function Unsubscribe (props) {
  // Current user and associated subject
  const [ confirmed, setConfirmed ] = useState(null);
  const [ error, setError ] = useState();
  const [ alreadyUnsubscribed, setAlreadyUnsubscribed ] = useState(false);
  const { classes } = useStyles();

  const params = new URLSearchParams(window.location.search);
  const patient = params.get("patient");
  const authToken = params.get("auth_token");

  useEffect(() => {
    if (!(patient || authToken)) {
      return;
    }
    fetch("/Survey.unsubscribe" + (patient ? `?patient=${patient}` : ""), { method: 'GET' })
      .then(async (response) => {
        if (response.ok) {
          return response.json();
        } else {
          // Try to read JSON error body
          let errorData;
          try {
            errorData = await response.json();
          } catch (e) {
            // Fallback if body is not JSON
            errorData = { error: response.statusText };
          }
          return Promise.reject(errorData);
        }
      })
      .then(json => {
        if (json.status === "success") {
          setAlreadyUnsubscribed(json.unsubscribed);
        } else {
          return Promise.reject(json.error);
        }
      })
      .catch(error => {
        // error now has access to a custom backend error data
        const errMsg = "Cannot unsubscribe: ";
        setError(errMsg + (error.error || error));
      });
  }, [patient, authToken]);

  if (!(patient || authToken)) {
    return (
      <ErrorPage
        title="Invalid access"
        message="This page can only be accessed by opening an invitation to fill in a survey"
      />
    );
  }

  const unsubscribe = (value) => {
    const request_data = new FormData();
    request_data.append("unsubscribe", value);
    patient && request_data.append("patient", patient);
    fetch("/Survey.unsubscribe", { method: 'POST', body: request_data })
      .then( (response) => response.ok ? response.json() : Promise.reject(response) )
      .then( json => json.status == "success" ? (setConfirmed(json.unsubscribed), setAlreadyUnsubscribed(null)) : Promise.reject(json.error))
      .catch((response) => {
        const errMsg = "Unsubscribing failed";
        setError(errMsg + (response.status ? ` with error code ${response.status}: ${response.statusText}` : response));
      });
  }

  const returnToSurvey = () => {
    window.location = "/Survey.html/" + (authToken ? `?auth_token=${authToken}` : "");
  };

  const appName = document.querySelector('meta[name="title"]')?.content;

  return (
    <Paper className={classes.paper} elevation={0}>
      <Grid
        container
        direction="column"
        alignItems="stretch"
        spacing={4}
      >
        <Logo component={Grid} />
        { appName &&
          <Grid>
            <Stack spacing={2}>
              <Typography variant="overline" component="h1" color="textSecondary" sx={{ fontWeight: "bold" }}>
                { appName }
              </Typography>
              <Divider />
            </Stack>
          </Grid>
        }
        <Grid>
          { error ?
            <>
              <Alert severity="error">
                <AlertTitle>An error occurred</AlertTitle>
                {error}
              </Alert>
              {authToken && <SubmitButton onClick={returnToSurvey}>
                Return to survey
              </SubmitButton>}
            </> : alreadyUnsubscribed ? <>
              <StatusMessage>
                { `You are already unsubscribed from all ${appName} emails.`}
              </StatusMessage>
              <SubmitButton variant="outlined" onClick={() => unsubscribe(0)}>
                Resubscribe
              </SubmitButton>
            </> : confirmed !== null ?
              <>
                <Alert severity="success">
                  You have been {confirmed ? "unsubscribed from" : "resubscribed to"} {appName}.
                </Alert>
                <SubmitButton onClick={() => unsubscribe(1 - confirmed)}>
                  {confirmed ? "Resubscribe" : "Unsubscribe"}
                </SubmitButton>
              </>
              :
              <>
                <StatusMessage>
                  { `This will unsubscribe you from all ${appName} emails.`}
                </StatusMessage>
                <SubmitButton onClick={() => unsubscribe(1)}>
                  Unsubscribe
                </SubmitButton>
              </>
          }
        </Grid>
      </Grid>
    </Paper>
  );
}

const root = createRoot(document.querySelector('#patient-portal-unsubscribe-container'));
root.render(
  <StrictMode>
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={portalTheme}>
        <Unsubscribe />
      </ThemeProvider>
    </StyledEngineProvider>
  </StrictMode>
);

export default Unsubscribe;
