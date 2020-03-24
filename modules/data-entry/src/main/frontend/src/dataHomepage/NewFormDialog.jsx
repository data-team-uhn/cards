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

import { CircularProgress, Dialog, DialogContent, DialogTitle, Fab, Grid, List, DialogActions, Button} from "@material-ui/core";
import { ListItemText, Tooltip, Typography, withStyles } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";

import SubjectSelectorList, { createSubjects, SubjectListItem } from "../questionnaire/SubjectSelector.jsx";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

/**
 * A component that renders a FAB to open a dialog to create a new form.
 *
 * @param {presetPath} string The questionnaire to use automatically, if any.
 */
function NewFormDialog(props) {
  const { children, classes, presetPath } = props;
  const [ open, setOpen ] = useState(false);
  const [ initialized, setInitialized ] = useState(false);
  const [ questionnaires, setQuestionnaires ] = useState([]);
  const [ subjects, setSubjects ] = useState([]);
  const [ newSubjects, setNewSubjects ] = useState([]);
  const [ selectedQuestionnaire, setSelectedQuestionnaire ] = useState(presetPath);
  const [ selectedSubject, setSelectedSubject ] = useState();
  const [ numFetchRequests, setNumFetchRequests ] = useState(0);
  const [ error, setError ] = useState("");

  let initiateFormCreation = () => {
    if (newSubjects.length == 0) {
      // No new subjects need to be created, just create the form
      createForm();
    } else {
      // New subjects need to be created
      createSubjects(newSubjects, selectedSubject, createForm);
    }
  }

  let createForm = (subject) => {
    setError("");

    // Get the subject identifier, if necessary
    if (!subject) {
      subject = selectedSubject["@path"];
    }

    // Make a POST request to create a new form, with a randomly generated UUID
    const URL = "/Forms/" + uuid();
    var request_data = new FormData();
    request_data.append('jcr:primaryType', 'lfs:Form');
    request_data.append('questionnaire', selectedQuestionnaire["@path"]);
    request_data.append('questionnaire@TypeHint', 'Reference');
    request_data.append('subject', subject);
    request_data.append('subject@TypeHint', 'Reference');
    fetch( URL, { method: 'POST', body: request_data })
      .then( (response) => {
        setNumFetchRequests((num) => (num-1));
        if (response.ok) {
          // Redirect the user to the new uuid
          // FIXME: Would be better to somehow obtain the router prefix from props
          // but that is not currently possible
          props.history.push("/content.html" + URL);
        } else {
          return(Promise.reject(response));
        }
      })
      .catch(parseErrorResponse);
    setNumFetchRequests((num) => (num+1));
  }

  let openDialog = () => {
    // Determine what questionnaires and subjects are available
    if (!initialized) {
      fetchAndPopulate("select * from [lfs:Questionnaire]", setQuestionnaires);
      setInitialized(true);
    }
    setOpen(true);
    setError("");
  }

  let fetchAndPopulate = (query, setter) => {
    // Send a fetch request to determine the subjects available
    fetch('/query?query=' + encodeURIComponent(query))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {setter(json["rows"])})
      .catch(parseErrorResponse)
      .finally(() => {setNumFetchRequests((num) => (num-1))});
  }

  // Parse an errored response object
  let parseErrorResponse = (response) => {
    setError(`New form request failed with error code ${response.status}: ${response.statusText}`);
  }

  // Add a new subject
  let addNewSubject = () => {
    setNewSubjects((old) => {
      let updated = old.slice();
      updated.push("");
      return updated;
    });
  }

  const isFetching = numFetchRequests > 0;

  return (
    <React.Fragment>
      <Dialog open={open} onClose={() => { setOpen(false); }}>
        <DialogTitle id="new-form-title">
          Select a questionnaire
        </DialogTitle>
        <DialogContent dividers>
        {error && <Typography color='error'>{error}</Typography>}
        {isFetching && <div className={classes.newFormTypePlaceholder}><CircularProgress size={24} className={classes.newFormTypeLoadingIndicator} /></div>}
        <Grid container>
          <Grid item xs={6}>
            <Typography variant="h4">Questionnaire</Typography>
            {questionnaires &&
              <List>
                {questionnaires.map((questionnaire) => {
                  return (
                  <SubjectListItem
                    key={questionnaire["jcr:uuid"]}
                    onClick={() => {setSelectedQuestionnaire(questionnaire)}}
                    disabled={isFetching}
                    selected={questionnaire["jcr:uuid"] === selectedQuestionnaire?.["jcr:uuid"]}
                    >
                    <ListItemText primary={questionnaire["title"]} />
                  </SubjectListItem>);
                })}
              </List>
            }
          </Grid>
          <Grid item xs={6}>
            <Typography variant="h4">Subject</Typography>
            {subjects &&
              <SubjectSelectorList
                disabled={isFetching}
                onAddSubject={addNewSubject}
                onChangeNewSubjects={setNewSubjects}
                onError={setError}
                onSelect={setSelectedSubject}
                newSubjects={newSubjects}
                setSubjects={setSubjects}
                subjects={subjects}
                selectedSubject={selectedSubject}
                />
            }
          </Grid>
        </Grid>
        </DialogContent>
        <DialogActions>
          <Button
            variant="contained"
            color="primary"
            onClick={initiateFormCreation}
            >
            Create
          </Button>
        </DialogActions>
      </Dialog>
      <div className={classes.newFormButtonWrapper}>
        <Tooltip title={children} aria-label="add">
          <Fab
            color="primary"
            aria-label="add"
            onClick={openDialog}
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

export default withStyles(QuestionnaireStyle)(withRouter(NewFormDialog));
