/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

import React, { useEffect, useState } from "react";

import { Button, DialogActions, DialogContent, Typography } from "@mui/material";

import ResponsiveDialog from "../components/ResponsiveDialog";

/**
 * Component that displays the session expiry info.
 *
 * @param {bool} isEdit Whether or not form is in edit mode
 * @param {time} lastSaveTimestamp last form save timestamp
 * @param {bool} saveInProgress Whether or not form saving is in progress
 * @param {func} onStay Callback for when the user decides to stay on the form edit page
 * @param {func} onExit Callback for when the user decides to save and exit editting form
 * @param {func} saveDataWithCheckin Callback for when to save and checkin form after countdown is timedout
 */
function SessionExpiryWarningModal(props) {
  const { isEdit, lastSaveTimestamp, saveInProgress, onStay, onExit, saveDataWithCheckin } = props;

  let [ showCountdownModal, setShowCountdownModal ] = useState(false);
  let [ showSessionExpiredAlert, setShowSessionExpiredAlert ] = useState(false);
  let [ autoCheckinTimer, setAutoCheckinTimer ] = useState();
  const SESSION_EXPIRATION_DELAY = 2 * 60 * 1000;
  let [ countDown, setCountDown ] = useState(SESSION_EXPIRATION_DELAY);

  const diffString = (division, result, count) => {
    if (count > 0) {
      result.push(count + " " + (count == 1 && division[division.length - 1] == "s"
        ? division.substring(0, division.length -1)
        : division));
    }
  }

  useEffect(() => {
    // launch timer when opening a form or when done saving
    if (showSessionExpiredAlert || !isEdit || lastSaveTimestamp && saveInProgress) return;
    
    // Restart the countdown timer
    setCountDown(SESSION_EXPIRATION_DELAY);
    timer && clearTimeout(timer);
    autoCheckinTimer && clearInterval(autoCheckinTimer);

    // set the timer in 27 minutes to launch the checkin
    const timer = setTimeout(() => {
	  let countDownStartTime = new Date().getTime();
	  // display a modal alert that the session will expire in 2 minutes
	  setShowCountdownModal(true);

      // start a countdown recalculated every second
	  let interval = setInterval(() => {
	      let timeLeft = countDownStartTime + countDown - new Date().getTime();
	      if (timeLeft >= 0) {
	        setCountDown(timeLeft);
	        //  save & checkin at minute 29
		    if (timeLeft < 1000) {
			  setShowCountdownModal(false);
			  clearTimeout(timer);
			  clearInterval(interval);
		      // Do a save & checkin for the current form
		      saveDataWithCheckin(undefined, () => {
		        // On success, display an alert modal
		        setShowSessionExpiredAlert(true);
		      });
		    }
	      }
	    }, 1000);
	    setAutoCheckinTimer(interval);
    }, 27 * 60 * 1000);

    return () => {clearTimeout(timer); clearInterval(autoCheckinTimer);}
  }, [isEdit, lastSaveTimestamp, saveInProgress]);

  const getExpiryMessage = () => {
    let result = "";
    // Get the date difference in the format: X minutes or Y seconds
    // skipping any time division that has a value of 0
    let minutes = Math.floor((countDown % (1000 * 60 * 60)) / (1000 * 60));
    let seconds = Math.floor((countDown % (1000 * 60)) / 1000);
    let diffStrings = [];
    diffString("minutes", diffStrings, minutes);
    diffString("seconds", diffStrings, seconds);

    if (diffStrings.length > 0) {
      result = "Your session will expire in " + diffStrings[0] + ".";
    }

    return result;
  }

  return (
    <>
      <ResponsiveDialog title={getExpiryMessage()} open={showCountdownModal}>
        <DialogContent dividers>
          <Typography variant="body1" paragraph>You should save your answers now to keep your session active and prevent data loss.</Typography>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => onExit()}
            variant="outlined"
            color="primary"
            >
            Save and exit
          </Button>
          <Button
            onClick={() => {setShowCountdownModal(false); onStay();}}
            variant="contained"
            >
            Stay on this page
          </Button>
        </DialogActions>
      </ResponsiveDialog>
      <ResponsiveDialog title="Session expired" open={showSessionExpiredAlert}>
        <DialogContent dividers>
          <Typography variant="body1" paragraph>Your session has expired, please refresh to keep editing</Typography>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => location.reload()}
            variant="contained"
            color="primary"
            >
            Refresh
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    </>
  );
}

export default SessionExpiryWarningModal;
