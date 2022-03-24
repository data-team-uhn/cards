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
  // Keep track of how many entries we add, to force refresh of LiveTable
  const [numNewEntries, setNumNewEntries] = useState(0);

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
      "key": "surveyID",
      "label": "Menu Entry",
      "format": "string",
    },
    {
      "key": "@name", // TODO: Needs to be the @name of the actual QuestionnaireSet
      "label": "Surveys",
      "format": "string",
    },
    {
      "key": "emergencyContact",
      "label": "Emergency Contact",
      "format": "string",
    },
  ]

  let dialogSuccess = () => {
    setNumNewEntries((old) => old + 1);
    dialogClose();
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
              Statistics
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
            updateData={numNewEntries}
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

    // If this clinic does not exist, we need to create a new path for it
    let newClinicName = requestData.get("clinicName");
    let surveyID = requestData.get("surveyID");
    let displayName = requestData.get("displayName");
    let sidebarName = requestData.get("sidebarLabel");
    let idHash = hashCode(newClinicName);

    // TODO: If the idHash changes (i.e. clinicName changes) and we are editing a clinic, we need to delete the old one
    // and create a new one
    requestData.append("jcr:primaryType", "cards:ClinicMapping");
    let newFetch = fetchWithReLogin(globalLoginDisplay, "/Proms/ClinicMapping/" + idHash,
      {
        method: 'POST',
        body: requestData
      });

    console.log(isNewClinic);

    // There are a series of new things that need to eb added when creating a new clinic
    if (isNewClinic) {
      let clinicRemap = new FormData();
      clinicRemap.append(":newClinic", newClinicName);

      let sidebarBody = new FormData();
      sidebarBody.append("jcr:primaryType", "cards:Extension");
      sidebarBody.append("cards:extensionPointId", "cards/coreUI/sidebar/entry");
      sidebarBody.append("cards:extensionName", sidebarName);
      sidebarBody.append("cards:targetURL", "/content.html/Dashboard/" + idHash);
      sidebarBody.append("cards:icon", "asset:proms-homepage.pmccIcon.js");
      sidebarBody.append("cards:defaultOrder", 10);

      let dashboardView = new FormData();
      dashboardView.append("jcr:primaryType", "cards:Extension");
      dashboardView.append("cards:extensionPointId", "cards/coreUI/view");
      dashboardView.append("cards:extensionName", "Dashboard");
      dashboardView.append("cards:extensionRenderURL", "asset:proms-homepage.PromsDashboard.js");
      dashboardView.append("cards:targetURL", "/content.html/Dashboard");
      dashboardView.append("cards:exactURLMatch", false);

      let dashboardExtension = new FormData();
      dashboardExtension.append("jcr:primaryType", "cards:ExtensionPoint");
      dashboardExtension.append("cards:extensionPointId", `proms/dashboard/${idHash}`);
      dashboardExtension.append("cards:extensionPointName", `${displayName} questionnaires dashboard`);
      dashboardExtension.append("title", displayName);
      dashboardExtension.append("description", displayName);
      dashboardExtension.append("surveys", surveyID);

      let dashboardHomepage = new FormData();
      dashboardHomepage.append("jcr:primaryType", "cards:Extension");
      dashboardHomepage.append("cards:extensionPointId", `proms/dashboard/${idHash}`);
      dashboardHomepage.append("cards:extensionName", `${displayName} View`);
      dashboardHomepage.append("cards:extensionRenderURL", "asset:proms-homepage.PromsView.js");
      dashboardHomepage.append("cards:defaultOrder", 5);
      dashboardHomepage.append("cards:data", `/Proms/${surveyID}`); // TODO: Double check this, not sure it's right

      newFetch
        // After updating the clinicMapping, we also need to update all running configurations with the new data
        .then(() => fetchWithReLogin(globalLoginDisplay, "/Proms/ClinicMapping.addNew",
          {
            method: 'POST',
            body: clinicRemap
          }))
        // After updating the configurations, we need to create the sidebar entry
        .then(() => fetchWithReLogin(globalLoginDisplay, `/Extensions/Sidebar/${idHash}`,
          {
            method: 'POST',
            body: sidebarBody
          }))
        // And the new dashboard view
        .then(() => fetchWithReLogin(globalLoginDisplay, `/Extensions/Views/${idHash}Dashboard`,
          {
            method: 'POST',
            body: dashboardView
          }))
        // And the new extension point for the dashboard view
        .then(() => fetchWithReLogin(globalLoginDisplay, `/apps/cards/ExtensionPoints/DashboardViews${idHash}`,
          {
            method: 'POST',
            body: dashboardExtension
          }))
        // And the new extension point for appointments of this clinic
        .then(() => fetchWithReLogin(globalLoginDisplay, `/Extensions/DashboardViews/${idHash}View`,
          {
            method: 'POST',
            body: dashboardHomepage
          }))
        .then(() => {
          onSuccess && onSuccess();
        })
        .catch((response) => {setError(response.status + " " + response.statusText);})
        .finally(() => {
          setSaveInProgress(false);
        });
    }
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