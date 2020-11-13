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
import { withRouter, useHistory } from "react-router-dom";

import { Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton } from "@material-ui/core";
import { Tooltip, Typography, withStyles } from "@material-ui/core";
import { Delete, Close } from "@material-ui/icons";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

/**
 * A component that renders an icon to open a dialog to delete an entry.
 */
function DeleteButton(props) {
  const { classes, entryPath, entryName, warning, onComplete, entryType, entryLabel, size, shouldGoBack, buttonClass } = props;

  const [ open, setOpen ] = useState(false);
  const [ errorOpen, setErrorOpen ] = useState(false);
  const [ errorMessage, setErrorMessage ] = useState("");
  const [ dialogMessage, setDialogMessage ] = useState(null);
  const [ dialogAction, setDialogAction ] = useState("");
  const [ deleteRecursive, setDeleteRecursive ] = useState(false);
  const [ entryNotFound, setEntryNotFound ] = useState(false);
  const [ deletionStatus, setDeletionStatus ] = useState(undefined);

  const defaultDialogAction = `Are you sure you want to delete ${entryName}?`;
  const defaultErrorMessage = entryName + " could not be removed.";
  const history = useHistory();

  let openDialog = () => {
    closeError();
    if (!open) {setOpen(true);}
  }

  let closeDialog = () => {
    if (open) {setOpen(false);}
  }

  let openError = () => {
    closeDialog();
    if (!errorOpen) {setErrorOpen(true);}
  }

  let closeError = () => {
    if (errorOpen) {
      setErrorOpen(false);
    }
    if (entryNotFound) {
      // Can't delete. Assume already deleted and exit if required
      if (onComplete) {onComplete();}
      if (shouldGoBack) {goBack();}
    }
  }

  let handleError = (status, response) => {
    // If the user is not logged in, offer to log in
    const sessionInfo = window.Sling.getSessionInfo();
    if (sessionInfo === null || sessionInfo.userID === 'anonymous') {
      // On first attempt to save while logged out, set status to false to make button text inform user
      setDeletionStatus(false);
      setDialogAction(defaultErrorMessage);
    } else if (status === 401) {
      setErrorMessage(`${defaultErrorMessage} You are not permitted to perform that action.`);
      openError();
    } else if (status === 404) {
      setErrorMessage(`${entryName} could not be found. This ${entryType ? entryType : "item"} may have already been deleted.`);
      setEntryNotFound(true);
      openError();
    } else {
      try {
        response.json().then((json) => handleJsonError(response.status, json));
      } catch (error) {
        setErrorMessage(defaultErrorMessage);
        openError();
      }
    }
  }

  let handleJsonError = (status, json) => {
    if (status === 409) {
      if (deleteRecursive) {
        // Already recursive delete, error out
        setErrorMessage(`${defaultErrorMessage} ${json["status.message"]}`);
        openError();
      } else {
        setDialogMessage(`${json["status.message"].replace("This item", entryName)}`);
        setDialogAction(`Would you like to delete ${entryName} and all items that reference it?`);
        setDeleteRecursive(true);
        openDialog();
      }
    } else {
      setErrorMessage(`${defaultErrorMessage} The server returned response code ${status}`);
      openError();
    }
  }

  let handleDeleteButtonClicked = () => {
    if (deletionStatus === false) {
      handleLogin();
    } else {
      handleDelete();
    }
  }

  let handleLogin = () => {
    const width = 600;
    const height = 800;
    const top = window.top.outerHeight / 2 + window.top.screenY - (height / 2);
    const left = window.top.outerWidth / 2 + window.top.screenX - (width / 2);
    // After a successful log in, the login dialog code will "open" the specified resource, which results in executing the specified javascript code
    window.open("/login.html?resource=javascript%3Awindow.close()", "loginPopup", `width=${width}, height=${height}, top=${top}, left=${left}`);
    // Reset the dialog message and log in button
    setDeletionStatus(undefined);
    setDialogAction(defaultDialogAction);
  }

  let handleDelete = () => {
    let url = new URL(entryPath, window.location.origin);
    if (deleteRecursive) {
      url.searchParams.set("recursive", true);
    }
    fetch( url, {
      method: 'DELETE',
      headers: {
        Accept: "application/json"
      }
    }).then((response) => {
      if (response.ok)  {
        setDeletionStatus(true);
        closeDialog();
        if (onComplete) {onComplete();}
        if (shouldGoBack) {goBack();}
      } else {
        handleError(response.status, response);
      }
    });
  }

  let handleIconClicked = () => {
    setDialogMessage(null);
    setDialogAction(defaultDialogAction);
    setDeleteRecursive(false);
    openDialog();
  }

  let goBack = () => {
    if (history.length > 2) {
      history.goBack();
    } else {
      history.replace("/");
    }
  }

  return (
    <React.Fragment>
      <Dialog open={errorOpen} onClose={closeError}>
        <DialogTitle disableTypography>
          <Typography variant="h6" color="error" className={classes.dialogTitle}>Error</Typography>
          <IconButton onClick={closeError} className={classes.closeButton}>
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent>
            <Typography variant="body1">{errorMessage}</Typography>
        </DialogContent>
      </Dialog>
      <Dialog open={open} onClose={closeDialog}>
        <DialogTitle disableTypography>
        <Typography variant="h6">Delete {entry Label ? entryLabel.concat(' ') : ''}{entryName}{deleteRecursive ? " and dependent items": null }</Typography>
        </DialogTitle>
        <DialogContent>
            <Typography variant="body1">{dialogMessage}</Typography>
            <Typography variant="body1">{dialogAction}</Typography>
        </DialogContent>
        <DialogActions className={classes.dialogActions}>
            <Button variant="contained" color="secondary" size="small" onClick={() => handleDeleteButtonClicked()}>
              { deletionStatus === false ? "Log in and Try Again?" : (deleteRecursive ? "Delete All" : "Delete")}
            </Button>
            <Button variant="contained" size="small" onClick={closeDialog}>Close</Button>
        </DialogActions>
      </Dialog>
      <Tooltip title={entryType ? "Delete " + entryType : "Delete"}>
        <IconButton component="span" onClick={handleIconClicked} className={buttonClass}>
          <Delete fontSize={size ? size : "default"} className={warning ? classes.warningIcon : null}/>
        </IconButton>
      </Tooltip>
    </React.Fragment>
  );
}

export default withStyles(QuestionnaireStyle)(withRouter(DeleteButton));
