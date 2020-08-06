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
import React, { useState } from "react";
import { withRouter } from "react-router-dom";

import { Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton } from "@material-ui/core";
import { Tooltip, Typography, withStyles } from "@material-ui/core";
import { Delete } from "@material-ui/icons"

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

/**
 * A component that renders an icon to open a dialog to delete an entry.
 */
function DeleteButton(props) {
  const { classes, entry, reload, entryType, size } = props;
  const [ open, setOpen ] = useState(false);

  let openDialog = () => {
    setOpen(true);
  }

  let closeDialog = () => {
    setOpen(false);
  }

  let handleDelete = () => {
    let request_data = new FormData();
    request_data.append(':operation', 'delete');
    fetch( entry["@path"], { method: 'POST', body: request_data }).then(reload());
    closeDialog();
  }

  return (
    <React.Fragment>
      <Dialog open={open} onClose={closeDialog}>
        <DialogTitle disableTypography>
          <Typography variant="h6">Delete {entry["@name"]}</Typography>
        </DialogTitle>
        <DialogContent>
            <Typography variant="body1">Are you sure you want to delete {entry["@name"]}?</Typography>
        </DialogContent>
        <DialogActions className={classes.dialogActions}>
            <Button variant="contained" color="secondary" size="small" onClick={() => handleDelete()}>Delete</Button>
            <Button variant="contained" size="small" onClick={closeDialog}>Close</Button>
        </DialogActions>
      </Dialog>
      <Tooltip title={entryType ? "Delete " + entryType : "Delete"}>
        <IconButton component="span" onClick={openDialog} className={classes.iconButton}>
          <Delete fontSize={size ? size : "medium"}/>
        </IconButton>
      </Tooltip>
    </React.Fragment>
  );
}

export default withStyles(QuestionnaireStyle)(withRouter(DeleteButton));
