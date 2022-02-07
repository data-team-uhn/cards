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
import React, { useState, useContext } from "react";
import { withRouter, useHistory } from "react-router-dom";
import PropTypes from "prop-types";

import { Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton } from "@material-ui/core";
import { Tooltip, Typography, withStyles } from "@material-ui/core";
import { Delete, Close } from "@material-ui/icons";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

/**
 * A component that renders an icon to open a dialog to delete an entry.
 */
function DeleteButton(props) {
  const { classes, entryPath, entryName, onComplete, entryType, entryLabel, size, shouldGoBack, buttonClass, variant, label } = props;

  const [ open, setOpen ] = useState(false);
  const [ errorOpen, setErrorOpen ] = useState(false);
  const [ errorMessage, setErrorMessage ] = useState("");
  const [ dialogMessage, setDialogMessage ] = useState(null);
  const [ dialogAction, setDialogAction ] = useState("");
  const [ deleteRecursive, setDeleteRecursive ] = useState(false);
  const [ entryNotFound, setEntryNotFound ] = useState(false);

  const buttonText = label || ("Delete " + (entryType?.toLowerCase() || '')).trim();
  const defaultDialogAction = `Are you sure you want to delete ${entryType} ${entryName}?`;
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
    if (status === 404) {
      setErrorMessage(`${entryName} could not be found. This ${entryType ? entryType : "item"} may have already been deleted.`);
      setEntryNotFound(true);
      openError();
    } else if (status === 401 || response.url.startsWith(window.location.origin + "/login")) {
      setErrorMessage(`The currently signed in user does not have permission to delete ${entryName}.`);
      setEntryNotFound(false);
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
        let label = entryLabel ? entryLabel.concat(' ') : entryType ? entryType.concat(' ') : '';
        setDialogMessage(`${json["status.message"].replace("This item", label + entryName)}`);
        setDialogAction(`Would you like to delete ${entryName} and all items that reference it?`);
        setDeleteRecursive(true);
        openDialog();
      }
    } else {
      setErrorMessage(`${defaultErrorMessage} The server returned response code ${status}`);
      openError();
    }
  }

  let handleDelete = () => {
    // If no path is provided, display the button but don't do any delete calls
    // and consider deletion successful since there's nothing to do
    if (!entryPath) {
      closeDialog();
      if (onComplete) {onComplete();}
      if (shouldGoBack) {goBack();}
      return;
    }
    let url = new URL(entryPath, window.location.origin);
    if (deleteRecursive) {
      url.searchParams.set("recursive", true);
    }

    // We should not use fetchWithReLogin here, since the deletion can cause a 401 error
    // if the currently-logged-in user is unauthorized. Instead, fetch, and handle 401s separately
    fetch(url, {
      method: 'DELETE',
      headers: {
        Accept: "application/json"
      }
    }).then((response) => {
      if (response.status == 401 || response.status == 500) {
        handleError(response.status, response);
      } else if (response.ok && response.url.startsWith(window.location.origin + "/login")) {
        handleError(response.status, response);
      } else if (response.ok)  {
        closeDialog();
        if (onComplete) {onComplete();}
        if (shouldGoBack) {goBack();}
      } else {
        handleError(response.status, response);
      }
    });
  }

  let handleClick = () => {
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
        <Typography variant="h6">Delete {entryLabel ? entryLabel.concat(' ') : ''}{entryName}{deleteRecursive ? " and dependent items": null }</Typography>
        </DialogTitle>
        <DialogContent>
            <Typography variant="body1">{dialogMessage}</Typography>
            <Typography variant="body1">{dialogAction}</Typography>
        </DialogContent>
        <DialogActions className={classes.dialogActions}>
            <Button variant="contained" color="secondary" size="small" onClick={() => handleDelete()}>
              { deleteRecursive ? "Delete All" : "Delete" }
            </Button>
            <Button variant="contained" size="small" onClick={closeDialog}>Close</Button>
        </DialogActions>
      </Dialog>
      {variant == "icon" ?
        <Tooltip title={buttonText}>
          <IconButton component="span" onClick={handleClick} className={buttonClass} size={size}>
            <Delete fontSize={size == "small" ? size : undefined}/>
          </IconButton>
        </Tooltip>
        :
        <Button
          onClick={handleClick}
          size={size ? size : "medium"}
          startIcon={variant == "extended" ? <Delete /> : undefined}
        >
          {buttonText}
        </Button>
      }
    </React.Fragment>
  );
}

DeleteButton.propTypes = {
  variant: PropTypes.oneOf(["icon", "text", "extended"]), // "extended" means both icon and text
  label: PropTypes.string,
  size: PropTypes.oneOf(["small", "medium"]),
}

DeleteButton.defaultProps = {
  variant: "icon",
  size: "medium",
}

export default withStyles(QuestionnaireStyle)(withRouter(DeleteButton));
