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
    FormGroup,
    FormControlLabel,
    List,
    ListItem,
    Typography
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import AdminConfigScreen from "../adminDashboard/AdminConfigScreen.jsx";
import { camelCaseToWords } from "../questionnaireEditor/LabeledField.jsx";

export const PATIENT_IDENTIFICATION_PATH = "/Proms/PatientIdentification";
export const DEFAULT_PLACEHOLDER = {
};

const useStyles = makeStyles(theme => ({
  formEntries: {
    "& .MuiListItem-root:not(:first-child) .MuiTypography-root": {
      marginTop: theme.spacing(3),
    },
  },
}));

function PatientIdentificationConfiguration() {
  const classes = useStyles();

  const [ patientIdentification, setPatientIdentification ] = useState();
  const [ hasChanges, setHasChanges ] = useState(false);

  const labels = {
    enableTokenlessAuth: "Patients can answer surveys without a personalized link",
    requirePIIAuth: "Patients must confirm their identity by providing their date of birth and either MRN or HCN",
    tokenLifetime: "The authentication token is valid after the associated event for"
  };

  let buildConfigData = (formData) => {
    for (let key of Object.keys(patientIdentification)) {
      !key.startsWith("jcr:") && formData.append(key, patientIdentification[key] || "");
    }
  }

  useEffect(() => {
    setHasChanges(true);
  }, [patientIdentification]);

  function ConfigCheckbox(props) {
    const { id } = props;
  
    return(
      <ListItem>
          <FormGroup>
              <FormControlLabel control={
                  <Checkbox
                      id={id}
                      name={id}
                      value={patientIdentification?.[id] || false}
                      onChange={(event) => { setPatientIdentification({...patientIdentification, [id]: event.target.value}); }}
                  />
                  }
                  label={labels[id]}
              />
          </FormGroup>
      </ListItem>
    );
  }

  return (
      <AdminConfigScreen
          title="Patient Identification Configuration"
          configPath={PATIENT_IDENTIFICATION_PATH}
          onConfigFetched={setPatientIdentification}
          hasChanges={hasChanges}
          buildConfigData={buildConfigData}
          onConfigSaved={() => setHasChanges(false)}
          >
          <List className={classes.formEntries}>
              <ListItem>
                  <Typography variant="h6">{camelCaseToWords("Configuration")}</Typography>
              </ListItem>
              <ConfigCheckbox id="enableTokenlessAuth" />
              <ConfigCheckbox id="requirePIIAuth" />
              <ConfigCheckbox id="tokenLifetime" />
          </List>
      </AdminConfigScreen>
  );
}

export default PatientIdentificationConfiguration;
