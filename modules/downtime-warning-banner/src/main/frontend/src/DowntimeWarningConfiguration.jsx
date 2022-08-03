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
    FormControlLabel,
    List,
    ListItem,
} from '@mui/material';
import { makeStyles } from '@mui/styles';

import AdminConfigScreen from "./adminDashboard/AdminConfigScreen.jsx";

import { AdapterLuxon } from "@mui/x-date-pickers/AdapterLuxon";
import { LocalizationProvider } from '@mui/x-date-pickers';
import DateQuestionUtilities from "./questionnaire/DateQuestionUtilities";

const useStyles = makeStyles(theme => ({
  textField: {
    minWidth: "250px",
    paddingBottom: theme.spacing(2),
  },
}));

function DowntimeWarningConfiguration() {
  const classes = useStyles();

  // The configuration values
  const [ enabled, setEnabled ] = useState(false);
  const [ fromDate, setFromDate ] = useState();
  const [ toDate, setToDate ] = useState();
  const [ dateRangeIsInvalid, setDateRangeIsInvalid ] = useState(false);

  // Tracking unsaved changes
  const [ hasChanges, setHasChanges ] = useState();

  const dateFormat = "yyyy-MM-dd hh:mm";
  const views = DateQuestionUtilities.getPickerViews(dateFormat);

  // Read the settings from the saved configuration
  let readDowntimeWarningSettings = (json) => {
    setEnabled(json.enabled == 'true');
    setFromDate(DateQuestionUtilities.toPrecision(DateQuestionUtilities.stripTimeZone(json.fromDate)));
    setToDate(DateQuestionUtilities.toPrecision(DateQuestionUtilities.stripTimeZone(json.toDate)));
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
      <DatePicker
        inputFormat={dateFormat}
        views={views}
        label={label}
        minDate={lowerLimitLuxon || undefined}
        maxDate={upperLimitLuxon || undefined}
        value={value}
        onChange={(newValue) => {onDateChange(newValue);  setHasChanges(true); }}
        renderInput={ (params) =>
          <TextField
            variant="standard"
            InputLabelProps={{
              shrink: true,
            }}
            InputProps={{
              className: classes.textField
            }}
            helperText={null}
            {...params}
          />
        }
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
              {getDateField("Start of maintenance", fromDate || undefined, setFromDate)}
            </ListItem>
            <ListItem key="toDate">
              {getDateField("End of maintenance", toDate || undefined, setToDate)}
            </ListItem>
          </List>
    </AdminConfigScreen>
  );
}

export default DowntimeWarningConfiguration;
