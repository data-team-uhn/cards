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
    FormControlLabel,
    List,
    ListItem,
} from '@mui/material';
import { makeStyles } from '@mui/styles';

import AdminConfigScreen from "./adminDashboard/AdminConfigScreen.jsx";

import { AdapterLuxon } from "@mui/x-date-pickers/AdapterLuxon";
import { LocalizationProvider } from '@mui/x-date-pickers';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { DateTime } from "luxon";
import DateTimeUtilities from "./questionnaire/DateTimeUtilities";

const useStyles = makeStyles(theme => ({
  textField: {
    minWidth: "250px",
  },
}));

function DowntimeWarningConfiguration() {
  const classes = useStyles();

  // The configuration values
  const [ enabled, setEnabled ] = useState(false);
  const [ fromDate, setFromDate ] = useState(null);
  const [ toDate, setToDate ] = useState(null);
  const [ dateRangeIsInvalid, setDateRangeIsInvalid ] = useState(false);

  // Tracking unsaved changes
  const [ hasChanges, setHasChanges ] = useState();

  const dateFormat = "yyyy-MM-dd HH:mm";
  const views = DateTimeUtilities.getPickerViews(dateFormat);

  // Read the settings from the saved configuration
  let readDowntimeWarningSettings = (json) => {
    setEnabled(json.enabled == 'true');
    json.fromDate && setFromDate(DateTime.fromFormat(json.fromDate, dateFormat));
    json.toDate && setToDate(DateTime.fromFormat(json.toDate, dateFormat));
  }

  let buildConfigData = (formData) => {
    formData.append('enabled', enabled);
    formData.append('fromDate', fromDate.toFormat(dateFormat));
    formData.append('toDate', toDate.toFormat(dateFormat));
  }

  useEffect(() => {
    // Determine if the end date is earlier than the start date
    setDateRangeIsInvalid(!!fromDate && !!toDate && toDate < fromDate);
  }, [fromDate, toDate]);

  useEffect(() => {
    setHasChanges(true);
  }, [enabled, fromDate, toDate]);

  let getDateField = (label, value, onDateChange) => {
    return (
    <LocalizationProvider dateAdapter={AdapterLuxon}>
      <DateTimePicker
        format={dateFormat}
        ampm={false}
        label={label}
        value={value}
        onChange={(newValue) => {onDateChange(newValue);  setHasChanges(true); }}
        componentsProps={{ textField: {
                             variant: 'standard',
                             helperText: null,
                             InputProps: {
				               className: classes.textField
				             }
                         }
        }}
      />
    </LocalizationProvider>);
  }

  return (
    <AdminConfigScreen
        title="Downtime Warning Banner Settings"
        configPath="/apps/cards/config/DowntimeWarning"
        configTemplate={{enabled: false, fromDate: "", toDate: ""}}
        onConfigFetched={readDowntimeWarningSettings}
        hasChanges={hasChanges}
        configError={!!dateRangeIsInvalid ? "Invalid date range" : undefined}
        buildConfigData={buildConfigData}
        onConfigSaved={() => setHasChanges(false)}
      >
          <List>
            <ListItem key="enabled">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={enabled}
                    onChange={ event => { setEnabled(event.target.checked); setHasChanges(true); } }
                    name="enabled"
                  />
                }
                label="Show downtime warning banner"
              />
            </ListItem>
            <ListItem key="fromDate">
              {getDateField("Start of maintenance", fromDate, setFromDate)}
            </ListItem>
            <ListItem key="toDate">
              {getDateField("End of maintenance", toDate, setToDate)}
            </ListItem>
          </List>
    </AdminConfigScreen>
  );
}

export default DowntimeWarningConfiguration;
