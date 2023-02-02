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
import React, { useEffect, useState, useRef } from 'react';
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

export const PATIENT_ACCESS_CONFIG_PATH = "/Survey/PatientAccess";
export const DEFAULT_PATIENT_ACCESS_CONFIG = {
    tokenlessAuthEnabled: false,
    PIIAuthRequired: false,
    allowedPostVisitCompletionTime: "0",
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

function PatientAccessConfiguration() {
  const classes = useStyles();

  const [ patientAccessConfig, setPatientAccessConfig ] = useState();
  const [ hasChanges, setHasChanges ] = useState(false);
  const [ draftLifetime, setDraftLifetime ] = useState();
  const [ error, setError ] = useState(false);
  const inputRef = useRef(null);

  // Boolean fields can have one label
  // Text fields can have one label and one optional helper text
  const labels = {
    tokenlessAuthEnabled: "Patients can answer surveys without a personalized link",
    PIIAuthRequired: "Patients must confirm their identity by providing their date of birth and either MRN or HCN",
    allowedPostVisitCompletionTime: ["Patients can fill out surveys after the associated event for:"],
    draftLifetime: ["Patients can edit unsubmitted responses for:", "-1 means that drafts are kept until the patient is no longer able to access their surveys"]
  };
  const errorText = `Please use a value between 0 and ${patientAccessConfig?.allowedPostVisitCompletionTime}, or ${DEFAULT_PATIENT_ACCESS_CONFIG['draftLifetime']} to disable periodic draft deletion.`;

  let readPatientAccessConfigData = (json) => {
	setDraftLifetime(json.draftLifetime);
	inputRef.current.value = json.draftLifetime;
	setPatientAccessConfig(json);
  }

  let buildConfigData = (formData) => {
    for (let key of Object.keys(patientAccessConfig)) {
      !key.startsWith("jcr:") && formData.append(key, patientAccessConfig[key] || DEFAULT_PATIENT_ACCESS_CONFIG[key]);
    }
    formData.append('draftLifetime', draftLifetime || DEFAULT_PATIENT_ACCESS_CONFIG['draftLifetime']);
  }

  useEffect(() => {
    setError(draftLifetime && (draftLifetime > patientAccessConfig?.allowedPostVisitCompletionTime
                                  || draftLifetime < DEFAULT_PATIENT_ACCESS_CONFIG['draftLifetime']));
  }, [draftLifetime]);

  let renderConfigCheckbox = (key, valueOverride) => (
      <ListItem>
        <FormControlLabel control={
          <Checkbox
            name={key}
            checked={valueOverride || patientAccessConfig?.[key] || DEFAULT_PATIENT_ACCESS_CONFIG[key]}
            disabled={valueOverride}
            onChange={event => {
              setPatientAccessConfig({...patientAccessConfig, [key]: valueOverride || event.target.checked});
              setHasChanges(true);
            }}
          />}
          label={labels[key]}
        />
      </ListItem>
    );

  let renderConfigInput = (key, unit, inputProps) => (
      <ListItem>
        <FormGroup className={classes.textField}>
          <FormLabel>{labels[key][0]}</FormLabel>
          <TextField
            variant="standard"
            type="number"
            onChange={event => { setPatientAccessConfig({...patientAccessConfig, [key]: event.target.value}); setHasChanges(true); }}
            onBlur={event => { setPatientAccessConfig({...patientAccessConfig, [key]: event.target.value}); setHasChanges(true); }}
            placeholder={DEFAULT_PATIENT_ACCESS_CONFIG[key] || ""}
            value={patientAccessConfig?.[key]}
            helperText={labels[key][1]}
            InputProps={{
              endAdornment: unit && <InputAdornment position="end">{unit}</InputAdornment>,
            }}
            inputProps={inputProps}
          />
        </FormGroup>
      </ListItem>
    );

  let renderDraftLifetimeConfigInput = (key, unit, inputProps) => (
      <ListItem>
        <FormGroup className={classes.textField}>
          <FormLabel>{labels[key][0]}</FormLabel>
          <TextField
            inputRef={inputRef}
            error={error}
            variant="standard"
            type="number"
            onChange={event => setDraftLifetime(event.target.value)}
            onBlur={event => setDraftLifetime(event.target.value)}
            placeholder={DEFAULT_PATIENT_ACCESS_CONFIG[key] || ""}
            value={draftLifetime}
            helperText={error ? errorText : labels[key][1]}
            InputProps={{
              endAdornment: unit && <InputAdornment position="end">{unit}</InputAdornment>,
            }}
            inputProps={inputProps}
          />
        </FormGroup>
      </ListItem>
    );

  return (
      <AdminConfigScreen
          title="Patient Access"
          configPath={PATIENT_ACCESS_CONFIG_PATH}
          configTemplate={Object.keys(DEFAULT_PATIENT_ACCESS_CONFIG).reduce((t, k) => ({...t, [k] : ""}), {})}
          onConfigFetched={readPatientAccessConfigData}
          hasChanges={hasChanges}
          buildConfigData={buildConfigData}
          onConfigSaved={() => setHasChanges(false)}
          >
          <List>
            { renderConfigCheckbox("tokenlessAuthEnabled") }
            { renderConfigCheckbox("PIIAuthRequired", patientAccessConfig?.tokenlessAuthEnabled) }
            { renderConfigInput("allowedPostVisitCompletionTime", "days") }
            { renderDraftLifetimeConfigInput("draftLifetime", "days", {min: -1, max: patientAccessConfig?.allowedPostVisitCompletionTime}) }
          </List>
      </AdminConfigScreen>
  );
}

export default PatientAccessConfiguration;
