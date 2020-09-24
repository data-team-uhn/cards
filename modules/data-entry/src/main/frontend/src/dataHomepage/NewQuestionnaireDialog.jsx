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

import { Avatar, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Fab, Tooltip, TextField, withStyles } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function NewQuestionnaireDialog(props) {
  const { children, classes, presetPath } = props;
  const [ open, setOpen ] = useState(false);
  const [ isFetching, setFetching ] = useState(false);
  const [ error, setError ] = useState("");
  const [ duplicateTitle, setDuplicateTitle ] = useState(false);
  const [title, setTitle ] = useState("");
  const [ questionnaires, setQuestionnaires ] = useState([]);

  let openDialog = () => {
    setOpen(true);
    setDuplicateTitle(false);
    setError("");
    // Determine what questionnaires are available
    if (questionnaires.length === 0) {
      // Send a fetch request to determine the questionnaires available
      fetch('/query?query=' + encodeURIComponent('select * from [lfs:Questionnaire]'))
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {setQuestionnaires(json["rows"])})
        .finally(() => {setFetching(false)});
      setFetching(true);
    }
  }

  let handleChangeTitle = (value) => {
    // Check if a questionnaire with the given title exists already
    const titles = questionnaires.filter(questionnaire => questionnaire.title == value);
    if (titles.length === 0) {
      setTitle(value);
      setDuplicateTitle(false);
    }
    else {
      setTitle("");
      setDuplicateTitle(true);
    }
  }

  return (
    <React.Fragment>
       <Dialog open={open} onClose={() => { setOpen(false); }} autoFocus={false}>
        <DialogTitle id="new-questionnaire-title">
          Create a new questionnaire
        </DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            placeholder="Enter a title"
            onChange={(event) => { 
              handleChangeTitle(event.target.value);
            }}
            error={duplicateTitle}
            helperText={duplicateTitle ? "A questionnaire with this name already exists" : " "}
          >  
          </TextField>
        </DialogContent>
         <DialogActions>
          <Button
            variant="contained"
            color="primary"
            onClick={() => { setOpen(false); }}
            disabled={!title}
            >
            {'Create'}
          </Button>
          <Button
            variant="contained"
            color="default"
            onClick={() => { setOpen(false); }}
            >
            {'Cancel'}
          </Button>
        </DialogActions>
      </Dialog>
      <div className={classes.newFormButtonWrapper}>
        <Tooltip title="New Questionnaire" aria-label="add">
          <Fab
            color="primary"
            aria-label="add"
            onClick={() => { openDialog(); }}
            disabled={!open && isFetching}
          >
            <AddIcon />
          </Fab>
        </Tooltip>
        {!open && isFetching && <CircularProgress size={56} className={classes.newFormLoadingIndicator} />}
      </div>
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle)(NewQuestionnaireDialog);
