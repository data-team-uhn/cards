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
import React, { useContext, useEffect, useState } from 'react';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Typography
} from "@material-ui/core";

import Fields from "../questionnaireEditor/Fields.jsx";
import LiveTable from "../dataHomepage/LiveTable.jsx";
import NewItemButton from "../components/NewItemButton.jsx";
import ResponsiveDialog from "../components/ResponsiveDialog.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import { formatIdentifier } from "../questionnaireEditor/EditorInput.jsx";

function Clinics(props) {
  const [currentClinicName, setCurrentClinicName] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isNewClinic, setIsNewClinic] = useState(false);

  let columns = [
    {
      "key": "clinicName",
      "label": "ID",
      "format": "string",
    },
    {
      "key": "displayName",
      "label": "Name",
      "format": "string",
    },
    {
      "key": "sidebarLabel",
      "label": "Sidebar Entry",
      "format": "string",
    },
    {
      "key": "surveyID",
      "label": "Surveys",
      "format": "string",
    },
    {
      "key": "description",
      "label": "Description",
      "format": "string",
    },
    {
      "key": "emergencyContact",
      "label": "Emergency Contact",
      "format": "string",
    },
  ]

  let dialogSuccess = () => {
    // Since a dialog success changes the sidebar, we should reload everything
    location.reload();
  }

  let dialogClose = () => {
    setDialogOpen(false);
  }

  return (
    <>
      <Card>
        <CardHeader
          title={
            <Button>
              Clinics
          </Button>
          }
          action={
            <NewItemButton
              title="On-board a new clinic"
              onClick={() => { setDialogOpen(true); setIsNewClinic(true); }}
              inProgress={dialogOpen}
            />
          }
        />
        <CardContent>
          <LiveTable
            columns={columns}
            entryType={"Proms/ClinicMapping"}
            customUrl={"Proms/ClinicMapping.paginate"}
            admin={true}
          />
        </CardContent>
      </Card>
      <OnboardNewClinicDialog currentClinicName={currentClinicName} open={dialogOpen} onClose={dialogClose} onSuccess={dialogSuccess} isNewClinic={isNewClinic} />
    </>
  );
}

function OnboardNewClinicDialog(props) {
  let { currentClinicName, isNewClinic, open, onClose, onSuccess } = props;
  let [error, setError] = useState("");
  let [existingData, setExistingData] = useState(false);
  let [saveInProgress, setSaveInProgress] = useState(false);
  let [initialized, setInitialized] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let clinicsSpecs = require('./Clinics.json');

  let reset = () => {
    // reset all fields
    setError();
    setExistingData(false);
    setInitialized(false);
  }

  // Implementation of the Java hashcode function
  // Code taken from https://werxltd.com/wp/2010/05/13/javascript-implementation-of-javas-string-hashcode-method/
  // This is used to determine the proper node name of a clinic mapping
  let hashCode = (toConvert) => {
    var hash = 0;
    if (toConvert.length == 0) return hash;
    for (let i = 0; i < toConvert.length; i++) {
      let char = toConvert.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32bit integer
    }
    return hash;
  }

  useEffect(() => {
    if (!open) {
      reset();
      return;
    }
    if (!isNewClinic && currentClinicName) {
      let fetchExistingData = () => {
        // We want to keep references the way they are, since reference inputs will expect their UUIDs
        fetchWithReLogin(globalLoginDisplay, `/Proms/ClinicData/${hashCode(currentClinicName)}.-dereference.json`)
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then(setExistingData)
          .then(() => setInitialized(true))
          .catch(handleError);
      };
      if (!existingData) {
        fetchExistingData();
      }
    } else {
      setInitialized(true);
    }
  }, [open]);

  let saveData = (event) => {
    event.preventDefault();
    setSaveInProgress(true);

    // Unlike normal submissions, we need to extract out fields that don't exist in a ClinicMapping node, and are
    // meant to be processed separately
    let requestData = new FormData(event.currentTarget);

    // Verify that mandatory variables have been filled out
    let mandatoryFields = clinicsSpecs["//REQUIRED"];
    for (const fieldName of mandatoryFields) {
      if ((!requestData.has(fieldName)) || requestData.get(fieldName) == "") {
        setError(`The ${formatIdentifier(fieldName)} field is mandatory`);
        return;
      }
    }

    // Add this new clinic mapping
    let url = new URL("/Proms/ClinicMapping.addNew", window.location.origin);
    fetchWithReLogin(globalLoginDisplay, url,
      {
        method: 'POST',
        body: requestData
      })
      .then(() => {
        onSuccess && onSuccess();
      })
      .catch((response) => {setError(response.status + " " + response.statusText);})
      .finally(() => {
        setSaveInProgress(false);
      });
  }

  return (
    <form action='/Proms/ClinicMapping' method='POST' onSubmit={saveData}>
      <ResponsiveDialog disablePortal open={open} onClose={onClose}>
        <DialogTitle>{isNewClinic ? "Create New Clinic Mapping" : "Edit Clinic Mapping"}</DialogTitle>
        <DialogContent>
          {error && <Typography color="error">{error}</Typography>}
          <Grid container direction="column" spacing={2}>
            {
              // We don't want to load the Fields component until we are fully initialized
              // since otherwise the default values will be empty and cannot be assigned
              initialized && <Fields data={existingData || {}} JSON={clinicsSpecs} edit />
            }
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={onClose}
            variant="contained"
            color="default"
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            color="primary"
            disabled={saveInProgress}
          >
            {isNewClinic ? "Create" : "Save"}
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    </form>
  );
}

export default Clinics;