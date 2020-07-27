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

import React, { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  TextField,
  Typography,
  withStyles
} from "@material-ui/core";

import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import AnswerOptions from './AnswerOptions';
import Fields from './Fields'

// Dialog for editing or creating questions and sections

let EditDialog = (props) => {
  let questionJSON = require('./Question.json');
  let sectionJSON = require('./Section.json');
  let [ title, setTitle ] = useState('');
  let [ json ] = useState(props.type.includes('Question') ? questionJSON : sectionJSON);
  // Marks that a save operation is in progress
  let [ saveInProgress, setSaveInProgress ] = useState();
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
  let [error, setError ] = useState('');

  let saveData = (event) => {
    // This stops the normal browser form submission
    event.preventDefault();

    // If the previous save attempt failed, instead of trying to save again, open a login popup
    if (lastSaveStatus === false) {
      loginToSave();
      return;
    }

    setSaveInProgress(true);
    // If the question/section already exists, update it
    if (props.edit) {
      // currentTarget is the element on which the event listener was placed and invoked, thus the <form> element
      let request_data = new FormData(event.currentTarget);
      fetch(`${props.data['@path']}`, {
        method: 'POST',
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
        .finally(() => {setSaveInProgress(false); props.onClose();});
    } else {
      // If the question/section doesn't exist, create it
      const URL = `${props.data['@path']}/${title}`
      const primaryType = props.type.includes('Question') ? 'lfs:Question' : 'lfs:Section'
      var request_data = new FormData(event.currentTarget);
      request_data.append('jcr:primaryType', primaryType);
      fetch(URL, { method: 'POST', body: request_data })
        .then((response) => response.ok ? true : Promise.reject(response))
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
        .finally(() => {setSaveInProgress(false); props.onClose()});}
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

  // If an error was returned, do not display a form at all, but report the error
  if (error) {
    return (
      <Grid container justify='center'>
        <Grid item>
          <Typography variant='h2' color='error'>
            Error obtaining form data: {error.status} {error.statusText}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let dialogTitle = () => {
    return (props.edit ? 'Edit ' : 'New ').concat(props.type);
  }

  let titleField = () => {
    return (
      <Grid container alignItems='flex-end' spacing={2}>
        <Grid item xs={6}><Typography>{props.type === 'Question' ? 'Title' : 'Name' }</Typography></Grid>
        <Grid item xs={6}><TextField name='title' value={title} onChange={(event)=> { setTitle(event.target.value); }} /></Grid>
      </Grid>
    )
  }

  return (
    <React.Fragment>
      <Dialog id='editDialog' open={props.open} onClose={props.onClose}>
        <DialogTitle>
          { dialogTitle() }
        </DialogTitle>
        <form action={props.data && props.data['@path']} method='POST' onSubmit={saveData} onChange={() => setLastSaveStatus(undefined) } key={props.id}>
          <DialogContent>
            { !props.edit && titleField() }
            <Fields data={props.edit && props.data || {}} JSON={json[0]} edit={true} />
            { props.data && props.type.includes('Question') && <AnswerOptions data={props.data} /> }
          </DialogContent>
          <DialogActions>
            <Button
              type='submit'
              variant='contained'
              color='primary'
              disabled={saveInProgress}
            >
              {saveInProgress ? 'Saving' :
              lastSaveStatus === true ? 'Saved' :
              lastSaveStatus === false ? 'Save failed, log in and try again?' :
              'Save'}
            </Button>
            <Button
              variant='contained'
              color='default'
              onClick={props.onClose}
            >
              {'Cancel'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </React.Fragment>
  );
};

EditDialog.propTypes = {
  data: PropTypes.object,
  type: PropTypes.string.isRequired,
  edit: PropTypes.bool.isRequired,
  open: PropTypes.bool.isRequired
};

export default withStyles(QuestionnaireStyle)(EditDialog);
