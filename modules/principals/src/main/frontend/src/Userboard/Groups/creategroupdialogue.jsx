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

import React, { useState } from "react";
import PropTypes from "prop-types";
import { checkPropTypes } from "../../propTypes";
import { Alert, Button, Grid, Dialog, DialogTitle, DialogActions, DialogContent, TextField } from "@mui/material";
import { withStyles } from 'tss-react/mui';

import userboardStyle from '../userboardStyle.jsx';

function CreateGroupDialogue(props) {
  checkPropTypes(CreateGroupDialogue, props);
  const { classes, reload, isOpen, handleClose } = props;

  const [ error, setError ] = useState("");
  const [ newName, setNewName ] = useState("");

  let handleCreateGroup = () => {
    setError("");
    let formData = new FormData();
    formData.append(':name', newName);
    let url = "/system/userManager/group.create.json";

    fetch(url, {
        method: 'POST',
        credentials: 'include',
        body: formData
    })
    .then((response) => {
        if (!response.ok) {
          setError(response.statusText);
          return;
        }
        reload();
        handleClose();
    });
  }

  return (
    <Dialog
      open={isOpen}
      onClose={handleClose}
    >
      <DialogTitle>Create New Group</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error">{error}</Alert>}
        <Grid container>
          <Grid>
            <TextField
              variant="standard"
              id="name"
              name="name"
              label="Name"
              onChange={(event) => { setError(""); setNewName(event.target.value); }}
              autoFocus
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions className={classes.dialogActions}>
        <Button
          variant="outlined"
          onClick={handleClose}
        >
            Cancel
          </Button>
        <Button
          variant="contained"
          onClick={(event) => { event.preventDefault(); handleCreateGroup(); }}
        >
          Create Group
        </Button>
      </DialogActions>
    </Dialog>
  );
}

CreateGroupDialogue.propTypes = {
  isOpen: PropTypes.bool,
  handleClose: PropTypes.func.isRequired,
  reload: PropTypes.func.isRequired
}

export default withStyles(CreateGroupDialogue, userboardStyle);
