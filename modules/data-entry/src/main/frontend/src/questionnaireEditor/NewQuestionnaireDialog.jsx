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

import { Avatar, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography, withStyles } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";
import { v4 as uuidv4 } from 'uuid';
import NewItemButton from "../components/NewItemButton.jsx";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function NewQuestionnaireDialog(props) {
  const { children, classes } = props;
  const [ open, setOpen ] = useState(false);
  const [ isFetching, setFetching ] = useState(false);
  const [ error, setError ] = useState("");
  const [ duplicateTitle, setDuplicateTitle ] = useState(false);
  const [ title, setTitle ] = useState("");
  const [ questionnaires, setQuestionnaires ] = useState([]);

  let openDialog = () => {
    setOpen(true);
    setDuplicateTitle(false);
    setError("");
    // Determine what questionnaires are available
    if (questionnaires.length === 0) {
      // Send a fetch request to determine the questionnaires available
      fetch('/query?query=' + encodeURIComponent('select * from [cards:Questionnaire]'))
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {setQuestionnaires(json["rows"])})
        .finally(() => {setFetching(false)});
      setFetching(true);
    }
  }

  let createForm = () => {
    setError("");

    // Make a POST request to create a new form, with a randomly generated UUID
    const URL = "/Questionnaires/" + uuidv4();
    var request_data = new FormData();
    request_data.append('jcr:primaryType', 'cards:Questionnaire');
    request_data.append('title', title);
    fetch( URL, { method: 'POST', body: request_data })
      .then( (response) => {
        setFetching(false);
        if (response.ok) {
          // Redirect the user to the new uuid
          // FIXME: Would be better to somehow obtain the router prefix from props
          // but that is not currently possible
          props.history.push("/content.html/admin" + URL + ".edit");
        } else {
          return(Promise.reject(response));
        }
      })
      .catch(parseErrorResponse);
    setFetching(true);
  }
  let parseErrorResponse = (response) => {
    setFetching(false);
    setError(`New questionnaire request failed with error code ${response.status}: ${response.statusText}`);
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
        {error && <Typography color='error'>{error}</Typography>}
          <TextField
            autoFocus
            inputProps={{
              onKeyDown: (event) => {
                if (event.key == 'Enter' && title) {
                  createForm();
                }
              }
            }}
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
            onClick={createForm}
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
      <NewItemButton
           title="New questionnaire"
           onClick={() => { openDialog(); }}
           inProgress={!open && isFetching}
      />
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle)(withRouter(NewQuestionnaireDialog));
