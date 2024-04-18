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
import withStyles from '@mui/styles/withStyles';
import LockIcon from "@mui/icons-material/Lock";
import LockOpenIcon from "@mui/icons-material/LockOpen";
import PrintPreview from "../questionnaire/PrintPreview.jsx";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import ErrorDialog from "../components/ErrorDialog.jsx";

function LockButton(props) {
  const { classes, entryPath, onOpen, onClose, size, variant, entryType, entryName, className, statusFlags } = props;

  const [ open, setOpen ] = useState(false);
  const [ dialogMessage, setDialogMessage ] = useState("");
  const [ dialogAction, setDialogAction ] = useState("");
  const [ errorOpen, setErrorOpen ] = useState(false);
  const [ errorMessage, setErrorMessage ] = useState("");
  const [ requestInProgress, setRequestInProgress ] = useState(false);
  const [ isLocked, setLocked ] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const POST_ACTION_NAME = "action";
  const POST_ACTION_LOCK = "LOCK";
  const POST_ACTION_UNLOCK = "UNLOCK";

  let lockUnlockText = isLocked ? "Unlock" : "Lock";
  let lockUnlockProgressText = isLocked ? "Unlocking..." : "Locking..."

  useEffect(() => {
    setLocked(statusFlags && statusFlags.includes("LOCKED"));
  }, [statusFlags]);

  useEffect(() => {
    if (isLocked) {
      setDialogMessage("Are you sure you would like to unlock this " + entryType + "?");
    } else {
      setDialogMessage("Would you like to lock this " + entryType + "?");
    }
  }, [isLocked, entryType]);


  let openDialog = () => {
    onOpen && onOpen();
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

    let request_data = new FormData();
    // Populate the request data with information about the tou_accepted answer
    request_data.append(POST_ACTION_NAME, isLocked ? POST_ACTION_UNLOCK : POST_ACTION_LOCK);

    fetchWithReLogin(globalLoginDisplay, url, {
      method: "POST",
      body: request_data
    }).then((response) => {
      setRequestInProgress(false);
      if (response.ok)  {
        closeDialog();
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
        {lockUnlockText} {entryType} {entryName}
      </DialogTitle>
      <DialogContent>
          <Typography variant="body1">{dialogMessage}</Typography>
          <Typography variant="body1">{dialogAction}</Typography>
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
  size: PropTypes.oneOf(["small", "medium", "large"]),
}

LockButton.defaultProps = {
  variant: "icon",
  size: "large",
}

export default withStyles(QuestionnaireStyle)(withRouter(LockButton));
