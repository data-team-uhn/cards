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
    FormControlLabel,
    List,
    ListItem
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import AdminScreen from "../adminDashboard/AdminScreen.jsx";

const useStyles = makeStyles(theme => ({
  saveButton: {
    marginTop: theme.spacing(3),
  },
}));

function ToUConfiguration() {
  const classes = useStyles();

  // Status tracking values of fetching/posting the data from/to the server
  const [ error, setError ] = useState();
  const [ enabled, setEnabled ] = useState(false);
  const [ isSaved, setIsSaved ] = useState(false);

  // Fetch saved admin config settings
  let getToUConfig = () => {
    fetch('/Proms/TermsOfUse.json')
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setEnabled(json.enabled);
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
    formData.append('enabled', enabled);

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
        {error && <Alert severity="error">{error}</Alert>}
        <form onSubmit={handleSubmit}>
          <List>
            <ListItem key="enabled">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={enabled}
                    onChange={(event) => { event.preventDefault(); setIsSaved(false); setEnabled(event.target.checked); }}
                    name="enabled"
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
      </AdminScreen>
  );
}

export default ToUConfiguration;
