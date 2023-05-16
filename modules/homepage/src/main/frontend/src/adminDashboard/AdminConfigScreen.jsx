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

import PropTypes from "prop-types";

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

/**
 * Component that displays an administration screen allowing for a configuration node to be
 * updated by an admin user.
 *
 * Given the path of the configuration node (specified by the `configPath` prop), it generates
 * a form with two buttons, SAVE and RESET. The actual contents of the form are rendered
 * by the parent component as the `children`. `AdminConfigScreen` loads the configuration
 * from the backend and passes it on to the parent component via the `onConfigFetched` handler.
 * It gets notified if any changes have been made to the form via the `hasChanges` prop.
 * In case of data sanity issues as the user fills out a form (for example, a start date which
 * is later than an end date), it receives an error to display via the `configError` prop.
 * Data sanity errors will prevent the form from being saved by disabling the button.
 * When the save button is pressed, the parent component is notified via the `buildConfigData`
 * handler to build the form data, which is then sent to the server by `AdminConfigScreen`. Once
 * successfully saved, the parent component is notified via the `onConfigSaved` handler.
 * Resetting is done by reverting to the configuration that was first loaded when rendering
 * this component, both on the frontend (the parent component is notified to populate the form
 * with the original values) and on the backend (whatever has been saved in the current session
 * is overwritted with the original values). The user is informed of the effects of resetting
 * and is asked to confirm before actually performing it.
 *
 * @example
 * <AdminConfigScreen
 *  title="My feature's configuration"
 *  configPath="/path/to/jcr/node"
 *  onConfigFetched={readConfig}
 *  hasChanges={hasChanges}
 *  configError={error}
 *  buildConfigData={buildConfigData}
 *  onConfigSaved={resetHasChanges}
 *  >
 *    { renderConfigFormEntries() }
 * </AdminConfigScreen>
 *
 * @param {string} title  - the title of the administration screen, required
 * @param {string} configPath  - the path of the configuration node; will be used for loading and saving the data, required
 * @param {object} configTemplate - what an empty config would look like; should list all the possible fields with empty values, required
 * @param {function} onConfigFetched  - handler to pass the loaded configuration json to the parent component
 * @param {boolean} hasChanges  - how the parent component notifies that the user changed the form values
 * @param {string} configError  - how the parent component notifies that there's a data sanity issue
 * @param {function} buildConfigData  - handler to pass the formData to be populated by the parent component before saving
 * @param {function} onConfigSaved  - handler to notify the parent component that the data has been saved (no params passed)
 * @param {node or Array.<node>} children - any content that will be displayed in the form, typically
 *   labeled form controls for each property of the configuration node
 */

function AdminConfigScreen(props) {
  const { title, configPath, configTemplate, onConfigFetched, hasChanges, configError, buildConfigData, onConfigSaved, children } = props;
  const [ config, setConfig ] = useState();
  const [ configIsInitial, setConfigIsInitial ] = useState(true);
  const [ error, setError ] = useState();
  const [ resetConfirmationPending, setResetConfirmationPending ] = useState(false);

  const globalContext = useContext(GlobalLoginContext);
  const history = useHistory();
  const classes = useStyles();

  useEffect(() => {getConfig()}, []);
  useEffect(() => {hasChanges && setConfigIsInitial(false)}, [hasChanges]);

  // Loading the existing configuration
  const getConfig = () => {
    fetchWithReLogin(globalContext, `${configPath}.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(json => {
         let conf = Object.assign({}, configTemplate, json);
         setConfig(conf);
         onConfigFetched?.(conf);
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
    if (event) {
      // If the submit is user-initiated, grab values form the form
      buildConfigData?.(formData);
      // Use the config template to Fill in any missing values
      for (let key of Object.keys(configTemplate)) {
        formData.get(key) == null && Array.of(configTemplate[key]).flat().forEach(v => formData.append(key, v));
      }
    } else {
      // Otherwise just save the existing config
      // Use the template keys to avoid attempts to save reserved properties like "jcr:...", "sling:...", "@..."
      for (let key of Object.keys(configTemplate)) {
        Array.of(config[key]).flat().forEach(v => formData.append(key, v));
      }
    }

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
              disabled={!!configError || !hasChanges}
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
              Confirm configuration reset
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

AdminConfigScreen.propTypes = {
  title: PropTypes.string.isRequired,
  configPath: PropTypes.string.isRequired,
  configTemplate: PropTypes.object.isRequired,
  onConfigFetched: PropTypes.func,
  hasChanges: PropTypes.bool,
  configError: PropTypes.string,
  buildConfigData: PropTypes.func,
  onConfigSaved: PropTypes.func,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
}

export default AdminConfigScreen;
