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
import React, { useState, useEffect } from 'react';
import {
    Checkbox,
    TextField,
    Typography,
    FormControlLabel,
    List,
    ListItem,
} from '@mui/material';

import AdminConfigScreen from "../adminDashboard/AdminConfigScreen.jsx";

function QuickSearchConfiguration(props) {

  const [ limit, setLimit ] = useState(5);
  const [ allowedResourceTypes, setAllowedResourceTypes ] = useState([]);
  const [ showTotalRows, setShowTotalRows ] = useState(true);
  const [ hasChanges, setHasChanges ] = useState(true);

  const resourceTypes = ["cards:Form", "cards:Subject", "cards:Questionnaire"];

  // Read the settings from the saved configuration
  let readQuickSearchSettings = (json) => {
    setLimit(json["limit"]);
    setAllowedResourceTypes(json["allowedResourceTypes"]);
    setShowTotalRows(json["showTotalRows"]  == 'true');
  }

  let buildConfigData = (formData) => {
    formData.append('limit', limit);
    formData.append('showTotalRows', showTotalRows);
    for (var i in allowedResourceTypes) {
      formData.append('allowedResourceTypes', allowedResourceTypes[i]);
    }
  }

  let onSourceTypeChange  = (checked, resourceName) => {
    setHasChanges(true);
    let types = allowedResourceTypes.slice();
    if (checked) {
      types.push(resourceName);
    } else {
      types.splice(types.indexOf(resourceName), 1);
    }
    setAllowedResourceTypes(types);
  }

  return (
    <AdminConfigScreen
        title="Quick Search Settings"
        configPath="/apps/cards/config/QuickSearch"
        configTemplate={{limit: "", showTotalRows: false, allowedResourceTypes: [""]}}
        onConfigFetched={readQuickSearchSettings}
        hasChanges={hasChanges}
        buildConfigData={buildConfigData}
        onConfigSaved={() => setHasChanges(false)}
      >
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
                onChange={ event => { setLimit(event.target.value); setHasChanges(true); } }
                style={{'width' : '250px'}}
                helperText="How many results should be displayed"
              />
            </ListItem>
            <ListItem key="showTotalRows">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={showTotalRows}
                    onChange={ event => { setShowTotalRows(event.target.checked); setHasChanges(true); } }
                    name="showTotalRows"
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
                        />
                      }
                      label={resourceName.replace('cards:', '') + 's'}
                    />
                  </ListItem>
                )
              })
            }
          </List>
    </AdminConfigScreen>
  );
}

export default QuickSearchConfiguration;
