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

import React, { useState, useRef } from "react";
import PropTypes from "prop-types";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Typography,
  withStyles
} from "@material-ui/core";

import moment from "moment";
import DeleteIcon from "@material-ui/icons/Delete";
import QuestionnaireStyle from "./QuestionnaireStyle";

let DeleteQuestionDialog = (props) => {
  let [openDeleteDialog, setOpenDeleteDialog] = useState(false);
  let [ forms, setForms] = useState(0);

  let deleteQuestionWarningMessage = () => {
    const formsExist = forms && forms > 0;
    if (!formsExist) {
      let uuid;
      // Get uuid of questionnaire
      fetch(`/Questionnaires/${props.id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => { uuid = (response['jcr:uuid']) });

      // Find all forms with that questionnaire uuid
      fetch('/query?query=' + encodeURIComponent(`select * from [lfs:Form] as n WHERE n.'questionnaire'='${uuid}'`))
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => { parseResult(json); });
    }
    // Count the number of returned forms
    let parseResult = (forms) => {
      let filteredForms = Object.values(forms['rows']).length;
      setForms(filteredForms);
   }
    return formsExist
      ? `There are ${forms} forms filled out for this questionnaire. Are you sure you wish to proceed ?`
      : "Are you sure you wish to proceed ?"
  }

  return (
    <React.Fragment>
      <Dialog id="deleteDialog" open={openDeleteDialog} onClose={() => { setOpenDeleteDialog(false); }}>
        <form action={props.data["@path"]} method="DELETE" key={props.id}>
          <DialogTitle>
            <Typography>Confirm question deletion</Typography>
          </DialogTitle>
          <DialogContent>
            <Typography>{ deleteQuestionWarningMessage() }</Typography>
          </DialogContent>
          <DialogActions>
            <Button
              type="submit"
              variant="contained"
              color="secondary"
              onClick={() => { setOpenDeleteDialog(false); }}
              >
              {'Yes, delete'}
            </Button>
            <Button
              variant="contained"
              color="default"
              onClick={() => { setOpenDeleteDialog(false); }}
              >
              {'Cancel'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
      <IconButton onClick={ () => {  setOpenDeleteDialog(true); }}>
        <DeleteIcon />
      </IconButton>
    </React.Fragment>
  );
};

DeleteQuestionDialog.propTypes = {
  data: PropTypes.object.isRequired,
  id: PropTypes.string.isRequired
};

export default withStyles(QuestionnaireStyle)(DeleteQuestionDialog);
