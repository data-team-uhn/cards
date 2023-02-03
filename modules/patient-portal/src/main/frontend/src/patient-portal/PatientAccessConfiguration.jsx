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
    allowedPostVisitCompletionTime: "0"
};

const useStyles = makeStyles(theme => ({
  textField: {
    "& .MuiFormLabel-root" : {
      color: theme.palette.text.primary,
    },
    "& .MuiTextField-root" : {
      maxWidth: "250px",
    },
  },
}));

function PatientAccessConfiguration() {
  const classes = useStyles();

  const [ patientAccessConfig, setPatientAccessConfig ] = useState();
  const [ hasChanges, setHasChanges ] = useState(false);

  const labels = {
    tokenlessAuthEnabled: "Patients can answer surveys without a personalized link",
    PIIAuthRequired: "Patients must confirm their identity by providing their date of birth and either MRN or HCN",
    allowedPostVisitCompletionTime: "Patients can fill out surveys after the associated event for:"
  };

  let buildConfigData = (formData) => {
    for (let key of Object.keys(patientAccessConfig)) {
      !key.startsWith("jcr:") && formData.append(key, patientAccessConfig[key] || DEFAULT_PATIENT_ACCESS_CONFIG[key]);
    }
  }

  useEffect(() => {
    setHasChanges(true);
  }, [patientAccessConfig]);

  let renderConfigCheckbox = (key, valueOverride) => (
      <ListItem>
        <FormControlLabel control={
          <Checkbox
            name={key}
            checked={valueOverride || patientAccessConfig?.[key] || DEFAULT_PATIENT_ACCESS_CONFIG[key]}
            disabled={valueOverride}
            onChange={event => setPatientAccessConfig({...patientAccessConfig, [key]: valueOverride || event.target.checked})}
          />}
          label={labels[key]}
        />
      </ListItem>
    );

  let renderConfigInput = (key, unit) => (
      <ListItem>
        <FormGroup className={classes.textField}>
          <FormLabel>{labels[key]}</FormLabel>
          <TextField
            variant="standard"
            type="number"
            onChange={event => setPatientAccessConfig({...patientAccessConfig, [key]: event.target.value})}
            onBlur={event => setPatientAccessConfig({...patientAccessConfig, [key]: event.target.value})}
            placeholder={DEFAULT_PATIENT_ACCESS_CONFIG[key] || ""}
            value={patientAccessConfig?.[key]}
            InputProps={{
              endAdornment: unit && <InputAdornment position="end">{unit}</InputAdornment>,
            }}
          />
        </FormGroup>
      </ListItem>
    );

  return (
      <AdminConfigScreen
          title="Patient Access"
          configPath={PATIENT_ACCESS_CONFIG_PATH}
          configTemplate={Object.keys(DEFAULT_PATIENT_ACCESS_CONFIG).reduce((t, k) => ({...t, [k] : ""}), {})}
          onConfigFetched={setPatientAccessConfig}
          hasChanges={hasChanges}
          buildConfigData={buildConfigData}
          onConfigSaved={() => setHasChanges(false)}
          >
          <List>
            { renderConfigCheckbox("tokenlessAuthEnabled") }
            { renderConfigCheckbox("PIIAuthRequired", patientAccessConfig?.tokenlessAuthEnabled) }
            { renderConfigInput("allowedPostVisitCompletionTime", "days") }
          </List>
      </AdminConfigScreen>
  );
}

export default PatientAccessConfiguration;
