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
} from '@mui/material';

function QuickSearchConfiguration(props) {
  const { match, location, classes } = props;

  const [ path, setPath ] = useState();
  const [ limit, setLimit ] = useState(5);
  const [ allowedResourceTypes, setAllowedResourceTypes ] = useState([]);
  const [ showTotalRows, setShowTotalRows ] = useState(true);
  const [ onSuccess, setOnSuccess ] = useState(false);
  const [ fetched, setFetched ] = useState(false);

  const resourceTypes = ["cards:Form", "cards:Subject", "cards:Questionnaire"];

  // Fetch saved admin config settings
  let getQuickSearchSettings = () => {
    fetch('/apps/cards/config/QuickSearch.deep.json')
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setFetched(true);
        setPath(json["@path"]);
        setLimit(json["limit"]);
        setAllowedResourceTypes(json["allowedResourceTypes"]);
        setShowTotalRows(json["showTotalRows"]  == 'true');
      });
  }

  // submit function
  let handleSubmit = (event) => {

    // This stops the normal browser form submission
    event && event.preventDefault();

    // Build formData object.
    // We need to do this because sling does not accept JSON, need url encoded data
    let formData = new URLSearchParams();
    formData.append('limit', limit);
    formData.append('showTotalRows', showTotalRows);
    for (var i in allowedResourceTypes) {
      formData.append('allowedResourceTypes', allowedResourceTypes[i]);
    }

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

  let onSourceTypeChange  = (checked, resourceName) => {
    event.preventDefault();
    setOnSuccess(false);
    let types = allowedResourceTypes.slice();
    if (checked) {
      types.push(resourceName);
    } else {
      types.splice(types.indexOf(resourceName), 1);
    }
    setAllowedResourceTypes(types);
  }

  if (!fetched) {
    getQuickSearchSettings();
  }

  return (
    <Card>
      <CardHeader
        title={
          <Typography variant="h6" gutterBottom>Quick search settings</Typography>
        }
      />
      <CardContent>
        <form onSubmit={handleSubmit}>
          <List>
            <ListItem key="h1">
              <Typography variant="h6">Search results controls:</Typography>
            </ListItem>
            <ListItem key="limit">
              <TextField
                variant="standard"
                id="limit"
                name="limit"
                type="number"
                label="Limit"
                value={limit}
                onChange={(event) => { setOnSuccess(false); setLimit(event.target.value); }}
                style={{'width' : '10%'}} />
              <Typography variant="body1">How many results should be displayed</Typography>
            </ListItem>
            <ListItem key="showTotalRows">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={showTotalRows}
                    onChange={(event) => { event.preventDefault(); setOnSuccess(false); setShowTotalRows(event.target.checked); }}
                    name="showTotalRows"
                    color="primary"
                  />
                }
                label="Show the total number of results"
              />
            </ListItem>
            <ListItem key="h2">
              <Typography variant="h6">Types of resources allowed to be be queried:</Typography>
            </ListItem>
            {
              resourceTypes.map((resourceName) => {
                return (
                  <ListItem key={resourceName}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={allowedResourceTypes.indexOf(resourceName) > -1}
                          onChange={(event) => { onSourceTypeChange(event.target.checked, resourceName); }}
                          name={resourceName}
                          color="primary"
                        />
                      }
                      label={resourceName.replace('cards:', '') + 's'}
                    />
                  </ListItem>
                )
              })
            }
            <ListItem key="button">
            { !onSuccess ?
              <Button type="submit" variant="contained" color="primary" size="small">Save</Button>
              :
              <Button type="submit" variant="contained" color="primary" size="small">Saved</Button>
            }
            </ListItem>
          </List>
        </form>
      </CardContent>
    </Card>
  );
}

export default QuickSearchConfiguration;
