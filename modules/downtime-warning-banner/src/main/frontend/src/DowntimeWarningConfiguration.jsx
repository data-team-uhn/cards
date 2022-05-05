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
import {
    Button,
    Checkbox,
    TextField,
    Tooltip,
    Typography,
    FormControlLabel,
    Card,
    CardContent,
    CardHeader,
    List,
    ListItem,
    makeStyles
} from '@material-ui/core';

import QuestionnaireStyle from "./questionnaire/QuestionnaireStyle";

const useStyles = makeStyles(theme => ({
  textField: {
    minWidth: "250px",
    paddingBottom: theme.spacing(2),
  },
  saveButton: {
    marginTop: theme.spacing(3),
  },
}));

function DowntimeWarningConfiguration() {
  const classes = useStyles();

  const [ path, setPath ] = useState();

  // The the configuration values specified by the Administration
  const [ enabled, setEnabled ] = useState(false);
  const [ fromDate, setFromDate ] = useState();
  const [ toDate, setToDate ] = useState();
  const [ isInvalidDateRange, setIsInvalidDateRange ] = useState(false);

  // Status tracking values of fetching/posting the data from/to the server
  const [ error, setError ] = useState();
  const [ onSuccess, setOnSuccess ] = useState(false);
  const [ fetched, setFetched ] = useState(false);

  const dateFormat = "yyyy-MM-dd hh:mm";

  // Fetch saved admin config settings
  let getDowntimeWarningSettings = () => {
    fetch('/apps/cards/config/DowntimeWarning.deep.json')
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setFetched(true);
        setPath(json["@path"]);
        setEnabled(json.enabled == 'true');
        setFromDate(json.fromDate);
        setToDate(json.toDate);
      });
  }

  // Submit function
  let handleSubmit = (event) => {

    // This stops the normal browser form submission
    event && event.preventDefault();

    // Build formData object.
    // We need to do this because sling does not accept JSON, need url encoded data
    let formData = new URLSearchParams();
    formData.append('enabled', enabled);
    formData.append('fromDate', fromDate);
    formData.append('toDate', toDate);

    // Use native fetch, sort like the XMLHttpRequest so no need for other libraries.
    fetch(path,
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

        setOnSuccess(true);
      })
      .catch(error => {
        console.log(error);
      });
  }

  let updateFromDate  = (event) => {
    event.preventDefault();
    setOnSuccess(false);
    setFromDate(event.target.value);
  }

  let updateToDate  = (event) => {
    event.preventDefault();
    setOnSuccess(false);
    setToDate(event.target.value);
    // Determine the end date is earlier than the start date
    setIsInvalidDateRange(fromDate && event.target.value && new Date(event.target.value).valueOf() < new Date(fromDate).valueOf());
  }

  if (!fetched) {
    getDowntimeWarningSettings();
  }

  return (
    <Card>
      <CardHeader
        title="Downtime warning banner settings"
        titleTypographyProps={{variant: "h6"}}
      />
      <CardContent>
        {error && <Typography color='error'>{errorText}</Typography>}
        <form onSubmit={handleSubmit}>
          <List>
            <ListItem key="enabled">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={enabled}
                    onChange={(event) => { event.preventDefault(); setOnSuccess(false); setEnabled(event.target.checked); }}
                    name="enabled"
                  />
                }
                label="Show downtime warning banner"
              />
            </ListItem>
            <ListItem key="fromDate">
              <TextField
                label="Start of maintenance"
                type="datetime-local"
                InputLabelProps={{ shrink: true }}
                className={classes.textField}
                onChange={(event) => { updateFromDate(event) }}
                onBlur={(event) => { updateFromDate(event) }}
                placeholder={dateFormat.toLowerCase()}
                value={fromDate || ""}
              />
            </ListItem>
            <ListItem key="toDate">
              <TextField
                label="End of maintenance"
                type="datetime-local"
                InputLabelProps={{ shrink: true }}
                className={classes.textField}
                onChange={(event) => { updateToDate(event) }}
                onBlur={(event) => { updateToDate(event) }}
                placeholder={dateFormat.toLowerCase()}
                value={toDate || ""}
                error={isInvalidDateRange}
                helperText={isInvalidDateRange ? "End date should be after the start date." : ""}
              />
            </ListItem>
            <ListItem key="button">
              <Button
                type="submit"
                disabled={isInvalidDateRange}
                variant="contained"
                color="primary"
                size="small"
                className={classes.saveButton}
              >
                { !onSuccess ? "Save" : "Saved" }
              </Button>
            </ListItem>
          </List>
        </form>
      </CardContent>
    </Card>
  );
}

export default DowntimeWarningConfiguration;
