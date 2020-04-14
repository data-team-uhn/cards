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

import { CircularProgress, Dialog, DialogContent, DialogTitle, Fab, List, DialogActions, Button} from "@material-ui/core";
import { ListItemText, Tooltip, Typography, withStyles } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";

import SubjectSelectorList, { createSubjects, SubjectListItem } from "../questionnaire/SubjectSelector.jsx";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

const PROGRESS_SELECT_QUESTIONNAIRE = 0;
const PROGRESS_SELECT_SUBJECT = 1;

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
  const [ presetQuestionnaire, setPresetQuestionnaire ] = useState();
  const [ selectedQuestionnaire, setSelectedQuestionnaire ] = useState();
  const [ selectedSubject, setSelectedSubject ] = useState();
  const [ progress, setProgress ] = useState();
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
    if (presetQuestionnaire) {
      setSelectedQuestionnaire(presetQuestionnaire);
    }
    setProgress(presetPath ? PROGRESS_SELECT_SUBJECT : PROGRESS_SELECT_QUESTIONNAIRE);
  }

  let fetchAndPopulate = (query, setter) => {
    // Send a fetch request to determine the subjects available
    fetch('/query?query=' + encodeURIComponent(query))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        // If the selectedQuestionnaire is currently presetPath, we need to turn it into
        // the actual questionnaire object in this step
        if (typeof presetPath === 'string') {
          let foundQuestionnaire = json["rows"].filter((object) => object["@path"] === presetPath)[0]
          setSelectedQuestionnaire(foundQuestionnaire);
          setPresetQuestionnaire(foundQuestionnaire);
        }
        setter(json["rows"])
      })
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

  // Offer the ability to select a subject or create the form, depending on what step the user is on
  let progressThroughDialog = () => {
    if (progress === PROGRESS_SELECT_QUESTIONNAIRE) {
      if (!selectedQuestionnaire) {
        setError("Please select a questionnaire.");
        return;
      } else {
        setProgress(PROGRESS_SELECT_SUBJECT);
      }
    } else {
      if (!selectedSubject) {
        setError("Please select a subject.");
        return;
      } else {
        initiateFormCreation();
      }
    }
  }

  // Set the currently selected subject and remove all errors.
  let selectSubject = (subject) => {
    setSelectedSubject(subject);
    setError(false);
  }

  let goBack = () => {
    setError(false);
    if (progress === PROGRESS_SELECT_QUESTIONNAIRE) {
      setOpen(false);
    } else {
      setProgress(PROGRESS_SELECT_QUESTIONNAIRE);
    }
  }

  // Unselect the given subject if it is currently selected
  let unselectSubject = (subject) => {
    if (selectedSubject["@path"] == subject["@path"]) {
      setSelectedSubject(null);
    }
  }

  const isFetching = numFetchRequests > 0;

  return (
    <React.Fragment>
      <Dialog open={open} onClose={() => { setOpen(false); }}>
        <DialogTitle id="new-form-title">
          {progress === PROGRESS_SELECT_QUESTIONNAIRE ? "Select a questionnaire" : "Select a subject"}
        </DialogTitle>
        <DialogContent dividers className={classes.NewFormDialog}>
          {error && <Typography color='error'>{error}</Typography>}
          {isFetching && <div className={classes.newFormTypePlaceholder}><CircularProgress size={24} className={classes.newFormTypeLoadingIndicator} /></div>}
          {progress === PROGRESS_SELECT_QUESTIONNAIRE ?
          <React.Fragment>
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
          </React.Fragment>
          :
          <React.Fragment>
            {subjects &&
              <SubjectSelectorList
                disabled={isFetching}
                onAddSubject={addNewSubject}
                onChangeNewSubjects={setNewSubjects}
                onDelete={unselectSubject}
                onError={setError}
                onSelect={selectSubject}
                newSubjects={newSubjects}
                setSubjects={setSubjects}
                subjects={subjects}
                selectedSubject={selectedSubject}
                />
            }
          </React.Fragment>}
        </DialogContent>
        <DialogActions>
          <Button
            variant="contained"
            color="secondary"
            onClick={goBack}
            >
            { progress == PROGRESS_SELECT_QUESTIONNAIRE ?
              "Cancel"
            :
              "Back"
            }
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={progressThroughDialog}
            >
            { progress == PROGRESS_SELECT_QUESTIONNAIRE ?
              "Continue"
            :
              "Create"
            }
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
