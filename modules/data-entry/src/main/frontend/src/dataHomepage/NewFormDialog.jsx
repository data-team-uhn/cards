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
import uuid from "uuid/v4";

import { Button, CircularProgress, Dialog, DialogTitle, List, ListItem, ListItemText, Typography, withStyles } from "@material-ui/core";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function NewFormDialog(props) {
  const { children, classes, presetPath } = props;
  const [ open, setOpen ] = useState(false);
  const [ questionnaires, setQuestionnaires ] = useState([]);
  const [ isFetching, setFetching ] = useState(false);
  const [ error, setError ] = useState("");

  let createForm = (questionnaireReference) => {
    setOpen(true);
    setError("");
    
    // Make a POST request to create a new form, with a randomly generated UUID
    const URL = "/Forms/" + uuid();
    var request_data = new FormData();
    request_data.append('jcr:primaryType', 'lfs:Form');
    request_data.append('questionnaire', questionnaireReference);
    request_data.append('questionnaire@TypeHint', 'Reference');
    fetch( URL, { method: 'POST', body: request_data })
      .then( (response) => {
        if (response.ok) {
          // Redirect the user to the new uuid
          // FIXME: Would be better to somehow obtain the router prefix from props
          // but that is not currently possible
          props.history.push("/content.html" + URL);
        } else {
          return(Promise.reject(response));
        }
      })
      .catch(parseErrorResponse)
      .finally(() => {setFetching(false)});
    setFetching(true);
  }

  let openDialog = () => {
    // Determine what questionnaires are available
    setOpen(true);
    setError("");
    if (questionnaires.length === 0) {
      // Send a fetch request to determine the questionnaires available
      fetch('/query?query=' + encodeURIComponent('select * from [lfs:Questionnaire]'))
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {setQuestionnaires(json)})
        .catch(parseErrorResponse)
        .finally(() => {setFetching(false)});
      setFetching(true);
    }
  }

  // Parse an errored response object
  let parseErrorResponse = (response) => {
    setError(`New form request failed with error code ${response.status}: ${response.statusText}`);
  }

  return (
    <React.Fragment>
      { /* Only create a dialog if we need to allow the user to choose from multiple questionnaires */
      (!presetPath) && <Dialog open={open} onClose={() => { setOpen(false); }}>
        <DialogTitle id="new-form-title">
          Select questionnaire
        </DialogTitle>
        {error && <Typography color='error'>{error}</Typography>}
        {questionnaires &&
          <List>
            {questionnaires.map((questionnaire) => {
              return (
              <ListItem button key={questionnaire["jcr:uuid"]} onClick={() => {createForm(questionnaire["@path"])}} disabled={isFetching}>
                <ListItemText primary={questionnaire["title"]}>
                </ListItemText>
              </ListItem>);
            })}
          </List>
        }
        {isFetching && <CircularProgress size={24} className={classes.newFormLoadingIndicator} />}
      </Dialog>}
      <div className={classes.newFormButtonWrapper}>
        <Button
          variant="contained"
          color="default"
          onClick={presetPath ? () => {createForm(presetPath)} : openDialog}
          disabled={!open && isFetching}
          >
          { children }
        </Button>
        {!open && isFetching && <CircularProgress size={24} className={classes.newFormLoadingIndicator} />}
      </div>
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle)(withRouter(NewFormDialog));