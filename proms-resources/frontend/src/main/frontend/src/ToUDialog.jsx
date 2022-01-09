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
  DialogContent,
  makeStyles
} from "@material-ui/core";

import { Alert, AlertTitle } from "@material-ui/lab";

import FormattedText from "./components/FormattedText.jsx";
import ResponsiveDialog from "./components/ResponsiveDialog";

const useStyles = makeStyles(theme => ({
  touText : {
    "& .wmde-markdown blockquote" : {
      borderLeft: "0 none",
      color: "inherit",
      fontStyle: "italic",
    }
  },
  reviewButton : {
    marginRight: "auto",
  }
}));

// Component that renders the Dialog width Terms of Use
//
// Required props:
// open: Boolean specifying whether the dialog is open
// onClose: Callback specifying what happens when the dialog is closed
//
// Optional props:
// actionRequired: Boolean specifying whether the user has yet to accept the terms.
//   If true, Accept/Decline action buttons will be displayed at the bottom
//   If false, a Close action will be displayed at the bottom
// onAccept: Callback specifying what happens when the user accepts the terms
// onDecline: Callback specifying what happens when the user declines the terms
//
// Sample usage:
// <ToUDialog
//   open={open}
//   actionRequired={true}
//   onClose={onClose}
//   onAccept={onAccept}
//   onDecline={onDecline}
/// />
//

function ToUDialog(props) {
  const { open, actionRequired, onAccept, onDecline, onClose, ...rest } = props;
  const [ showConfirmationTou, setShowConfirmationTou ] = useState(false);
  const [ tou, setTou ] = useState();
  const [ error, setError ] = useState();

  const classes = useStyles();

  useEffect(() => {
    fetch("/Proms/TermsOfUse.json")
      .then( response => response.ok ? response.json() : Promise.reject(response) )
      .then( setTou )
      .catch( err => setError("Loading the Terms of Use failed, please try again later") );
  }, []);

  if (!tou && !error) {
    return null;
  }

  return (<>
    <ResponsiveDialog
      title={tou.title}
      open={open}
      width="md"
      onClose={onClose}
    >
      <DialogContent dividers className={classes.touText}>
      { error ?
        <Alert severity="error">
          <AlertTitle>An error occurred</AlertTitle>
          {error}
        </Alert>
        :
        <FormattedText>{tou.text}</FormattedText>
      }
      </DialogContent>
      <DialogActions>
      { actionRequired && !error ?
        <>
          <Button color="primary" onClick={onAccept} variant="contained">
            Accept
          </Button>
          <Button color="default" onClick={() => setShowConfirmationTou(true)} variant="contained" >
            Decline
          </Button>
        </>
        :
        <Button color="primary" onClick={onClose} variant="contained">
          Close
        </Button>
      }
      </DialogActions>
    </ResponsiveDialog>
    { actionRequired &&
      <ResponsiveDialog open={showConfirmationTou} title="Action required">
        <DialogContent>
          You can only fill out your pre-appointment surveys online after accepting the DATA PRO Terms of Use.
        </DialogContent>
        <DialogActions>
          <Button color="secondary" onClick={() => setShowConfirmationTou(false)} variant="contained" className={classes.reviewButton}>
            Review Terms
          </Button>
          <Button color="primary" onClick={onAccept} variant="contained" >
            Accept
          </Button>
          <Button color="default" onClick={() => {setShowConfirmationTou(false); onDecline && onDecline()}} variant="contained" >
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
  onAccept: PropTypes.func,
  onDecline: PropTypes.func,
}

export default ToUDialog;
