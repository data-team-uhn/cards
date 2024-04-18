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
import { withRouter } from "react-router-dom";
import PropTypes from "prop-types";

import { Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Tooltip, Typography } from "@mui/material";
import Alert from '@mui/material/Alert';
import withStyles from '@mui/styles/withStyles';
import LockIcon from "@mui/icons-material/Lock";
import LockOpenIcon from "@mui/icons-material/LockOpen";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import ErrorDialog from "../components/ErrorDialog.jsx";

function LockButton(props) {
  const { classes, entryPath, onOpen, onClose, onComplete, size, variant, entryType, entryName, className, statusFlags } = props;

  const [ open, setOpen ] = useState(false);
  const [ dialogContent, setDialogContent ] = useState(<></>);
  const [ username, setUsername ] = useState("");
  const [ errorOpen, setErrorOpen ] = useState(false);
  const [ errorMessage, setErrorMessage ] = useState("");
  const [ requestInProgress, setRequestInProgress ] = useState(false);
  const [ isLocked, setLocked ] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const METHOD_LOCK = "LOCK";
  const METHOD_UNLOCK = "UNLOCK";

  let lockUnlockText = isLocked ? "Unlock" : "Lock";
  let lockUnlockProgressText = isLocked ? "Unlocking..." : "Locking..."

  useEffect(() => {
    setLocked(statusFlags && statusFlags.includes("LOCKED"));
  }, [statusFlags]);

  useEffect(() => {
    if (isLocked) {
      setDialogContent(<>
        <Alert severity="warning">If you unlock this {entryType} all the associated data forms
          will be unlocked as well unless they were seperately locked. Proceed?</Alert>
      </>)
    } else {
      setDialogContent(<>
        <Typography variant="body1">Signed off by: {username}</Typography>
        <Typography variant="body1">Date: {new Date().toDateString()}</Typography>
        <Alert severity="warning">Once you sign off on this {entryType}, it will be locked along with
          all the associated data forms and no further edits will be possible. Proceed?</Alert>
        </>)
    }
  }, [isLocked, entryType, username]);

  let openDialog = () => {
    onOpen && onOpen();
    // TODO: move to a global context?
    // See: AdminNavbarLinks
    fetchUsername();
    setOpen(true);
  }

  let closeDialog = () => {
    onClose && onClose();
    setOpen(false);
  }

  let openError = (message) => {
    closeDialog();
    setErrorMessage(message);
    setErrorOpen(true);
  }

  let closeError = () => {
    setErrorOpen(false);
  }

  let fetchUsername = () => {
    fetchWithReLogin(globalLoginDisplay, "/system/sling/info.sessionInfo.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => setUsername(json["userID"]))
      .catch((error) => console.log(error));
  }

  let handleError = (status, response) => {
    if (status === 404) {
      // NOT FOUND
      openError(`Item could not be found. This item may have already been deleted.`);
    } else if (status === 403) {
      // FORBIDDEN
      openError(`You do not have permission to ${lockUnlockText.toLowerCase()} this item.`);
    } else {
      openError(`Could not ${lockUnlockText.toLowerCase()} the item. The server returned response code ${status}`);
    }
  }

  let handleConfirm = () => {
    // If no path is provided, can't lock or unlock anything
    if (!entryPath) {
      openError("Could not " + lockUnlockText.toLowerCase() + " as the path to this item is unknown");
      return;
    }
    let url = new URL(entryPath, window.location.origin);
    setRequestInProgress(true);

    fetchWithReLogin(globalLoginDisplay, url, {
      method: isLocked ? METHOD_UNLOCK : METHOD_LOCK,
    }).then((response) => {
      setRequestInProgress(false);
      if (response.ok)  {
        closeDialog();
        onComplete && onComplete();
      } else {
        handleError(response.status, response);
      }
    });
  }

  let buttonText = lockUnlockText + " " + entryType;

  return( <>
    <ErrorDialog open={errorOpen} onClose={closeError}>
      <Typography variant="body1">{errorMessage}</Typography>
    </ErrorDialog>
    <Dialog open={open} onClose={closeDialog}>
      <DialogTitle>
        {isLocked ? "Unlock" : "Signoff and lock"} {entryType} {entryName}
      </DialogTitle>
      <DialogContent>
        {dialogContent}
      </DialogContent>
      <DialogActions className={classes.dialogActions}>
          <Button variant="outlined" size="small" onClick={closeDialog}>Cancel</Button>
          <Button
            variant="contained"
            size="small"
            onClick={() => handleConfirm()}
            disabled={requestInProgress}
          >
            {requestInProgress ? lockUnlockProgressText : lockUnlockText}
          </Button>
      </DialogActions>
    </Dialog>
    { variant == "icon" ?
        <Tooltip title={buttonText}>
          <IconButton component="span" onClick={openDialog} className={className} size={size}>
            { isLocked
              ? <LockOpenIcon fontSize={size == "small" ? size : undefined}/>
              : <LockIcon fontSize={size == "small" ? size : undefined}/>
            }
          </IconButton>
        </Tooltip>
        :
        <Button
          onClick={openDialog}
          size={size}
          startIcon={variant == "extended" ? (isLocked ? <LockOpenIcon /> : <LockIcon />) : undefined}
        >
          {buttonText}
        </Button>
    }
  </>)
}

LockButton.propTypes = {
  variant: PropTypes.oneOf(["icon", "text", "extended"]), // "extended" means both icon and text
  entryType: PropTypes.string.isRequired,
  entryName: PropTypes.string.isRequired,
  entryPath: PropTypes.string.isRequired,
  buttonText: PropTypes.string,
  statusFlags: PropTypes.arrayOf(PropTypes.string),
  className: PropTypes.string,
  onOpen: PropTypes.func,
  onClose: PropTypes.func,
  onComplete: PropTypes.func,
  size: PropTypes.oneOf(["small", "medium", "large"]),
}

LockButton.defaultProps = {
  variant: "icon",
  size: "large",
}

export default withStyles(QuestionnaireStyle)(withRouter(LockButton));
