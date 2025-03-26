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
import { useNavigate } from "react-router";

import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import { v4 as uuidv4 } from 'uuid';
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function NewQuestionnaireDialog(props) {
  const { open, onClose, questionnaires } = props;
  const [ error, setError ] = useState("");
  const [ duplicateTitle, setDuplicateTitle ] = useState(false);
  const [ title, setTitle ] = useState("");

  const navigate = useNavigate();

  let createQuestionnaire = () => {
    setError("");

    // Make a POST request to create a new questionnaire, with a randomly generated UUID
    const URL = "/Questionnaires/" + uuidv4();
    var request_data = new FormData();
    request_data.append('jcr:primaryType', 'cards:Questionnaire');
    request_data.append('title', title);
    fetch( URL, { method: 'POST', body: request_data })
      .then( (response) => {
        if (response.ok) {
          // Redirect the user to the new uuid
          // FIXME: Would be better to somehow obtain the router prefix from props
          // but that is not currently possible
          onClose();
          navigate("/content.html/admin" + URL + ".edit");
        } else {
          return(Promise.reject(response));
        }
      })
      .catch(parseErrorResponse);
  }

  let parseErrorResponse = (response) => {
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
       <Dialog open={open} onClose={onClose} autoFocus={false}>
        <DialogTitle id="new-questionnaire-title">
          Create a new questionnaire
        </DialogTitle>
        <DialogContent>
        {error && <Typography color='error'>{error}</Typography>}
          <TextField
            variant="standard"
            autoFocus
            slotProps={{
              htmlInput: {
                onKeyDown: (event) => {
                  if (event.key == 'Enter' && title) {
                    createQuestionnaire();
                  }
                }
              },
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
            variant="outlined"
            onClick={onClose}
            >
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={createQuestionnaire}
            disabled={!title}
            >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle)(NewQuestionnaireDialog);
