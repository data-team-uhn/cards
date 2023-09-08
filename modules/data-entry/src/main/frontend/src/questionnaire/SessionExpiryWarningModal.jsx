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

import React from "react";

import { Button, DialogActions, DialogContent, Typography } from "@mui/material";

import ResponsiveDialog from "../components/ResponsiveDialog";

/**
 * Component that displays the session expiry info.
 *
 * @param {bool} open Whether or not this dialog is open
 * @param {func} onStay Callback for when the user decides to stay on the page
 * @param {func} onExit Callback for when the user decides to save and exit
 * @param {int} countDown Countdown time in milliseconds
 */
function SessionExpiryWarningModal(props) {
  const { open, countDown, onStay, onExit } = props;

  const diffString = (division, result, count) => {
    if (count > 0) {
      result.push(count + " " + (count == 1 && division[division.length - 1] == "s"
        ? division.substring(0, division.length -1)
        : division));
    }
  }

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
      result = "Your session will expire in " + diffStrings[0] + ". You should save your answers now to keep your session active and prevent unsaved data loss.";
    }

    return result;
  }

  return (
      <ResponsiveDialog title="Session will expire soon" open={open}>
        <DialogContent dividers>
          <Typography variant="body1" paragraph>{getExpiryMessage()}</Typography>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => onStay()}
            variant="outlined"
            >
            Stay on this page
          </Button>
          <Button
            onClick={() => onExit()}
            variant="contained"
            color="primary"
            >
            Save and exit
          </Button>
        </DialogActions>
      </ResponsiveDialog>
  );
}

export default SessionExpiryWarningModal;
