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
import React, { useRef, useState, useEffect } from "react";
import { withRouter } from "react-router-dom";
import { v4 as uuidv4 } from 'uuid';

import { CircularProgress, Button, Dialog, DialogActions, DialogContent, DialogTitle, Fab, Input, List} from "@material-ui/core";
import { ListItemText, Tooltip, Typography, withStyles } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";
import AssignmentIcon from "@material-ui/icons/Assignment";

import SubjectSelectorList, { NewSubjectDialog, SubjectListItem, parseToArray } from "../questionnaire/SubjectSelector.jsx";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

const PROGRESS_SELECT_QUESTIONNAIRE = 0;
const PROGRESS_SELECT_SUBJECT = 1;

/**
 * A component that renders a FAB to open a dialog to create a new form.
 *
 * @param {presetPath} string The questionnaire to use automatically, if any.
 */
function NewFormDialog(props) {
  const { children, classes, presetPath, currentSubject } = props;
  const [ open, setOpen ] = useState(false);
  const [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  const [ initialized, setInitialized ] = useState(false);
  const [ questionnaires, setQuestionnaires ] = useState([]);
  const [ presetQuestionnaire, setPresetQuestionnaire ] = useState();
  const [ selectedQuestionnaire, setSelectedQuestionnaire ] = useState();
  const [ selectedSubject, setSelectedSubject ] = useState();
  const [ progress, setProgress ] = useState();
  const [ numFetchRequests, setNumFetchRequests ] = useState(0);
  const [ error, setError ] = useState("");
  const [ numForms, setNumForms ] = useState(0);
  const [ relatedForms, setRelatedForms ] = useState();
  const [ disableProgress, setDisableProgress ] = useState(false);

  let createForm = (subject) => {
    setError("");
    setNumForms(0);

    // Get the subject identifier, if necessary
    if (!subject) {
      subject = selectedSubject["@path"];
    }

    // Make a POST request to create a new form, with a randomly generated UUID
    const URL = "/Forms/" + uuidv4();
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
    setNumForms(0);
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

  // Offer the ability to select a subject or create the form, depending on what step the user is on
  let progressThroughDialog = () => {
    if (disableProgress) {
      // don't allow user to progress if the maxPerSubject has been reached
      return;
    } else if (progress === PROGRESS_SELECT_QUESTIONNAIRE) {
      if (!selectedQuestionnaire) {
        setError("Please select a questionnaire.");
        return;
      } 
      if (selectedSubject) {
        createForm();
      }
      else {
        setProgress(PROGRESS_SELECT_SUBJECT);
      }
    } else {
      if (!selectedSubject) {
        setError("Please select a subject.");
        return;
      } else {
        createForm();
      }
    }
  }

  // Set the currently selected subject and remove all errors.
  let selectSubject = (subject) => {
    setSelectedSubject(subject);
    setError(false);
    setNumForms(0);
  }

  let goBack = () => {
    setError(false);
    setNumForms(0);
    // Exit the dialog if we're at the first page or if there is a preset path
    if (progress === PROGRESS_SELECT_QUESTIONNAIRE || presetPath) {
      setOpen(false);
    } else {
      setProgress(PROGRESS_SELECT_QUESTIONNAIRE);
      setSelectedSubject(null);
    }
  }

  // Unselect the given subject if it is currently selected
  let unselectSubject = (subject) => {
    if (selectedSubject["@path"] == subject["@path"]) {
      setSelectedSubject(null);
    }
  }

  // if the number of related forms of a certain questionnaire type is at the maxPerSubject, an error is set
  useEffect(() => {
    // only considers currentSubject, since setting this error would only be necessary on the 'Subject' page, which would set a currentSubject
    if (currentSubject && relatedForms && selectedQuestionnaire) {
      let atMax = (relatedForms.length && (relatedForms.filter((i) => (i["@name"] == selectedQuestionnaire["@name"])).length >= selectedQuestionnaire?.["maxPerSubject"]));
      if (atMax) {
        setError(`${currentSubject?.["type"]["@name"]} ${currentSubject?.["identifier"]} already has ${selectedQuestionnaire?.["maxPerSubject"]} ${selectedQuestionnaire?.["title"]} form(s) filled out.`);
        setDisableProgress(true);
      }
      else {
        setError("");
        setDisableProgress(false);
      }
    }
  }, [selectedQuestionnaire]);

  // get all the forms related to the selectedSubject, saved in the `relatedForms` state
  let filterQuestionnaire = () => {
    fetch(`/query?query=SELECT distinct q.* FROM [lfs:Questionnaire] AS q inner join [lfs:Form] as f on f.'questionnaire'=q.'jcr:uuid' where f.'subject'='${(currentSubject || selectedSubject)?.['jcr:uuid']}'`)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((response) => {
      setRelatedForms(response.rows);
    })
  }

  useEffect(() => {
    if (progress === PROGRESS_SELECT_QUESTIONNAIRE && (selectedSubject || currentSubject)) {filterQuestionnaire();}
    else setRelatedForms([]);
  }, [progress]);
  
  const isFetching = numFetchRequests > 0;

  useEffect(() => {
    if (currentSubject && selectedQuestionnaire) {
      // if the current subject is the only required type for the questionnaire
      if ( !selectedQuestionnaire?.["requiredSubjectTypes"] ||
        (selectedQuestionnaire?.["requiredSubjectTypes"]?.length == 1 &&
        (currentSubject.type["@path"] == selectedQuestionnaire["requiredSubjectTypes"][0]["@path"]))) {
        setSelectedSubject(currentSubject); // now that selectedsubject is set, will create form with current subject as subject
      }
      else {
        setSelectedSubject(null); // remove selectedsubject so next dialog can open (should not create form right away)
      }
    }
  }, [selectedQuestionnaire, open])

  return (
    <React.Fragment>
      <Dialog open={open} onClose={() => { setOpen(false); }}>
        <DialogTitle id="new-form-title">
          {progress === PROGRESS_SELECT_QUESTIONNAIRE ? "Select a questionnaire" : "Select a subject"}
        </DialogTitle>
        <DialogContent dividers className={classes.NewFormDialog}>
          {error && (!newSubjectPopperOpen) && <Typography color='error'>{error}</Typography>}
          {isFetching && <div className={classes.newFormTypePlaceholder}><CircularProgress size={24} className={classes.newFormTypeLoadingIndicator} /></div>}
          {progress === PROGRESS_SELECT_QUESTIONNAIRE ?
          <React.Fragment>
            <Typography variant="h4">Questionnaire</Typography>
            {questionnaires && relatedForms &&
              <List>
                {questionnaires.map((questionnaire) => {
                  // if selectedSubject has already been set, check if maxPerSubject has been reached for each questionnaire
                  // atMax returns true if the number of `relatedForms` is greater than the maxPerSubject
                  let atMax = (relatedForms.length && (relatedForms.filter((i) => (i["@name"] == questionnaire["@name"])).length >= questionnaire?.["maxPerSubject"]));
                  return (
                  <SubjectListItem
                    key={questionnaire["jcr:uuid"]}
                    onClick={() => {setSelectedQuestionnaire(questionnaire)}}
                    disabled={isFetching}
                    selected={questionnaire["jcr:uuid"] === selectedQuestionnaire?.["jcr:uuid"]}
                    avatarIcon={AssignmentIcon}
                    >
                    <div className={`${atMax ? classes.questionnaireDisabledListItem : classes.questionnaireListItem}`}>
                      <ListItemText primary={questionnaire["title"]} secondary={questionnaire["description"]}/>
                    </div>
                  </SubjectListItem>);
                })}
              </List>
            }
          </React.Fragment>
          :
          <React.Fragment>
            { /* We need selectedQuestionnaire to be filled out before this renders, or it will try grabbing the wrong subjects */
            selectedQuestionnaire && <SubjectSelectorList
              allowedTypes={parseToArray(selectedQuestionnaire["requiredSubjectTypes"])}
              disabled={isFetching}
              onDelete={unselectSubject}
              onError={setError}
              onSelect={selectSubject}
              selectedSubject={selectedSubject}
              currentSubject={currentSubject}
              selectedQuestionnaire={selectedQuestionnaire}
              disableProgress={setDisableProgress}
              />}
          </React.Fragment>}
        </DialogContent>
        <DialogActions>
          {progress === PROGRESS_SELECT_SUBJECT &&
            <Button
              variant="contained"
              color="secondary"
              onClick={() => { setNewSubjectPopperOpen(true); setError(); }}
              className={classes.createNewSubjectButton}
              >
              New subject
            </Button>
          }
          <Button
            variant="contained"
            color="default"
            onClick={goBack}
            >
            { (progress == PROGRESS_SELECT_QUESTIONNAIRE || presetPath) ?
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
              "Create Form"
            }
          </Button>
        </DialogActions>
      </Dialog>
      <NewSubjectDialog
        allowedTypes={parseToArray(selectedQuestionnaire?.["requiredSubjectTypes"])}
        disabled={isFetching}
        onClose={() => { setNewSubjectPopperOpen(false); setError(); setNumForms(0);}}
        onChangeSubject={(event) => {setNewSubjectName(event.target.value);}}
        currentSubject={currentSubject}
        onSubmit={createForm}
        open={newSubjectPopperOpen}
        />
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
