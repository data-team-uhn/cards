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

import React, { useState, useEffect, useContext } from "react";

import {
  Alert,
  Button,
  Grid,
  TextField,
  Typography,
} from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import AdminScreen from "../adminDashboard/AdminScreen.jsx";

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

const APIKEY_SERVLET_URL = "/.googleApiKey";

const useStyles = makeStyles(theme => ({
  noKeyInfo: {
    padding: theme.spacing(1, 0)
  },
}));

export default function googleApiKeyAdminPage() {
  const [ googleSystemApiKey, setGoogleSystemApiKey ] = useState("");
  const [ googleApiKey, setGoogleApiKey ] = useState("");
  const [ hasChanges, setHasChanges ] = useState(false);
  const [ error, setError ] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);
  const classes = useStyles();

  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, APIKEY_SERVLET_URL)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((keyJson) => {
        if (!keyJson.apikey) {
          console.log("No API key in APIKEY servlet response");
        }
        setGoogleSystemApiKey(keyJson.apikey);
        setGoogleApiKey(keyJson.apikey);
    })
    .catch((error) => {
        setError("Error fetching GoogleApiKey node: " + error);
    });
  }, [])

  // function to create / edit node
  function addNewKey() {
    const URL = `/libs/cards/conf/GoogleApiKey`;
    var request_data = new FormData();
    request_data.append('key', googleApiKey);
    fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
      .then((response) => response.ok ? response : Promise.reject(response))
      .then((data) => {
          setGoogleSystemApiKey(googleApiKey);
          setHasChanges(false);
      })
      .catch((error) => {
          setError("Error creating GoogleApiKey node: " + error);
      }
    )
  }

  return(
    <AdminScreen title="Google API key configuration">
      <Grid container direction="column" spacing={6} justifyContent="space-around">
        { !googleSystemApiKey &&
         <Grid item className={classes.noKeyInfo}>
           <Typography>Your system does not have a Google API Key configured.</Typography>
           <Typography>Without an API key, you cannot access Google services such as address autocomplete.</Typography>
         </Grid> }
        { error && <Alert severity="error">{error}</Alert> }
        <Grid item>
          <Grid container
            direction="row"
            alignItems="center"
            justifyContent="space-between"
            alignContent="space-between"
            spacing={2}
          >
            <Grid item xs={10}>
              <TextField
                  variant="outlined"
                  onChange={(evt) => {setGoogleApiKey(evt.target.value); setHasChanges(true); setError("");}}
                  value={googleApiKey}
                  label="Google API key"
                  fullWidth={true}
                />
            </Grid>
            <Grid item xs={2}>
              <Button
                color="primary"
                variant="contained"
                disabled={!hasChanges}
                onClick={() => {addNewKey()}}
              >
                Submit
              </Button>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
    </AdminScreen>
  );
}
