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

import { useState, useContext, useRef } from "react";

import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  TextField,
  Typography,
} from "@mui/material";
import PropTypes from 'prop-types';

import { checkPropTypes } from "../propTypes";
import Fields from './Fields';
import { camelCaseToWords } from './LabeledField';
import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";

// Dialog for editing or creating questions or sections

let EditDialog = (props) => {
  checkPropTypes(EditDialog, props);
  const { data, type, targetExists, isOpen, onSaved, onCancel, id, model } = props;
  let [ targetId, setTargetId ] = useState('');
  const dialogData = targetExists ? data : {};
  // Marks that a save operation is in progress
  let [ saveInProgress, setSaveInProgress ] = useState();
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ open, setOpen ] = useState(isOpen);
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
  const primaryType = `cards:${type}`;
  let [ error, setError ] = useState('');
  let [ variableNameError, setVariableNameError ] = useState('');

  // Dynamic require - webpack warning is expected for this pattern
  // Critical dependency: the request of a dependency is an expression
  let json = model ? require(`./${model}`) : require(`./${type}.json`);
  let hints = null;
  try {
    hints = require(`./${type}-hints.json`);
  } catch (e) {
    // do nothing
  }


  let formattedType = camelCaseToWords(type);

  let saveButtonRef = useRef();
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
            onSaved?.();
          } else {
            handleError(response);
          }
        })
        .catch(handleError);

    } else {
      // If the entry doesn't exist, create it
      let request_data = new FormData(event.currentTarget);
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
            fetchWithReLogin(globalLoginDisplay, `${data['@path']}.deep.json`)
              .then((response) => response.ok ? response.json() : Promise.reject(response))
              .then((json) => {
                let newData = json;
                newData[targetId].doHighlight = true;
                setSaveInProgress(false);
                setOpen(false);
                onSaved?.(newData);
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
      <Grid container justifyContent='center'>
        <Grid>
          <Typography variant='h2' color='error'>
            Error obtaining form data: {error.status} {error.statusText}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let dialogTitle = () => {
    return (targetExists ? 'Edit ' : 'New ').concat(formattedType.toLowerCase());
  }

  let targetIdField = () => {
    return (
      <Grid container alignItems='baseline' spacing={2}>
        <Grid size={4}><Typography variant="subtitle2">{`${formattedType} id:` }</Typography></Grid>
        <Grid size={8}>{
          targetExists ?
            <Typography>{data["@name"]}</Typography> :
            <TextField
              variant="standard"
              name=''
              value={targetId}
              onChange={(event)=> { setTargetId(event.target.value); setVariableNameError(''); }}
              onBlur={(event)=> { checkVariableName(event.target.value?.trim()); }}
              error={!!variableNameError}
              helperText={variableNameError}
              required
              multiline
              fullWidth
            />
        }</Grid>
      </Grid>
    )
  }

  let checkVariableName = (newValue) => {
    // The path with this variable name exists
    if (newValue && Object.keys(data).includes(newValue) && data[newValue]["@path"]) {
      let mainType = data["sling:resourceType"].replaceAll(/^cards\//g, "");
      let label = data[newValue].label || data[newValue].text || newValue;
      let err = `The identifier ${newValue} is already in use in this ${mainType} for the ${formattedType} '${label}'`;
      err += ". Please choose a different identifier.";
      setVariableNameError(err);
    }
  }

  return (
    <form
      action={data?.['@path']}
      method='POST'
      onSubmit={saveData}
      onChange={() => setLastSaveStatus(undefined) }
      key={id}
    >
      <Dialog
        disablePortal
        id='editDialog'
        open={open}
        onClose={() => { setOpen(false); onCancel?.();} }
        fullWidth
        maxWidth='md'
      >
        <DialogTitle>
          { dialogTitle() }
        </DialogTitle>
        <DialogContent>
          { error && <Typography color="error">{error}</Typography>}
          <Grid container direction="column" spacing={2}>
            <Grid>{targetIdField()}</Grid>
            <Fields
              data={dialogData}
              hints={hints}
              JSON={json[0]}
              edit={true}
              path={data["@path"] + (targetExists ? "" : `/${targetId}`)}
              saveButtontRef={saveButtonRef}
            />
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button
            variant='outlined'
            onClick={() => { setOpen(false); onCancel?.();}}
          >
            Cancel
          </Button>
          <Button
            ref={saveButtonRef}
            type='submit'
            variant='contained'
            disabled={saveInProgress || !!variableNameError}
          >
            {saveInProgress ? 'Saving' :
              lastSaveStatus === true ? 'Saved' :
                lastSaveStatus === false ? 'Save failed, log in and try again?' :
                  'Save'}
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
  onSaved: PropTypes.func,
  onCancel: PropTypes.func
};

export default EditDialog;
