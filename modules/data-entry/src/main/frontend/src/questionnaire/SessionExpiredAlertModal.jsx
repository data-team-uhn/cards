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
 * Component that displays the session expired alert message.
 *
 * @param {bool} open Whether or not this dialog is open
 */
function SessionExpiredAlertModal(props) {
  const { open } = props;

  return (
      <ResponsiveDialog title="Session expired" open={open}>
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
  );
}

export default SessionExpiredAlertModal;
