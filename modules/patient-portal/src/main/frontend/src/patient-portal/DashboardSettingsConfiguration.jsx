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
import AdminConfigScreen from "../adminDashboard/AdminConfigScreen.jsx";
import { camelCaseToWords } from "../questionnaireEditor/LabeledField.jsx";

function DashboardSettingsConfiguration() {

  const [ eventsLabel, setEventsLabel ] = useState("");
  const [ eventTimeLabel, setEventTimeLabel ] = useState("");
  const [ enableTimeTabs, setEnableTimeTabs ] = useState(true);
  const [ hasChanges, setHasChanges ] = useState(false);

  const fields = [{
    key : "eventsLabel",
    setter: setEventsLabel,
    value: eventsLabel
  },
  {
    key : "eventTimeLabel",
    setter: setEventTimeLabel,
    value: eventTimeLabel
  },
  {
    key : "enableTimeTabs",
    setter: setEnableTimeTabs,
    value: enableTimeTabs,
    type: "boolean"
  }];

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
        title="Clinic dashboard"
        configPath={"/Survey/DashboardSettings"}
        configTemplate={fields.reduce((t, k) => ({...t, [k.key] : ""}), {})}
        onConfigFetched={readDashboardSettings}
        hasChanges={hasChanges}
        buildConfigData={buildConfigData}
        onConfigSaved={() => setHasChanges(false)}
        >
          <List>
          { fields.map(field => { return (
            <ListItem key={field.key}>
            { field.type != "boolean" ?
              <TextField
                InputLabelProps={{ shrink: true }}
                variant="standard"
                fullWidth
                id={field.key}
                name={field.key}
                type="text"
                label={camelCaseToWords(field.key)}
                value={field.value}
                onChange={(event) => { field.setter(event.target.value); setHasChanges(true); }}
              />
              :
              <FormControlLabel
                control={
                  <Checkbox
                    checked={field.value}
                    onChange={ event => { field.setter(event.target.checked); setHasChanges(true); } }
                    name={field.key}
                  />
                }
                label={camelCaseToWords(field.key)}
              />
            }
            </ListItem>)
            })
          }
          </List>
      </AdminConfigScreen>
  );
}

export default DashboardSettingsConfiguration;
