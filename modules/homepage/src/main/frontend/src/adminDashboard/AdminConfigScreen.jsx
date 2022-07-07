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

import { useHistory } from 'react-router-dom';

import { Alert, Button, CardActions, CircularProgress } from "@mui/material";
import { makeStyles } from '@mui/styles';

import AdminScreen from "./AdminScreen.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

const useStyles = makeStyles(theme => ({
  root: {
    "& .MuiAlert-root": {
      margin: theme.spacing(0, 1, 2),
    },
    "& .MuiListItem-root": {
      padding: theme.spacing(1),
    },
    "& .MuiCardActions-root": {
      padding: theme.spacing(2, 1, 0),
    },
  },
}));

function AdminConfigScreen(props) {
  const { title, configPath, onConfigFetched, hasChanges, configError, buildConfigData, onConfigSaved, children } = props;
  const [ fetched, setFetched ] = useState();
  const [ error, setError ] = useState();

  const globalContext = useContext(GlobalLoginContext);
  const history = useHistory();
  const classes = useStyles();

  useEffect(() => getConfig(), []);

  // Loading the existing configuration
  const getConfig = () => {
    fetchWithReLogin(globalContext, `${configPath}.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(json => {
         setFetched(true);
         onConfigFetched?.(json);
	  })
      .catch(err => {
         setFetched(false);
         setError("The configuration could not be loaded.")
      });
  }

  // Submitting the form to save the new configuration
  const handleSubmit = (event) => {

    // This stops the normal browser form submission
    event && event.preventDefault();

    // Abort if there's a data sanity error
    if (configError) return;

    // Reset any recorded fetch error
    setError("");

    // Build formData object.
    let formData = new URLSearchParams();
    buildConfigData?.(formData);

    fetchWithReLogin(globalContext, configPath,
      {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: formData
      })
      .then((response) => {

        // Important note about native fetch, it does not reject failed
        // HTTP codes, it'll only fail when network error
        // Therefore, you must handle the error code yourself.
        if (!response.ok) {
          throw Error(response.statusText);
        }

        onConfigSaved?.();
      })
      .catch(err => setError("The configuration could not be saved."));
  }

  return (
    <AdminScreen title={title} className={classes.root}>
      { (configError || error) && <Alert severity="error">{configError || error}</Alert> }
      { typeof(fetched) == 'undefined' ? <CircularProgress/> :
        !fetched ? "" :
        <form onSubmit={handleSubmit}>
          { children }
          <CardActions>
            <Button
              type="submit"
              variant="contained"
              color="primary"
              size="small"
              disabled={configError || !hasChanges}
            >
              Save
            </Button>
            <Button
              variant="outlined"
              color="primary"
              size="small"
              onClick={() => history.push("/content.html/admin/") }
            >
              Cancel
            </Button>
          </CardActions>
        </form>
      }
    </AdminScreen>
  );
}

export default AdminConfigScreen;
