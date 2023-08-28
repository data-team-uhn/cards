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
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Typography
} from "@mui/material";

import Fields from "../questionnaireEditor/Fields.jsx";
import AdminResourceListing from "../adminDashboard/AdminResourceListing.jsx";
import ResponsiveDialog from "../components/ResponsiveDialog.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import { camelCaseToWords } from "../questionnaireEditor/LabeledField.jsx";

function Clinics(props) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isNewClinic, setIsNewClinic] = useState(false);

  let clinicsSpecs = require('./Clinics.json');

  let columns = Object.keys(clinicsSpecs).filter((stat) => !Array.isArray(clinicsSpecs[stat]))
    .map((stat) => {
      return {
        key: stat,
        label: camelCaseToWords(stat),
        format: clinicsSpecs[stat]
      };
    });

  let dialogSuccess = () => {
    // Since a dialog success changes the sidebar, we should reload everything
    location.reload();
  }

  let dialogClose = () => {
    setDialogOpen(false);
  }

  return (
    <>
      <AdminResourceListing
        title="Clinics"
        buttonProps={{
          title: "On-board a new clinic",
          onClick: () => {
            setDialogOpen(true);
            setIsNewClinic(true);
          },
          inProgress: (dialogOpen && isNewClinic)
        }}
        columns={columns}
        entryType={"Survey/ClinicMapping"}
        customUrl={"Survey/ClinicMapping.paginate"}
        admin={true}
      />
      <OnboardNewClinicDialog open={dialogOpen} onClose={dialogClose} onSuccess={dialogSuccess} isNewClinic={isNewClinic} />
    </>
  );
}

function OnboardNewClinicDialog(props) {
  let { isNewClinic, open, onClose, onSuccess } = props;
  let [error, setError] = useState("");
  let [saveInProgress, setSaveInProgress] = useState(false);
  let [initialized, setInitialized] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let clinicsSpecs = require('./Clinics.json');

  let hints = null;
  try {
    hints = require(`./Clinics-hints.json`);
  } catch (e) {
    // do nothing
  }

  let reset = () => {
    // reset all fields
    setError();
    setInitialized(false);
  }

  useEffect(() => {
    if (!open) {
      reset();
      return;
    }
    setInitialized(true);
  }, [open]);

  let saveData = (event) => {
    event.preventDefault();

    // Unlike normal submissions, we need to extract out fields that don't exist in a ClinicMapping node, and are
    // meant to be processed separately
    let requestData = new FormData(event.currentTarget);

    // Verify that mandatory variables have been filled out
    let mandatoryFields = clinicsSpecs["//REQUIRED"];
    for (const fieldName of mandatoryFields) {
      if ((!requestData.has(fieldName)) || requestData.get(fieldName) == "") {
        setError(`The ${camelCaseToWords(fieldName)} field is mandatory`);
        return;
      }
    }

    // Add this new clinic mapping
    let url = new URL("/Survey/ClinicMapping", window.location.origin);
    setSaveInProgress(true);
    fetchWithReLogin(globalLoginDisplay, url,
      {
        method: 'POST',
        body: requestData
      })
      .then((response) => {
        if (response.ok) {
          onSuccess && onSuccess();
        } else {
          return response.json().then((result) => {throw result.error});
        }
      })
      .catch((errorText) => {setError("Error: " + errorText);})
      .finally(() => {
        setSaveInProgress(false);
      });
  }

  return (
    <form action='/Survey/ClinicMapping' method='POST' onSubmit={saveData}>
      <ResponsiveDialog disablePortal open={open} onClose={onClose}>
        <DialogTitle>{isNewClinic ? "Create New Clinic Mapping" : "Edit Clinic Mapping"}</DialogTitle>
        <DialogContent>
          {error && <Typography color="error">{error}</Typography>}
          <Grid container direction="column" spacing={2}>
            {
              // We don't want to load the Fields component until we are fully initialized
              // since otherwise the default values will be empty and cannot be assigned
              initialized && <Fields data={{}} JSON={clinicsSpecs} hints={hints} edit />
            }
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={onClose}
            variant="outlined"
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