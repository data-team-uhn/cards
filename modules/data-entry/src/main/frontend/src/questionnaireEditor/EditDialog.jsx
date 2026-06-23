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
  Alert,
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
  const { data, type, targetExists, isOpen, onSaved, onCancel, id, spec, hints: hintsProp } = props;
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

  let json = [spec];
  let hints = hintsProp;
  if (!hints) {
    try {
      hints = require(`./${type}-hints.json`);
    } catch (e) {
      hints = null;
    }
  }


  let formattedType = camelCaseToWords(type);

  let saveButtonRef = useRef();
  const globalLoginDisplay = useContext(GlobalLoginContext);

  let saveData = (event) => {
    // This stops the normal browser form submission
    event.preventDefault();

    setSaveInProgress(true);
    setError('');
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

  // Render the Dialog's Paper as the <form> so DialogTitle/Content/Actions stay direct
  // children of the scroll container: only DialogContent scrolls while the title and
  // actions remain fixed. The form still wraps every field and the submit button
  // (onSubmit/FormData intact), and the dialog portals normally — avoiding both the
  // broken scrolling of a form-inside-Dialog and the aria-hidden-on-focused-ancestor
  // warning that the form-wrapping-Dialog + disablePortal structure caused.
  return (
    <Dialog
      key={id}
      id='editDialog'
      open={open}
      onClose={() => { setOpen(false); onCancel?.();} }
      fullWidth
      maxWidth='md'
      slotProps={{
        paper: {
          component: 'form',
          action: data?.['@path'],
          method: 'POST',
          onSubmit: saveData,
          onChange: () => { setLastSaveStatus(undefined); setError(''); },
        }
      }}
    >
      <DialogTitle>
        { dialogTitle() }
        { error &&
          <Alert severity="error" sx={{ mb: 2 }}>
            Error saving form: {error}
          </Alert>
        }
      </DialogTitle>
      <DialogContent>
        <Grid container direction="column" spacing={2}>
          <Grid>{targetIdField()}</Grid>
          <Fields
            data={dialogData}
            hints={hints}
            JSON={json[0]}
            edit={true}
            path={data["@path"] + (targetExists ? "" : `/${targetId}`)}
            saveButtonRef={saveButtonRef}
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
