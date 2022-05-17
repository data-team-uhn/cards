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

import React, { useState, useContext } from "react";
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
} from "@material-ui/core";

import withStyles from '@material-ui/styles/withStyles';

import Fields from './Fields'
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

// Dialog for editing or creating questions or sections

let EditDialog = (props) => {
  const { data, type, targetExists, isOpen, onClose, onCancel, id } = props;
  let [ targetId, setTargetId ] = useState('');
  // Marks that a save operation is in progress
  let [ saveInProgress, setSaveInProgress ] = useState();
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ open, setOpen ] = useState(isOpen);
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
  let [ error, setError ] = useState('');
  let [ variableNameError, setVariableNameError ] = useState('');
  let json = require(`./${type}.json`);

  let saveButtonRef = React.useRef();
  const globalLoginDisplay = useContext(GlobalLoginContext);

  let saveData = (event) => {
    // This stops the normal browser form submission
    event.preventDefault();

    setSaveInProgress(true);
    // If the question/section already exists, update it
    if (targetExists) {
      // currentTarget is the element on which the event listener was placed and invoked, thus the <form> element
      let request_data = new FormData(event.currentTarget);
      fetchWithReLogin(globalLoginDisplay,
        `${data['@path']}`,
        {
          method: 'POST',
          body: request_data
        })
        .then((response) => {
          if (response.ok) {
            setSaveInProgress(false);
            setOpen(false);
            onClose && onClose();
          } else {
            handleError(response);
          }
        })
        .catch(handleError);

    } else {
      // If the entry doesn't exist, create it
      let newData = data;
      const primaryType = `cards:${type}`;
      var request_data = new FormData(event.currentTarget);
      request_data.append('jcr:primaryType', primaryType);
      fetchWithReLogin(globalLoginDisplay,
        `${data['@path']}/${targetId}`,
        {
          method: 'POST',
          body: request_data
        })
        .then((response) => {
          if (response.ok) {
            setLastSaveStatus(true);

            // Fetch and propagate back data with appended newly created item to highlight & focus on it
            fetch(`${data['@path']}/${targetId}.deep.json`)
              .then((response) => response.ok ? response.json() : Promise.reject(response))
              .then((json) => {
                let item = json;
                item.doHighlight = true;
                newData = data;
                newData[targetId] = item;
                setSaveInProgress(false);
                setOpen(false);
                onClose && onClose(newData);
              })
              .catch(handleError);
          } else {
            handleError(response);
          }
        })
        .catch(handleError);
    }
  }

  let handleError = (response) => {
    if (response.status === 500) {
      response.json().then((json) => {
        setError(json.error.message);
      })
      setLastSaveStatus(undefined);
    } else {
      setError(response.statusText ? response.statusText : response.toString());
      setLastSaveStatus(false);
    }
    setSaveInProgress(false);
  };

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
    return (targetExists ? 'Edit ' : 'New ').concat(type);
  }

  let targetIdField = () => {
    return (
      <Grid container alignItems='baseline' spacing={2} direction="row">
        <Grid item xs={4}><Typography variant="subtitle2">{type === 'Question' ? 'Variable name:' : `${type} identifier` }</Typography></Grid>
        <Grid item xs={8}>{
          targetExists ?
          <Typography>{data["@name"]}</Typography> :
          <TextField
            variant="standard"
            name=''
            value={targetId}
            onChange={(event)=> { setTargetId(event.target.value); setVariableNameError(''); }}
            onBlur={(event)=> { checkVariableName(event.target.value?.trim()); }}
            error={variableNameError}
            helperText={variableNameError}
            multiline
            fullWidth />
        }</Grid>
      </Grid>
    );
  }

  let checkVariableName = (newValue) => {
    // The path with this variable name exists
    if (newValue && Object.keys(data).includes(newValue) && data[newValue]["@path"]) {
      let mainType = data["sling:resourceType"].replaceAll(/^cards\//g, "");
      let type = (data[newValue].dataType ? data[newValue].dataType + " " : "") + data[newValue]["sling:resourceType"].replaceAll(/^cards\//g, "");
      let label = data[newValue].label || data[newValue].text || newValue;
      setVariableNameError(`The identifier ${newValue} is already in use in this ${mainType} for the ${type} '${label}'. Please choose a different identifier.`);
    }
  }

  return (
    <form action={data?.['@path']} method='POST' onSubmit={saveData} onChange={() => setLastSaveStatus(undefined) } key={id}>
       <Dialog disablePortal id='editDialog' open={open} onClose={() => { setOpen(false); onCancel && onCancel();} } fullWidth maxWidth='md'>
          <DialogTitle>
          { dialogTitle() }
          </DialogTitle>
          <DialogContent>
            { error && <Typography color="error">{error}</Typography>}
            <Grid container direction="column" spacing={2}>
              <Grid item>{targetIdField()}</Grid>
              <Fields
                data={targetExists && data || {}}
                JSON={json[0]}
                edit={true}
                path={data["@path"] + (targetExists ? "" : `/${targetId}`)}
                saveButtontRef={saveButtonRef}
               />
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button
              ref={saveButtonRef}
              type='submit'
              variant='contained'
              color='primary'
              disabled={saveInProgress || variableNameError}
            >
              {saveInProgress ? 'Saving' :
              lastSaveStatus === true ? 'Saved' :
              lastSaveStatus === false ? 'Save failed, log in and try again?' :
              'Save'}
            </Button>
            <Button
              variant='contained'
              color='default'
              onClick={() => { setOpen(false); onCancel && onCancel();}}
            >
              {'Cancel'}
            </Button>
          </DialogActions>
       </Dialog>
    </form>
  );
};

EditDialog.propTypes = {
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  targetExists: PropTypes.bool.isRequired,
  isOpen: PropTypes.bool.isRequired,
  onClose: PropTypes.func,
  onCancel: PropTypes.func
};

export default EditDialog;
