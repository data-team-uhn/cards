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

import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Tooltip,
  TextField,
  Typography
} from "@mui/material";
import Alert from '@mui/material/Alert';
import withStyles from '@mui/styles/withStyles';
import LockIcon from "@mui/icons-material/Lock";
import LockOpenIcon from "@mui/icons-material/LockOpen";
import { DateTime } from "luxon";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import ErrorDialog from "../components/ErrorDialog.jsx";

function SubjectLockAction(props) {
  const { classes, subject, reloadSubject, size, variant, className } = props;

  const METHOD_LOCK = "LOCK";
  const METHOD_UNLOCK = "UNLOCK";
  const ACTION_CONTINUE = "CONTINUE";
  const ACTION_LOCK = "SIGN OFF";
  const ACTION_UNLOCK = "UNLOCK";

  const [ open, setOpen ] = useState(false);
  const [ dialogTitle, setDialogTitle ] = useState(null);
  const [ dialogContent, setDialogContent ] = useState(null);
  const [ nextAction, setNextAction ] = useState(ACTION_CONTINUE);
  const [ errorOpen, setErrorOpen ] = useState(false);
  const [ errorMessage, setErrorMessage ] = useState("");
  const [ requestInProgress, setRequestInProgress ] = useState(false);
  const [ isLocked, setLocked ] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let lockUnlockText = isLocked ? "Unlock" : "Lock";
  let entryType = subject?.type?.label || "Subject";
  let entryPath = subject?.["@path"];

  useEffect(() => {
    setLocked(subject?.statusFlags && subject.statusFlags.includes("LOCKED"))
  }, [subject])

  let openDialog = () => {
    setDialogContent(null);
    if (isLocked) {
      handleOpenDialogLocked();
    } else {
      handleOpenDialogUnlocked();
      fetchIncompleteForms();
    }
    // TODO: move to a global context?
    // See: AdminNavbarLinks
    setOpen(true);
  }

  let closeDialog = () => {
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

  let handleOpenDialogUnlocked = () => {
    setDialogTitle(`Signoff and lock ${entryType} ${subject?.identifier}`)
    setNextAction(ACTION_CONTINUE);
    setDialogContent(null);
    fetchIncompleteForms()
  }

  let getLockWarning = () => {
    return <Alert severity="warning">Once you sign off on this {entryType}, it will be locked along with
            all the associated data forms and no further edits will be possible. Proceed?</Alert>
  }

  let fetchUsername = () => {
    setRequestInProgress(true);
    fetchWithReLogin(globalLoginDisplay, "/system/sling/info.sessionInfo.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setNextAction(ACTION_LOCK);
        setDialogContent(
          <>
            <TextField
              disabled
              id="user"
              label="Signing User"
              defaultValue={json["userID"]}
              className={classes.lockDialogInput}
              />
            <TextField
              disabled
              id="date"
              label="Date"
              defaultValue={new Date().toDateString()}
              className={classes.lockDialogInput}
              />
            {getLockWarning()}
          </>
        )
        setRequestInProgress(false);
      })
      .catch(response => handleError(response.status, response));
  }

  let handleOpenDialogLocked = () => {
    setDialogTitle(`Unlock ${entryType} ${subject?.identifier}`)
    setNextAction(ACTION_UNLOCK)
    setDialogContent(
      <Alert severity="warning">
        If you unlock this {subject?.type?.label} all the associated data forms
        will be unlocked as well unless they were seperately locked. Proceed?
      </Alert>
    )
  }

  let handleContinue = () => {
    fetchUsername();
  }

  let fetchIncompleteForms = () => {
    setRequestInProgress(true);

    let subjects = [subject["jcr:uuid"]]
    getChildSubjects(subject, subjects);
    fetchWithReLogin(globalLoginDisplay, `/query?query=SELECT * FROM [cards:Form] as f where f.'subject' in ('${subjects.join("','")}') and f.'statusFlags'='INCOMPLETE'`)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((response) => {
      if (response.rows?.length > 0) {
        handleIncompleteForms(response.rows);
      } else {
        handleContinue();
      }
      setRequestInProgress(false);
    })
    .catch(response => handleError(response.status, response));
  }

  let handleIncompleteForms = (rows) => {
    setNextAction(ACTION_CONTINUE);
    setDialogContent(
      <>
        <Typography variant="body1">{rows.length} form{rows.length > 1 ? "s are" : " is"} incomplete:</Typography>
        <List>
          {rows.map((row, index) => {
            let date = row["jcr:created"];
            let dateObj = DateTime.fromISO(date);
            if (dateObj.isValid) {
              date = dateObj.toFormat("yyyy-MM-dd");
            }
            return <ListItem key={index}>
                <ListItemText primary={row.questionnaire.title} secondary={"Created on " + date}></ListItemText>
              </ListItem>
          })}
        </List>
        {getLockWarning()}
      </>
    )
  }

  let getChildSubjects = (subject, subjects) => {
    Object.values(subject).forEach((child) => {
      if (child["jcr:primaryType"] == "cards:Subject" && child["@path"].startsWith(subject["@path"])) {
        subjects.push(child["jcr:uuid"]);
        getChildSubjects(child, subjects);
      }
    })
  }

  let handleError = (status, response) => {
    setRequestInProgress(false);
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

  let handleActionClicked = () => {
    switch (nextAction) {
      case ACTION_CONTINUE:
        handleContinue();
        break;
      case ACTION_LOCK:
        handleSave(METHOD_LOCK);
        break;
      case ACTION_UNLOCK:
        handleSave(METHOD_UNLOCK);
        break;
      default:
        // Should not happen
        console.warn("Subject Lock dialog encountered unknown next action " + nextAction);
        closeDialog();
        break;
    }
  }

  let handleSave = (method) => {
    // If no path is provided, can't lock or unlock anything
    if (!entryPath) {
      openError("Could not " + lockUnlockText.toLowerCase() + " as the path to this item is unknown");
      return;
    }
    let url = new URL(entryPath, window.location.origin);
    setRequestInProgress(true);
    fetchWithReLogin(globalLoginDisplay, url, {
      method: method,
    }).then((response) => {
      setRequestInProgress(false);
      if (response.ok)  {
        closeDialog();
        reloadSubject && reloadSubject();
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
      <DialogTitle>{dialogTitle}</DialogTitle>
      <DialogContent>
        {dialogContent}
      </DialogContent>
      <DialogActions className={classes.dialogActions}>
          <Button variant="outlined" size="small" onClick={closeDialog}>Cancel</Button>
          <Button
            variant="contained"
            size="small"
            onClick={handleActionClicked}
            disabled={requestInProgress}
          >
            {nextAction}
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

SubjectLockAction.propTypes = {
  subject: PropTypes.object,
  className: PropTypes.string,
  reloadSubject: PropTypes.func,
  size: PropTypes.oneOf(["small", "medium", "large"]),
  variant: PropTypes.oneOf(["icon", "text", "extended"]), // "extended" means both icon and text
}

SubjectLockAction.defaultProps = {
  variant: "icon",
  size: "large",
}

export default withStyles(QuestionnaireStyle)(withRouter(SubjectLockAction));
