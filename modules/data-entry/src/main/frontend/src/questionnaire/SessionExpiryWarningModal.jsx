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

import PropTypes from "prop-types";

import { Backdrop, Button, DialogActions, DialogContent, Typography } from "@mui/material";

import ResponsiveDialog from "../components/ResponsiveDialog";

/**
 * Component that displays the session expiry info.
 *
 * @param {number=29*60*1000} activeLength how long does the session remain active (in miliseconds)
 * @param {number=2*60*1000} countdownLength for how long is the countdown warning shown before the session expires (in miliseconds)
 * @param {time} lastActivityTimestamp last activity timestamp
 * @param {func} onStay Callback for when the user decides to stay on the form edit page
 * @param {func} onExit Callback for when the user decides to save and exit editting form
 * @param {func} onExpired Callback for when the countdown reaches 0 and the session expired
 */
function SessionExpiryWarningModal(props) {
  const { activeLength, countdownLength, lastActivityTimestamp, onStay, onExit, onExpired } = props;

  let [ open, setOpen ] = useState(false);
  let [ expired, setExpired ] = useState(false);
  let [ countdownTimer, setCountdownTimer ] = useState();
  let [ countdown, setCountdown ] = useState(countdownLength);

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
    if (expired) return;

    warningTimer && clearTimeout(warningTimer);

    // set the timer to lauch the countdown warning
    const warningTimer = setTimeout(() => {
      // Restart the countdown timer
      setCountdown(countdownLength);
      var timeLeft = countdownLength;
      countdownTimer && clearInterval(countdownTimer);
      // display a modal alert that the session will expire soon
      setOpen(true);

      // start a countdown recalculated every second
      let interval = setInterval(() => {
          timeLeft = timeLeft - 1000;
          if (timeLeft >= 0) {
            setCountdown(timeLeft);
            if (timeLeft < 1000) {
              // Session expired
              onExpired?.();
              setExpired(true);
              clearTimeout(warningTimer);
              clearInterval(interval);
            }
          }
        }, 1000);
        setCountdownTimer(interval);
    }, (activeLength - countdownLength));

    return () => {clearTimeout(warningTimer); countdownTimer && clearInterval(countdownTimer);}
  }, [lastActivityTimestamp]);

  useEffect(() => {
    !open && countdownTimer && clearInterval(countdownTimer);
  }, [open, countdownTimer]);

  const getExpiryMessage = () => {
    // Get the date difference in the format: X minutes or Y seconds
    // skipping any time division that has a value of 0
    let minutes = Math.floor((countdown % (1000 * 60 * 60)) / (1000 * 60));
    let seconds = Math.floor((countdown % (1000 * 60)) / 1000);
    let diffStrings = [];
    diffString("minutes", diffStrings, minutes);
    diffString("seconds", diffStrings, seconds);

    if (diffStrings.length > 0) {
      return "Your session will expire in " + diffStrings.join(' ');
    } else {
      return "Session expired";
    }
  }

  return (
    <>
    <Backdrop
      sx={{ backgroundColor: (theme) => theme.palette.background.paper, zIndex: (theme) => theme.zIndex.drawer + 1 }}
      open={expired}
    />
    <ResponsiveDialog
      open={open}
      title={ getExpiryMessage() }
      >
        <DialogContent dividers>
          <Typography variant="body1" paragraph>
          { expired ?
            "Your session has expired. Please refresh this page to keep editing."
            :
            "You should save your answers now to keep your session active and prevent data loss."
          }
          </Typography>
        </DialogContent>
        <DialogActions>
        { expired ?
          <Button
            onClick={() => location.reload()}
            variant="contained"
          >
            Refresh
          </Button>
          :
          <>
            <Button
              onClick={onExit}
              variant="outlined"
            >
              Save and exit
            </Button>
            <Button
              onClick={() => {setOpen(false); onStay?.();}}
              variant="contained"
            >
              Save and stay on this page
            </Button>
          </>
        }
        </DialogActions>
    </ResponsiveDialog>
    </>
  );
}

SessionExpiryWarningModal.propTypes = {
  activeLength: PropTypes.number,
  countdownLength: PropTypes.number,
  lastActivityTimestamp: PropTypes.object,
  onStay: PropTypes.func,
  onExit: PropTypes.func,
  onExpired: PropTypes.func,
}

SessionExpiryWarningModal.defaultProps = {
  activeLength: 29 * 60 * 1000,
  countdownLength: 2 * 60 * 1000,
}

export default SessionExpiryWarningModal;
