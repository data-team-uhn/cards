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
    Checkbox,
    FormControlLabel,
    FormGroup,
    FormLabel,
    InputAdornment,
    List,
    ListItem,
    TextField
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import AdminConfigScreen from "../adminDashboard/AdminConfigScreen.jsx";

export const DATA_RETENTION_CONFIG_PATH = "/DataRetention/DataRetention";
export const DEFAULT_DATA_RETENTION_CONFIG = {
    deleteUnneededPatientDetails: false,
    deleteDraftAnswers: false,
    draftLifetime: "-1"
};

const useStyles = makeStyles(theme => ({
  textField: {
    margin: theme.spacing(3, 0),
    "& .MuiFormLabel-root" : {
      color: theme.palette.text.primary,
    },
    "& .MuiInputBase-root" : {
      maxWidth: "150px",
    },
  },
}));

function DataRetentionConfiguration() {
  const classes = useStyles();

  const [ dataRetentionConfig, setDataRetentionConfig ] = useState();
  const [ hasChanges, setHasChanges ] = useState(false);
  const [ error, setError ] = useState({});

  // Boolean fields can have one label
  // Text fields can have one label, one optional helper text, one optional error text
  const LABELS = {
    deleteUnneededPatientDetails: "Whether or not unneeded PII should be deleted",
    deleteDraftAnswers: "Whether or not draft answers should be deleted",
    draftLifetime: [
      "Patients can edit unsubmitted responses for:",
      "-1 means that drafts are kept until the patient is no longer able to access their surveys, 0 means drafts are deleted daily at midnight, 1 means they are kept until the next day at midmight, etc.",
      "Please use a value of at least 0, or -1 to disable periodic draft deletion."
    ]
  };

  const LIMITS = {
    draftLifetime: {min: -1}
  }

  let buildConfigData = (formData) => {
    for (let key of Object.keys(dataRetentionConfig)) {
      !key.startsWith("jcr:") && formData.append(key, dataRetentionConfig[key]);
    }
  }

  let renderConfigCheckbox = (key) => (
      <ListItem>
        <FormControlLabel control={
          <Checkbox
            name={key}
            checked={dataRetentionConfig?.[key] || DEFAULT_DATA_RETENTION_CONFIG[key]}
            onChange={event => {
              setDataRetentionConfig({...dataRetentionConfig, [key]: event.target.checked});
              setHasChanges(true);
            }}
          />}
          label={LABELS[key]}
        />
      </ListItem>
  );

  let onInputValueChanged = (key, value) => {
    setDataRetentionConfig(config => ({...config, [key]: (value || "")}));
    setHasChanges(true);
    setError(err => ({...err, [key]: (LIMITS[key]?.min > value || LIMITS[key]?.max < value)}));
  }

  let renderConfigInput = (key, unit) => (
      <ListItem>
        <FormGroup className={classes.textField}>
          <FormLabel>{LABELS[key][0]}</FormLabel>
          <TextField
            variant="standard"
            type="number"
            onChange={event => onInputValueChanged(key, event.target.value)}
            onBlur={event => onInputValueChanged(key, event.target.value)}
            placeholder={DEFAULT_DATA_RETENTION_CONFIG[key] || ""}
            value={dataRetentionConfig?.[key]}
            error={error[key]}
            helperText={error[key] ? LABELS[key][2] : LABELS[key][1]}
            InputProps={{
              endAdornment: unit && <InputAdornment position="end">{unit}</InputAdornment>,
            }}
            inputProps={LIMITS[key]}
          />
        </FormGroup>
      </ListItem>
    );

  return (
      <AdminConfigScreen
          title="Data Retention"
          configPath={DATA_RETENTION_CONFIG_PATH}
          configTemplate={Object.keys(DEFAULT_DATA_RETENTION_CONFIG).reduce((t, k) => ({...t, [k] : ""}), {})}
          onConfigFetched={setDataRetentionConfig}
          hasChanges={hasChanges}
          buildConfigData={buildConfigData}
          onConfigSaved={() => setHasChanges(false)}
          >
          <List>
            { renderConfigCheckbox("deleteUnneededPatientDetails") }
            { renderConfigCheckbox("deleteDraftAnswers") }
            { renderConfigInput("draftLifetime", "days") }
          </List>
      </AdminConfigScreen>
  );
}

export default DataRetentionConfiguration;
