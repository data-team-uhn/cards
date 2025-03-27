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
import PropTypes from "prop-types";
import { checkPropTypes } from "../propTypes";
import { Button, Dialog, DialogTitle, DialogActions, DialogContent, Typography } from "@mui/material";

import { withStyles } from 'tss-react/mui';

import userboardStyle from './userboardStyle.jsx';

function DeletePrincipalDialog(props) {
  checkPropTypes(DeletePrincipalDialog, props);
  const { classes, name, type, url, reload, isOpen, handleClose } = props;

  let handleDelete = () => {
    let path = url + name + ".delete.html";

    fetch(path, {
        method: 'POST',
        credentials: 'include'
    })
    .then(() => {
        reload();
        handleClose();
    })
    .catch((error) => console.log(error?.statusText ? error.statusText : error));
  }

  return (
    <Dialog
      open={isOpen}
      onClose={() => handleClose()}
    >
      <DialogTitle>
        Delete {name}
      </DialogTitle>
      <DialogContent>
        <Typography>Are you sure you want to delete {type} {name}?</Typography>
      </DialogContent>
      <DialogActions className={classes.dialogActions}>
        <Button
          variant="outlined"
          onClick={() => handleClose()}
        >
          Cancel
        </Button>
        <Button
          variant="contained"
          color="error"
          onClick={() => handleDelete()}
        >
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  );
}

DeletePrincipalDialog.propTypes = {
  isOpen: PropTypes.bool,
  handleClose: PropTypes.func.isRequired,
  name: PropTypes.string.isRequired,
  reload: PropTypes.func.isRequired,
  url: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired
}

export default withStyles(DeletePrincipalDialog, userboardStyle);
