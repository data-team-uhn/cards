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
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  MenuItem,
  Select,
  TextField,
  Typography,
  withStyles
} from "@material-ui/core";

import EditIcon from "@material-ui/icons/Edit";
import QuestionnaireStyle from "./QuestionnaireStyle";
import uuid from "uuid/v4";
import ObjectInput from "./ObjectInput";

export function fields(data, JSON) {
  let formatString = (key) => {
    let formattedString = key.charAt(0).toUpperCase() + key.slice(1);
      return formattedString.split(/(?=[A-Z])/).join(" ");
    }

    let inputTypes = (key, value) => {
    return [
      {
        dataType: "boolean",
        score: ( value === "boolean" ? 60 : 10),
        component: (
          <Grid item xs={6}><Checkbox name={key} id={key} defaultValue={data[key]} /></Grid>
        )
      },
      {
        dataType: "string",
        score: ( value === "string" ? 60 : 40),
        component: (
          <Grid item xs={6}><TextField name={key} id={key} defaultValue={data[key]} /></Grid>
        )
      },
      {
        dataType: "long",
        score: ( value === "long" ? 60 : 10),
        component: (
          <Grid item xs={6}><TextField name={key} id={key} defaultValue={data[key]} /></Grid>
        )
      },
      {
        dataType: "object",
        score: ( typeof(value) === "object" ? 60 : 0),
        component: (<ObjectInput key={key} value={value} data={data}></ObjectInput>
        )
      }
    ]
  }
    
  let fieldInput = (key, value) => {
    return inputTypes(key, value).reduce((a,b) => a.score > b.score ? a : b).component;
  }
  
  return Object.entries(JSON).map(([key, value]) => (
    <Grid container alignItems="flex-end" spacing={2}>
      <Grid item xs={6}>
        <Typography>{ formatString(key) }</Typography>
      </Grid>
        { fieldInput(key, value) }  
    </Grid>
  ));
}

let EditDialog = (props) => {
  let [ questionJSON, setQuestionJSON ] = useState( require('./Question.json'));
  let [ sectionJSON, setSectionJSON ] = useState( require('./Section.json'));
  let [ json, setJson ] = useState(props.type.includes("Question") ? questionJSON : sectionJSON);
  let [ openEditDialog, setOpenEditDialog ] = useState(false);
  // Marks that a save operation is in progress
  let [ saveInProgress, setSaveInProgress ] = useState();
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
  let [error, setError ] = useState("");
  let saveData = (event) => {
    // This stops the normal browser form submission
    event.preventDefault();

    // If the previous save attempt failed, instead of trying to save again, open a login popup
    if (lastSaveStatus === false) {
      loginToSave();
      return;
    }

    setSaveInProgress(true);

    if (props.data) {
      // currentTarget is the element on which the event listener was placed and invoked, thus the <form> element
      let request_data = new FormData(event.currentTarget);
      fetch(`${props.data["@path"]}`, {
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
    } else {
      const URL = props.presetPath + uuid();
      const primaryType = props.type.includes('Question') ? 'lfs:Question' : 'lfs:Section'
      var request_data = new FormData(event.currentTarget);
      request_data.append('jcr:primaryType', primaryType);
      fetch( URL, { method: 'POST', body: request_data })
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
        .finally(() => setSaveInProgress(false));
          setOpenEditDialog(false);
      }
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
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining form data: {error.status} {error.statusText}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let dialogTitle = () => {
    return (props.edit ? "Edit " : "New ").concat(props.type);
  }

  return (
    <React.Fragment>
      <Dialog id="editDialog" open={openEditDialog} onClose={() => { setOpenEditDialog(false); }}>
        <DialogTitle>
          { dialogTitle() }
        </DialogTitle>
        <form action={props.data["@path"]} method="POST" onSubmit={saveData} onChange={() => setLastSaveStatus(undefined) } key={props.id}>
          <DialogContent>
            { fields(props.data, json[0])}
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
              onClick={ () => { setOpenEditDialog(false); }}
              >
              {'Cancel'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
      <IconButton onClick={ () => { setOpenEditDialog(true); }}>
        <EditIcon />
      </IconButton>
    </React.Fragment>
  );
};

EditDialog.propTypes = {
  type: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired,
  edit: PropTypes.bool.isRequired
};

export default withStyles(QuestionnaireStyle)(EditDialog);




