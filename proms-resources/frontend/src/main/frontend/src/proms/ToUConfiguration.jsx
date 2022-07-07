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
import React, { useEffect, useState } from 'react';
import {
    Alert,
    Button,
    Checkbox,
    CircularProgress,
    FormControlLabel,
    FormHelperText,
    List,
    ListItem,
    TextField,
    Typography
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import AdminScreen from "../adminDashboard/AdminScreen.jsx";
import MarkdownText from "../questionnaireEditor/MarkdownText";

const useStyles = makeStyles(theme => ({
  text: {
    display: "block",
  },
  saveButton: {
    marginTop: theme.spacing(3),
  },
}));

function ToUConfiguration() {
  const classes = useStyles();

  // Status tracking values of fetching/posting the data from/to the server
  const [ error, setError ] = useState();
  const [ acceptanceRequired, setAcceptanceRequired ] = useState(false);
  const [ title, setTitle ] = useState();
  const [ text, setText ] = useState();
  const [ version, setVersion ] = useState();
  const [ isSaved, setIsSaved ] = useState(false);

  // Fetch saved admin config settings
  let getToUConfig = () => {
    fetch('/Proms/TermsOfUse.json')
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setAcceptanceRequired(json.acceptanceRequired || false);
        setTitle(json.title || "");
        setVersion(json.version || "");
        setText(json.text || "");
      })
      .catch(setError);
  }

  // Submit function
  let handleSubmit = (event) => {

    // This stops the normal browser form submission
    event && event.preventDefault();

    // Build formData object.
    // We need to do this because sling does not accept JSON, need url encoded data
    let formData = new URLSearchParams();
    formData.append('acceptanceRequired', acceptanceRequired);
    formData.append('title', title);
    formData.append('version', version);
    formData.append('text', text);

    // Use native fetch, sort like the XMLHttpRequest so no need for other libraries.
    fetch('/Proms/TermsOfUse',
      {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: formData
      })
      .then((response) => {

        // Important note about native fetch, it does not reject failed
        // HTTP codes, it'll only fail when network error
        // Therefore, you must handle the error code yourself.
        if (!response.ok) {
          throw Error(response.statusText);
        }

        setIsSaved(true);
      })
      .catch(setError);
  }

  useEffect(() => {
    getToUConfig();
  }, []);

  return (
    <AdminScreen title="Patient Portal Terms of Use">
      { error && <Alert severity="error">{error}</Alert> }
      { typeof(text) == 'undefined' ? <CircularProgress /> :
        <form onSubmit={handleSubmit}>
          <List>
            <ListItem key="title">
              <TextField
                InputLabelProps={{ shrink: true }}
                variant="standard"
                fullWidth
                id="title"
                name="title"
                type="text"
                label="Title"
                value={title}
                onChange={(event) => { setIsSaved(false); setTitle(event.target.value); }}
              />
            </ListItem>
            <ListItem key="version">
              <TextField
                InputLabelProps={{ shrink: true }}
                variant="standard"
                id="version"
                name="version"
                type="version"
                label="Version"
                value={version}
                onChange={(event) => { setIsSaved(false); setVersion(event.target.value); }}
                style={{'width' : '250px'}}
              />
            </ListItem>
            <ListItem key="text" className={classes.text}>
              <FormHelperText>Text</FormHelperText>
              <MarkdownText
                value={text}
                height={350}
                onChange={value => { setIsSaved(false); setText(value); }}
              />
            </ListItem>
            <ListItem key="acceptanceRequired">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={acceptanceRequired}
                    onChange={(event) => { setIsSaved(false); setAcceptanceRequired(event.target.checked); }}
                    name="acceptanceRequired"
                  />
                }
                label="Patients must accept the Terms of Use before using the portal"
              />
            </ListItem>
            <ListItem key="button">
              <Button
                type="submit"
                variant="contained"
                color="primary"
                size="small"
                className={classes.saveButton}
              >
                { isSaved ? "Saved" : "Save" }
              </Button>
            </ListItem>
          </List>
        </form>
      }
    </AdminScreen>
  );
}

export default ToUConfiguration;