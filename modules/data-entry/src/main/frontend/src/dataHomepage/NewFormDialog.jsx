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
import React, { useState, useEffect, useContext } from "react";
import { withRouter } from "react-router-dom";
import { v4 as uuidv4 } from 'uuid';

import { CircularProgress, Button, Dialog, DialogActions, DialogContent, DialogTitle, Fab } from "@material-ui/core";
import { Tooltip, Typography, withStyles } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";
import MaterialTable from "material-table";

import SubjectSelectorList, { NewSubjectDialog, parseToArray } from "../questionnaire/SubjectSelector.jsx";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

const PROGRESS_SELECT_QUESTIONNAIRE = 0;
const PROGRESS_SELECT_SUBJECT = 1;
export const MODE_ACTION = 0;
export const MODE_DIALOG = 1;

/**
 * A component that renders a FAB to open a dialog to create a new form.
 *
 * @param {presetPath} string The questionnaire to use automatically, if any.
 */
function NewFormDialog(props) {
  const { children, classes, presetPath, currentSubject, theme, mode, open, onClose } = { mode:MODE_ACTION, open: false, ...props };
  const [ dialogOpen, setDialogOpen ] = useState(false);
  const [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  const [ initialized, setInitialized ] = useState(false);
  const [ selectedQuestionnaire, setSelectedQuestionnaire ] = useState();
  const [ selectedSubject, setSelectedSubject ] = useState();
  const [ progress, setProgress ] = useState();
  const [ numFetchRequests, setNumFetchRequests ] = useState(0);
  const [ error, setError ] = useState("");
  const [ relatedForms, setRelatedForms ] = useState();
  const [ disableProgress, setDisableProgress ] = useState(false);
  const [ rowCount, setRowCount ] = useState(5);
  const [ wasOpen, setWasOpen ] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let createForm = (subject) => {
    setError("");

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
    fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
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
    if (!initialized) {
      if (typeof presetPath === 'string') {
        fetchQuestionnaire(presetPath.substring(presetPath.lastIndexOf("/") + 1));
      }
      setInitialized(true);
    }
    setDialogOpen(true);
    setError("");
    setProgress(presetPath ? PROGRESS_SELECT_SUBJECT : PROGRESS_SELECT_QUESTIONNAIRE);
  }

  let fetchQuestionnaire = (questionnaireName) => {
    // Send a fetch request to determine the subjects available for the specified questionnaire
    let query = `select * from [lfs:Questionnaire] as n where name()='${questionnaireName}'`;
    fetchWithReLogin(globalLoginDisplay, '/query?query=' + encodeURIComponent(query))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        // If a matching questionnaire was found, turn it into the actual questionnaire object
        if (json["returnedrows"] === 1) {
          let foundQuestionnaire = json["rows"][0]
          setSelectedQuestionnaire(foundQuestionnaire);
        }
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
  }

  let goBack = () => {
    setError(false);
    // Exit the dialog if we're at the first page or if there is a preset path
    if (progress === PROGRESS_SELECT_QUESTIONNAIRE || presetPath) {
      setDialogOpen(false);
      if (onClose) {
        onClose();
      }
    } else {
      setProgress(PROGRESS_SELECT_QUESTIONNAIRE);
      setSelectedSubject(null);
      if (disableProgress) setDisableProgress(false);
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
    fetchWithReLogin(globalLoginDisplay, `/query?query=SELECT distinct q.* FROM [lfs:Questionnaire] AS q inner join [lfs:Form] as f on f.'questionnaire'=q.'jcr:uuid' where f.'subject'='${(currentSubject || selectedSubject)?.['jcr:uuid']}'`)
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
  if (wasOpen !== open) {
    setWasOpen(open);
    if (MODE_DIALOG) {
      openDialog();
    }
  }

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
  }, [selectedQuestionnaire, dialogOpen])

  return (
    <React.Fragment>
      <Dialog
        open={mode === MODE_ACTION ? dialogOpen : open}
        onClose={() => {
          setDialogOpen(false);
          if (onClose) {
            onClose();
          }
        }}
      >
        <DialogTitle id="new-form-title">
          {progress === PROGRESS_SELECT_QUESTIONNAIRE ? "Select a questionnaire" : "Select a subject"}
        </DialogTitle>
        <DialogContent dividers className={classes.NewFormDialog}>
          {error && (!newSubjectPopperOpen) && <Typography color='error'>{error}</Typography>}
          {isFetching && <div className={classes.newFormTypePlaceholder}><CircularProgress size={24} className={classes.newFormTypeLoadingIndicator} /></div>}
          {progress === PROGRESS_SELECT_QUESTIONNAIRE ?
          <React.Fragment>
            {relatedForms &&
              <MaterialTable
                title=""
                columns={[
                  { title: 'Questionnaire', field: 'title' },
                  { title: 'Description', field: 'description' },
                ]}
                data={query => {
                  let url = new URL("/query", window.location.origin);
                  url.searchParams.set("query", `select * from [lfs:Questionnaire] as n${query.search ? ` WHERE CONTAINS(n.'title', '*${query.search}*')` : ""}`);
                  url.searchParams.set("limit", query.pageSize);
                  url.searchParams.set("offset", query.page*query.pageSize);
                  return fetchWithReLogin(globalLoginDisplay, url)
                    .then(response => response.json())
                    .then(result => {
                      return {
                        data: result["rows"],
                        page: Math.trunc(result["offset"]/result["limit"]),
                        totalCount: result["totalrows"],
                      }}
                    )
                }}
                options={{
                  search: true,
                  actionsColumnIndex: -1,
                  addRowPosition: 'first',
                  pageSize: rowCount,
                  rowStyle: rowData => ({
                    // /* It doesn't seem possible to alter the className from here */
                    backgroundColor: (selectedQuestionnaire?.["jcr:uuid"] === rowData["jcr:uuid"]) ? theme.palette.grey["200"] : theme.palette.background.default,
                    // // grey out subjects that have already reached maxPerSubject
                    color: ((relatedForms && (selectedSubject || currentSubject) && (relatedForms.filter((i) => (i["@name"] == rowData["@name"])).length >= rowData?.["maxPerSubject"]))
                    ? theme.palette.grey["500"]
                    : theme.palette.grey["900"]
                    )
                  })
                }}
                onRowClick={(event, rowData) => {
                  if (!isFetching) {
                    setSelectedQuestionnaire(rowData);
                  }
                }}
                onChangeRowsPerPage={pageSize => {
                  setRowCount(pageSize);
                }}
              />
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
        onClose={() => {
          setNewSubjectPopperOpen(false);
          setError();
          if (onClose) {
            onClose();
          }
        }}
        onChangeSubject={(event) => {setNewSubjectName(event.target.value);}}
        currentSubject={currentSubject}
        onSubmit={createForm}
        open={newSubjectPopperOpen}
        />
      {
        mode === MODE_ACTION &&
          <div className={classes.newFormButtonWrapper}>
            <Tooltip title={children} aria-label="add">
              <Fab
                color="primary"
                aria-label="add"
                onClick={openDialog}
                disabled={!dialogOpen && isFetching}
              >
                <AddIcon />
              </Fab>
            </Tooltip>
            {!dialogOpen && isFetching && <CircularProgress size={56} className={classes.newFormLoadingIndicator} />}
          </div>
      }
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle, {withTheme: true})(withRouter(NewFormDialog));
