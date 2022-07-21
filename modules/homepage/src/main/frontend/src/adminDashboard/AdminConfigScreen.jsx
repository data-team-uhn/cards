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

import { Alert, Button, CardActions, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from "@mui/material";
import { makeStyles } from '@mui/styles';

import AdminScreen from "./AdminScreen.jsx";
import FormattedText from "../components/FormattedText.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

const useStyles = makeStyles(theme => ({
  root: {
    "& .MuiCardContent-root > .MuiAlert-root": {
      margin: theme.spacing(0, 1, 1),
    },
    "& .MuiListItem-root": {
      padding: theme.spacing(1),
    },
    "& .MuiCardActions-root": {
      padding: theme.spacing(2, 1, 0),
    },
  },
  confirmationDialog: {
    "& .MuiDialogContent-root > *": {
      margin: theme.spacing(2, 0),
    },
    "& .MuiDialogActions-root .MuiButton-root:last-child": {
      marginLeft: "auto",
    },
  },
}));

function AdminConfigScreen(props) {
  const { title, configPath, onConfigFetched, hasChanges, configError, buildConfigData, onConfigSaved, children } = props;
  const [ config, setConfig ] = useState();
  const [ configIsInitial, setConfigIsInitial ] = useState(true);
  const [ error, setError ] = useState();
  const [ resetConfirmationPending, setResetConfirmationPending ] = useState(false);

  const globalContext = useContext(GlobalLoginContext);
  const history = useHistory();
  const classes = useStyles();

  useEffect(() => getConfig(), []);
  useEffect(() => hasChanges && setConfigIsInitial(false), [hasChanges]);

  // Loading the existing configuration
  const getConfig = () => {
    fetchWithReLogin(globalContext, `${configPath}.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(json => {
         setConfig(json);
         onConfigFetched?.(json);
	  })
      .catch(err => {
         setConfig(null);
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

  const handleReset = (event) => {
    // This stops the click event from reaching the parent form
    event && event.preventDefault();
    // Record the fact that we're back to the initial config
    setConfigIsInitial(true);
    // Load the initial config in the form (dropping any changes)
    onConfigFetched?.(config);
    // Save the initial config, overwriting any previously saved changes
    handleSubmit();
    // Close the confirmation dialog
    setResetConfirmationPending(false);
  }

  return (
    <AdminScreen title={title} className={classes.root}>
      { (configError || error) && <Alert severity="error">{configError || error}</Alert> }
      { typeof(config) == 'undefined' ? <CircularProgress/> :
        !config ? "" :
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
              color="error"
              size="small"
              disabled={configIsInitial}
              onClick={() => setResetConfirmationPending(true)}
            >
              Reset to initial settings
            </Button>
          </CardActions>

          { /* Confirmation dialog for resetting the changes */ }
          <Dialog className={classes.confirmationDialog} open={resetConfirmationPending}>
            <DialogTitle>
              <Typography variant="h6" color="error" className={classes.dialogTitle}>Confirm configuration reset</Typography>
            </DialogTitle>
            <DialogContent>
              <FormattedText>
                This will revert **all** the changes made since opening this pagem **including the ones that you may have already saved**.
              </FormattedText>
              <FormattedText>
                If you wish to keep the saved changes and discard the unsaved ones, you can simply navigate away from this page, for example by clicking on the link to Administration at the top.
              </FormattedText>
              <FormattedText>
                **Are you sure you wish to proceed with resetting the configuration?**
              </FormattedText>
            </DialogContent>
            <DialogActions>
              <Button size="small" variant="contained" onClick={handleReset}>Yes, Reset</Button>
              <Button size="small" variant="outlined" onClick={() => setResetConfirmationPending(false)}>No, Cancel</Button>
              <Button size="small" variant="text" onClick={() => history.push("/content.html/admin/")}>No, go to Administration</Button>
            </DialogActions>
          </Dialog>
        </form>
      }
    </AdminScreen>
  );
}

export default AdminConfigScreen;
