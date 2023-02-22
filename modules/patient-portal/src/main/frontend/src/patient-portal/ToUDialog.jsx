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

import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import {
  Button,
  DialogActions,
  DialogContent
} from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';

import FormattedText from "../components/FormattedText.jsx";
import ResponsiveDialog from "../components/ResponsiveDialog";

const useStyles = makeStyles(theme => ({
  touDialog : {
    "& .MuiDialogTitle-root > .MuiTypography-root" : {
      fontSize: "1.75rem",
    }
  },
  touText : {
    "& .wmde-markdown blockquote" : {
      borderLeft: "0 none",
      color: "inherit",
      fontStyle: "italic",
      "& a" : {
        wordBreak: "break-all",
      },
    }
  },
  actionErrorMessage: {
    marginLeft: theme.spacing(2),
    marginRight: "auto",
  },
  reviewButton : {
    marginRight: "auto",
  }
}));

// Component that renders the Dialog with Terms of Use
//
// Required props:
// open: Boolean specifying whether the dialog is open
// onClose: Callback specifying what happens when the dialog is closed
//
// Optional props:
// actionRequired: Boolean specifying whether the user has yet to accept the terms.
//   If true, Accept/Decline action buttons will be displayed at the bottom
//   If false, a Close action will be displayed at the bottom
// onCleared: Callback specifying what happens when the user accepts the terms
// onDecline: Callback specifying what happens when the user declines the terms
//
// Sample usage:
// <ToUDialog
//   open={open}
//   actionRequired={true}
//   onClose={onClose}
//   onCleared={onCleared}
//   onDecline={onDecline}
/// />
//

const TOU_ACCEPTED_VARNAME = 'tou_accepted';

function ToUDialog(props) {
  const { open, actionRequired, onCleared, onDecline, onClose, ...rest } = props;

  const [ showConfirmationTou, setShowConfirmationTou ] = useState(false);
  const [ touAcceptedVersion, setTouAcceptedVersion ] = useState();
  const [ tou, setTou ] = useState();
  const [ error, setError ] = useState();
  const [ actionError, setActionError ] = useState();

  const classes = useStyles();

  const fetchTouAccepted = () => {
    fetch("/Survey.termsOfUse")
      .then( response => response.ok ? response.json() : Promise.reject(response) )
      .then( json => json.status == "success" ? json[TOU_ACCEPTED_VARNAME] : Promise.reject(json.error) )
      .then( setTouAcceptedVersion )
      .catch((response) => {
        let errMsg = "Retrieving Terms of Use version failed";
        setError(errMsg + (response.status ? ` with error code ${response.status}: ${response.statusText}` : response));
      });
  }

  useEffect(() => {
    if (actionRequired && !touAcceptedVersion) {
      fetchTouAccepted();
    }
  }, [actionRequired, touAcceptedVersion])

  useEffect(() => {
    if (actionRequired && open && error) {
      setError(null);
      fetchTouAccepted();
    }
  }), [actionRequired, open]

  useEffect(() => {
    fetch("/Survey/TermsOfUse.json")
      .then( response => response.ok ? response.json() : Promise.reject(response) )
      .then( setTou )
      .catch( err => setError("Loading the Terms of Use failed, please try again later") );
  }, []);

  useEffect(() => {
    if (tou && (!tou.acceptanceRequired || touAcceptedVersion && tou.version == touAcceptedVersion) ) {
      onCleared && onCleared();
    }
  }, [tou?.acceptanceRequired, tou?.version, touAcceptedVersion]);

  if ((!tou || actionRequired && !touAcceptedVersion) && !error) {
    return null;
  }

  // When the patient user accepts the terms of use, save their preference and hide the ToU dialog
  const saveTouAccepted = (version) => {
    let request_data = new FormData();
    // Populate the request data with information about the tou_accepted answer
    request_data.append(TOU_ACCEPTED_VARNAME, version);

    // Update the Patient information form
    fetch("/Survey.termsOfUse", { method: 'POST', body: request_data })
      .then( (response) => response.ok ? response.json() : Promise.reject(response) )
      .then( json => json.status == "success" ? onCleared && onCleared() : Promise.reject(json.error))
      .catch((response) => {
        let errMsg = "Recording acceptance of Terms of Use failed";
        console.log(errMsg + (response.status ? ` with error code ${response.status}: ${response.statusText}` : response));
        setActionError("Error: your preference could not be recorded.");
      });
  }

  return (<>
    <ResponsiveDialog
      title={tou?.title}
      open={open && (touAcceptedVersion !== tou?.version || !actionRequired)}
      width="md"
      onClose={onClose}
      scroll={actionRequired? "body" : "paper"}
      className={classes.touDialog}
    >
      { actionRequired &&
        <DialogContent>
          { touAcceptedVersion != "none" ?
            <Alert severity="warning">
              <AlertTitle>The Terms of Use have been updated</AlertTitle>
              Please review and accept the new Terms of Use to continue.
            </Alert>
            :
            <Alert icon={false} severity="info">
              Please read the Terms of Use and click Accept at the bottom to continue.
            </Alert>
          }
        </DialogContent>
      }
      <DialogContent dividers={!actionRequired} className={classes.touText}>
      { error ?
        <Alert severity="error">
          <AlertTitle>An error occurred</AlertTitle>
          {error}
        </Alert>
        :
        <FormattedText>{tou?.text}</FormattedText>
      }
      </DialogContent>
      <DialogActions>
      { actionRequired && !error && !actionError ?
        <>
          <Button color="primary" onClick={() => saveTouAccepted(tou.version)} variant="contained">
            Accept
          </Button>
          <Button color="secondary" onClick={() => setShowConfirmationTou(true)} variant="contained">
            Decline
          </Button>
        </>
        :
        <>
          { actionError &&
            <FormattedText color="error" className={classes.actionErrorMessage}>{actionError}</FormattedText>
          }
          <Button color="primary" onClick={() => onClose(!!actionError)} variant="outlined">
            Close
          </Button>
        </>
      }
      </DialogActions>
    </ResponsiveDialog>
    { actionRequired &&
      <ResponsiveDialog open={showConfirmationTou} title="Action required">
        <DialogContent>
          You can only fill out your pre-appointment surveys online after accepting the DATA PRO Terms of Use.
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowConfirmationTou(false)} variant="outlined" className={classes.reviewButton}>
            Review Terms
          </Button>
          <Button color="primary" onClick={() => {setShowConfirmationTou(false); saveTouAccepted(tou?.version)}} variant="contained" >
            Accept
          </Button>
          <Button color="secondary" onClick={() => {setShowConfirmationTou(false); onDecline && onDecline()}} variant="contained" >
            Decline
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    }
  </>);
}

ToUDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  actionRequired: PropTypes.bool,
  onCleared: PropTypes.func,
  onDecline: PropTypes.func,
}

export default ToUDialog;
