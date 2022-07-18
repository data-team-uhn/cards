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
    Checkbox,
    FormControlLabel,
    List,
    ListItem,
    TextField,
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import AdminConfigScreen from "../adminDashboard/AdminConfigScreen.jsx";
import { camelCaseToWords } from "../questionnaireEditor/LabeledField.jsx";

function DashboardSettingsConfiguration() {

  const [ enableTimeTabs, setEnableTimeTabs ] = useState(true);
  const [ hasChanges, setHasChanges ] = useState(false);
  const [ eventsLabel, setEventsLabel ] = useState("");
  const [ eventTimeLabel, setEventTimeLabel ] = useState("");

  const props = [{
    label : "eventsLabel",
    setter: setEventsLabel,
    value: eventsLabel
  },
  {
    label : "eventTimeLabel",
    setter: setEventTimeLabel,
    value: eventTimeLabel
  }];

  useEffect(() => {
    setHasChanges(true);
  }, [enableTimeTabs, eventsLabel, eventTimeLabel]);

  let buildConfigData = (formData) => {
    formData.append('enableTimeTabs', enableTimeTabs);
    formData.append('eventsLabel', eventsLabel);
    formData.append('eventTimeLabel', eventTimeLabel);
  }

  // Read the settings from the saved configuration
  let readDashboardSettings = (json) => {
    setEventsLabel(json.eventsLabel);
    setEventTimeLabel(json.eventTimeLabel);
    setEnableTimeTabs(json.enableTimeTabs);
  }

  return (
      <AdminConfigScreen
        title="Staff dashboard"
        configPath={"/Proms/DashboardSettings"}
        onConfigFetched={readDashboardSettings}
        hasChanges={hasChanges}
        buildConfigData={buildConfigData}
        onConfigSaved={() => setHasChanges(false)}
        >
          <List>
            { props.map(prop => { return (
            <ListItem key={prop.label}>
              <TextField
                InputLabelProps={{ shrink: true }}
                variant="standard"
                fullWidth
                id={prop.label}
                name={prop.label}
                type="text"
                label={camelCaseToWords(prop.label)}
                value={prop.value}
                onChange={(event) => { prop.setter(event.target.value); }}
              />
            </ListItem>)
            })}
            <ListItem key="enableTimeTabs">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={enableTimeTabs}
                    onChange={ event => setEnableTimeTabs(event.target.checked) }
                    name="enableTimeTabs"
                  />
                }
                label="Enable time tabs"
              />
            </ListItem>
        </List>
      </AdminConfigScreen>
  );
}

export default DashboardSettingsConfiguration;
