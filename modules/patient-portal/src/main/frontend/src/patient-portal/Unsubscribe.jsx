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
import { createRoot } from 'react-dom/client';
import {
  Alert,
  AlertTitle,
  Button,
  Checkbox,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemButton,
  ListItemText,
  Paper,
  Typography
} from '@mui/material';
import { makeStyles } from 'tss-react/mui';
import { ThemeProvider, StyledEngineProvider } from '@mui/material/styles';
import { appTheme } from "../themePalette.jsx";
import ErrorPage from "../components/ErrorPage.jsx";
import Logo from "../components/Logo.jsx";

const useStyles = makeStyles()(theme => ({
  paper: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: theme.spacing(3, 3, 3),
    maxWidth: 500,
    width: "100%",
    margin: "0 auto",
    "& > .MuiGrid-root" : {
      width: "100%",
    },
  },
  sizeSmall : {
    padding: "0!important",
  },
  denseIcon : {
    minWidth: theme.spacing(4),
  },
  submit : {
    marginLeft: theme.spacing(1),
    float: 'right',
  },
  stepIndicator : {
    border: "1px solid " + theme.palette.action.disabled,
    background: "transparent",
    color: theme.palette.text.disabled,
    fontSize: "small",
    fontWeight: "bold",
  }
}));

function Unsubscribe (props) {
  const [ showAllAlert, setShowAllAlert ] = useState(false);
  const [ showListAlert, setShowListAlert ] = useState(false);
  const [ error, setError ] = useState();
  const [ unsubscribedAll, setUnsubscribedAll ] = useState(false);
  const [ unsubscribedList, setUnsubscribedList ] = useState([]);
  const [ initiated, setInitiated ] = useState();
  const { classes } = useStyles();
  const [ clinics, setClinics ] = useState();

  // get all of the available clinics list
  useEffect(() => {
    fetch("Survey/ClinicMapping.paginate?limit=1000", {method: 'GET'})
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setClinics(json?.rows);
      })
      .catch(() => setError("Can not load clinics data"));
  }, []);

  // get the list of clinincs that patient has unsubscribed from
  useEffect(() => {
    if (!clinics) return;
    fetchInfo();
  }, [clinics]);

  let fetchInfo = () => {
    fetch("/Survey.unsubscribe", {method: 'GET'})
      .then( (response) => response.ok ? response.json() : Promise.reject(response) )
      .then( (json) => {
        json.status == "success" ? setUnsubscribedAll(json.email_unsubscribed) : Promise.reject(json.error);
        let list = [];
        if (json?.email_unsubscribed != null) {
          list = json.email_unsubscribed == 1 ? clinics.map(c => c["@path"]) : [];
        } else {
          json.unsubscribed_list && list.push(...json.unsubscribed_list);
        }

        if (json?.email_unsubscribed != 1 && json.currentClinic && !list.includes(json.currentClinic)) {
          !initiated && list.push(json.currentClinic);
          setInitiated(true);
        }

        setUnsubscribedList(list);
      })
      .catch((response) => {
        let errMsg = "Cannot unsubscribe: ";
        setError(errMsg + (response.status ? response.statusText : response));
      });
  }

  let unsubscribe = (data, callback) => {
    setShowAllAlert(false);
    setShowListAlert(false);
    setError();
    fetch("/Survey.unsubscribe", { method: 'POST', body: data })
      .then( (response) => response.ok ? response.json() : Promise.reject(response) )
      .then( (json) => {
        fetchInfo();
        callback();
      })
      .catch((response) => {
        let errMsg = "Unsubscribing failed: ";
        setError(errMsg + (response.status ? `with error code ${response.status}: ${response.statusText}` : response));
      });
  }

  let unsubscribeAll = () => {
    let request_data = new FormData();
    request_data.append("email_unsubscribed", unsubscribedAll ? 0 : 1);
    unsubscribe(request_data, ()=> setShowAllAlert(true));
  }

  let unsubscribeList = () => {
    let request_data = new FormData();
    if (setUnsubscribedList.length == clinics.length) {
      request_data.append("email_unsubscribed", 1);
    } else if (setUnsubscribedList.length == 0) {
      request_data.append("email_unsubscribed", 0);
    } else {
      unsubscribedList.forEach((item) => request_data.append("unsubscribed_list", item));
    }
    unsubscribe(request_data, ()=> setShowListAlert(true));
  }

  let handleToggle = (value) => {
    setShowAllAlert(false);
    setShowListAlert(false);
    setError();

    const currentIndex = unsubscribedList.indexOf(value);
    const newList = [...unsubscribedList];

    if (currentIndex === -1) {
      newList.push(value);
    } else {
      newList.splice(currentIndex, 1);
    }

    setUnsubscribedList(newList);
  };

  if (!("hasSessionSubject" in document.getElementById("patient-portal-unsubscribe-container").dataset)) {
    return (
      <ErrorPage
        title="Invalid access"
        message="This page can only be accessed by opening an invitation to fill in a survey"
        buttonLink="/content.html/Questionnaires/User"
        buttonLabel="Go to the dashboard"
        textAlign="left"
      />
    );
  }

  let appName = document.querySelector('meta[name="title"]')?.content;

  return (
      <Paper className={classes.paper} elevation={0}>
        <Grid
          container
          direction="column"
          spacing={2}
        >
          <Logo component={Grid} size={12} />
          <Grid size={12}>
            { error && <Alert severity="error">
              <AlertTitle>An error occurred</AlertTitle>
               {error}
              </Alert>
            }
            { showAllAlert && <Alert icon={false} severity="success">{ `You have ${unsubscribedAll ? "unsubscribed from" : "resubscribed to"} ${appName}.` }</Alert> }
            { showListAlert && <Alert icon={false} severity="success">
              { `You have unsubscribed from ${unsubscribedList.map(s => s.replace("/Survey/ClinicMapping/", "")).join(", ")}.` }
              </Alert> }
          </Grid>
          <Grid size={12}>
            <Typography variant="h4">Your Experience survey options</Typography>
          </Grid>
          <Grid size={12}>
            <Typography variant="h6">Unsubscribe from:</Typography>
          </Grid>
          <Grid size={12}>
            <List dense>
            { (clinics || []).filter(c => c).map(c => (
              <ListItem key={c.clinicName} disablePadding>
                 <ListItemButton onClick={() => handleToggle(c["@path"])} dense>
                  <ListItemIcon className={classes.denseIcon}>
                    <Checkbox
                      size="small"
                      classes={{ sizeSmall: classes.sizeSmall }}
                      edge="start"
                      disableRipple
                      checked={unsubscribedAll || unsubscribedList.includes(c["@path"])}
                    />
                  </ListItemIcon>
                  <ListItemText primary={c.displayName} />
                </ListItemButton>
              </ListItem>
            ))}
            </List>
          </Grid>
          <Grid size={12}>
            <Button
              variant="contained"
              className={classes.submit}
              onClick={() => unsubscribeList()}
              >
              Unsubscribe from Selection
            </Button>
            <Button
              variant="outlined"
              className={classes.submit}
              onClick={() => unsubscribeAll()}
              >
              {unsubscribedAll ? "Resubscribe to all" : "Unsubscribe from all"}
            </Button>
          </Grid>
        </Grid>
      </Paper>
  );
}

const root = createRoot(document.querySelector('#patient-portal-unsubscribe-container'));
root.render(
  <StyledEngineProvider injectFirst>
    <ThemeProvider theme={appTheme}>
      <Unsubscribe />
    </ThemeProvider>
  </StyledEngineProvider>
);

export default Unsubscribe;
