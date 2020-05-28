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
  Grid,
  IconButton,
  MenuItem,
  Select,
  TextField,
  withStyles
} from "@material-ui/core";

import moment from "moment";
import CloseIcon from '@material-ui/icons/Close';
import EditIcon from "@material-ui/icons/Edit";
import DeleteIcon from "@material-ui/icons/Delete";
import QuestionnaireStyle from "./QuestionnaireStyle";

let EditQuestionnaireDialog = (props) => {
  let [openEditDialog, setOpenEditDialog] = useState(false);
  let [openDeleteDialog, setOpenDeleteDialog] = useState(false);
  // FIXME Pull these from a source of truth instead
  let [ dataTypes, setDataTypes ] = useState(["long", "double", "decimal", "boolean", "date", "text", "vocabulary"]);
  let [ forms, setForms] = useState(0);

  let [ label, setLabel ] = useState(props.data.text);
  let [ description, setDescription ] = useState(props.data.description);
  let [ dataType, setDataType ] = useState(props.data.dataType);
  let [ minAnswers, setMinAnswers ] = useState(props.data.minAnswers || 0);
  let [ maxAnswers, setMaxAnswers ] = useState(props.data.maxAnswers || 0);
  let [ answerChoices, setAnswerChoices ] = useState(Object.values(props.data)
    .filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption')
    .map(value => value.value)
    .slice());
  let newAnswerChoice = useRef("");

  // Marks that a save operation is in progress
  let [ saveInProgress, setSaveInProgress ] = useState();
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
  
  let saveData = (event) => {
    // This stops the normal browser form submission
    event.preventDefault();

    // If the previous save attempt failed, instead of trying to save again, open a login popup
    if (lastSaveStatus === false) {
      loginToSave();
      return;
    }

    setSaveInProgress(true);
    // currentTarget is the element on which the event listener was placed and invoked, thus the <form> element
    let request_data = new FormData(event.currentTarget);
    request_data.set('text', label);
    request_data.set('description', description);
    request_data.set('dataType', dataType);
    request_data.set('minAnswers', minAnswers);
    request_data.set('maxAnswers', maxAnswers);
    fetch(`/Questionnaires/${props.id}`, {
      method: "POST",
      body: request_data
    }).then((response) => response.ok ? true : Promise.reject(response))
      .then(() => setLastSaveStatus(true))
      // FIXME Use setError?
      .catch(() => {
        // If the user is not logged in, offer to log in
        const sessionInfo = window.Sling.getSessionInfo();
        if (sessionInfo === null || sessionInfo.userID === 'anonymous') {
          // On first attempt to save while logged out, set status to false to make button text inform user
          setLastSaveStatus(false);
        }
      })
      .finally(() => setSaveInProgress(false));

    setOpenEditDialog(false);
  }

  // Open the login page in a new popup window, centered wrt the parent window
  let loginToSave = () => {
    const width = 600;
    const height = 800;
    const top = window.top.outerHeight / 2 + window.top.screenY - ( height / 2);
    const left = window.top.outerWidth / 2 + window.top.screenX - ( width / 2);
    // After a successful log in, the login dialog code will "open" the specified resource, which results in executing the specified javascript code
    window.open("/login.html?resource=javascript%3Awindow.close()", "loginPopup", `width=${width}, height=${height}, top=${top}, left=${left}`);
    // Display 'save' on button
    setLastSaveStatus(undefined);
  }

  let deleteAnswerChoice = (answerChoice) => {
    let updatedAnswerChoices = answerChoices.slice();
    updatedAnswerChoices.splice(answerChoices.indexOf(answerChoice), 1);
    setAnswerChoices(updatedAnswerChoices);
  }

  let updateAnswerChoice = (value, answerChoice) => {
    let updatedAnswerChoices = answerChoices.slice();
    updatedAnswerChoices[answerChoices.indexOf(answerChoice)] = value;
    setAnswerChoices(updatedAnswerChoices); 
  }

  let addNewAnswerChoice = () => {
    let updatedAnswerChoices = answerChoices.slice();
    updatedAnswerChoices.push(newAnswerChoice.current.value);
    setAnswerChoices(updatedAnswerChoices);
  }

  let answerChoicesFields = () => {
    return answerChoices.map(value => {
      return (
        <div key={answerChoices.indexOf(value)}>
          <TextField
            value={value}
            onChange={(event) => { 
              updateAnswerChoice(event.target.value, value);
            }}
          />
          <IconButton
            size="small"
            onClick={()=> {
              deleteAnswerChoice(value);
            }}
          >
            <CloseIcon />
          </IconButton>
        </div>
      );
    });
  }

  let dataTypeSelectOptions = () => {
    return dataTypes.map(dataType => {
      return (
        <MenuItem key={dataTypes.lastIndexOf(dataType)} value={dataType} primaryText={dataType}>
          {dataType}
        </MenuItem>
      );
    });
  }

  let deleteQuestionnaireWarningMessage = () => {
    const formsExist = forms && forms > 0;
    return formsExist
      ? `There are ${forms} forms filled out for this questionnaire that will also be deleted. Are you sure you wish to proceed ?`
      : "Are you sure you wish to proceed ?"
  }

  return (
    <React.Fragment>
      <Dialog id="editDialog" open={openEditDialog} onClose={() => { setOpenEditDialog(false); }}>
        <DialogTitle>
          { props.edit ? "Edit Question" : "New Question" }
        </DialogTitle>
        <form action={props.data["@path"]} method="POST" onSubmit={saveData} onChange={() => setLastSaveStatus(undefined) } key={props.id}>
          <DialogContent>
           <Grid container alignItems="flex-end" spacing={2}>
              <Grid item xs={6}>
                Label:
              </Grid>
              <Grid item xs={6}>
                <TextField
                  id="label"
                  defaultValue={label}
                  onChange={(event) => { setLabel(event.target.value); }}
                />
              </Grid>
              <Grid item xs={6}>
                Description:
              </Grid>
              <Grid item xs={6}>
                <TextField
                  id="description"
                  defaultValue={description}
                  onChange={(event) => { setDescription(event.target.value); }}
                />
              </Grid>
              <Grid item xs={6}>
                Answer type:
              </Grid>
              <Grid item xs={6}>
                <Select 
                  id="answerType"
                  defaultValue={dataType}
                  onChange={(event) => { setDataType(event.target.value); }}
                >
                  { dataTypeSelectOptions() }
                </Select>
              </Grid>
              <Grid item xs={6}>
                Minimum number of selected options:
              </Grid>
              <Grid item xs={6}>
                <TextField
                  defaultValue={minAnswers}
                  onChange={(event) => { setMinAnswers(event.target.value); }}
                />
              </Grid>
              <Grid item xs={6}>
                Maximum number of selected options:
              </Grid>
              <Grid item xs={6}>
                <TextField
                  defaultValue={maxAnswers} 
                  onChange={(event) => { setMaxAnswers(event.target.value); }}
                />
              </Grid>
              <Grid item xs={6}>
                Answer choices:
              </Grid>
              <Grid item>
                { answerChoicesFields() }
                  <TextField
                    inputRef={newAnswerChoice}
                    //onChange={(event) => { this.ListeningStateChangedEvent(event.target.value); }}
                    helperText="Press ENTER to add a new line"
                    inputProps={Object.assign({
                      onKeyDown: (event) => {
                        if (event.key == 'Enter') {
                          // We need to stop the event so that it doesn't trigger a form submission
                          event.preventDefault();
                          event.stopPropagation();
                          addNewAnswerChoice();
                          newAnswerChoice.current.value = "";
                        }
                      }
                    })}
                  />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button
              type="submit"
              variant="contained"
              color="primary"
              disabled={saveInProgress}
            >
              {saveInProgress ? 'Saving' :
              lastSaveStatus === true ? 'Saved' :
              lastSaveStatus === false ? 'Save failed, log in and try again?' :
              'Save'}
            </Button>
            <Button
              variant="contained"
              color="default"
              onClick={() => { setOpenEditDialog(false); }}
              >
              {'Cancel'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
      <Dialog id="deleteDialog" open={openDeleteDialog} onClose={() => { setOpenDeleteDialog(false); }}>
        <form action={props.data["@path"]} method="DELETE" key={props.id}>
          <DialogTitle>
            Confirm questionnaire deletion
          </DialogTitle>
          <DialogContent>
            { deleteQuestionnaireWarningMessage() }
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
      <IconButton onClick={ () => { setOpenEditDialog(true); }}>
        <EditIcon />
      </IconButton>
      <IconButton onClick={ () => {  setOpenDeleteDialog(true); }}>
        <DeleteIcon />
      </IconButton>
    </React.Fragment>
  );
};

EditQuestionnaireDialog.propTypes = {
  data: PropTypes.object.isRequired,
  id: PropTypes.string.isRequired
};

export default withStyles(QuestionnaireStyle)(EditQuestionnaireDialog);
