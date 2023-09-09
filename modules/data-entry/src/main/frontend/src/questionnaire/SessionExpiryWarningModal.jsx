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
 * @param {time} lastActivityTimestamp last activity timestamp
 * @param {func} onStay Callback for when the user decides to stay on the form edit page
 * @param {func} onExit Callback for when the user decides to save and exit editting form
 * @param {func} onExpired Callback for when the countdown reaches 0 and the session expired
 */
function SessionExpiryWarningModal(props) {
  const { lastActivityTimestamp, onStay, onExit, onExpired } = props;

  let [ showCountdownModal, setShowCountdownModal ] = useState(false);
  let [ showSessionExpiredAlert, setShowSessionExpiredAlert ] = useState(false);
  let [ countDownTimer, setCountDownTimer ] = useState();
  const COUNTDOWN_LENGTH = 2 * 60 * 1000;
  let [ countDown, setCountDown ] = useState(COUNTDOWN_LENGTH);

  const diffString = (division, result, count) => {
    if (count > 0) {
      result.push(count + " " + (count == 1 && division[division.length - 1] == "s"
        ? division.substring(0, division.length -1)
        : division));
    }
  }

  // launch timer when opening a form or when done saving
  useEffect(() => {
    // do not set timer if session is already expired
    if (showSessionExpiredAlert) return;

    // Restart the countdown timer
    setCountDown(COUNTDOWN_LENGTH);
    timer && clearTimeout(timer);
    countDownTimer && clearInterval(countDownTimer);

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
            if (timeLeft < 1000) {
              // Session expired
              onExpired?.();
              setShowCountdownModal(false);
              setShowSessionExpiredAlert(true);
              clearTimeout(timer);
              clearInterval(interval);
            }
          }
        }, 1000);
        setCountDownTimer(interval);
    }, 27 * 60 * 1000);

    return () => {clearTimeout(timer); clearInterval(countDownTimer);}
  }, [lastActivityTimestamp]);

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
      result = "Your session will expire in " + diffStrings.join(' ');
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
          <Typography variant="body1" paragraph>Your session has expired. Please refresh this page to keep editing.</Typography>
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
