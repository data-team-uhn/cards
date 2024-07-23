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
import React, { useState, useEffect, useContext } from "react";

import { withRouter, useHistory } from "react-router-dom";

import {
  Alert,
  AlertTitle,
  Box,
  CircularProgress,
  Grid,
  Tooltip,
  Typography,
} from "@mui/material";

import ContactPageIcon from '@mui/icons-material/ContactPage';
import EventNoteIcon from '@mui/icons-material/EventNote';
import EventDoneIcon from '@mui/icons-material/EventAvailable';
import LockIcon from '@mui/icons-material/Lock';

import { DataGrid } from '@mui/x-data-grid';

import { DateTime } from "luxon";

import ResourceHeader from "../questionnaire/ResourceHeader.jsx";
import DateQuestionUtilities from "../questionnaire/DateQuestionUtilities";
import { getHierarchyAsList, getTextHierarchy } from "../questionnaire/SubjectIdentifier";
import { FORM_ENTRY_CONTAINER_PROPS } from "../questionnaire/QuestionnaireStyle.jsx";

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

const VISIT_INFORMATION_FORM_TITLE = "Visit information";

const statusIcons = {
  "LOCKED" : <LockIcon sx={{mt: 1.5, ml: 1}} />,
  "SUBMITTED" : <EventDoneIcon sx={{mt: 1.5, ml: 1}} />,
  "DEFAULT" : <EventNoteIcon sx={{mt: 1.5, ml: 1}} />,
}

const visitGridColumns = [
  {
    field: 'icon',
    headerName: '',
    width: 50,
    renderCell: ({ value }) => (
      value && statusIcons[value]
      ? <Tooltip title={value}>{statusIcons[value]}</Tooltip>
      : statusIcons["DEFAULT"]
    )
  },
  {
    field: 'id',
    headerName: 'Visit number',
    width: 150
  },
  {
    field: 'time',
    headerName: "Date / time",
    type: "dateTime",
    width: 250,
    valueFormatter: ({ value }) => {
      if (!value) return null;
      let dateTime = DateQuestionUtilities.toPrecision(DateQuestionUtilities.stripTimeZone(value));
      let dateTimeString = !dateTime?.isValid ? "" : dateTime.toLocaleString(DateTime.DATETIME_MED_WITH_WEEKDAY);
      return dateTimeString;
    }
  },
  {
    field: 'location',
    headerName: 'Location',
    width: 250,
  },
  {
    field: 'provider',
    headerName: 'Provider',
    width: 250,
  },
];
function Patient(props) {
  const patientUuid = props.match.params.patientId.replace(/\..*/, '');

  // Data already associated with the subject
  const [ patientData, setPatientData ] = useState();
  // Visit information questionnaire definition, allowing to get data about the patient's visits
  const [ visitInformationQuestionnaire, setVisitInformationQuestionnaire ] = useState();
  // List of visits on record for this patient
  const [ visits, setVisits ] = useState();
  // Visit data formatted for display in a DataGrid
  const [ visitGridRows, setVisitGridRows ] = useState();
  // When something goes wrong:
  const [ error, setError ] = useState();

  const history = useHistory();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // --------------------------------------------------------------------------------------------------------------
  // Load and format the existing patient data, including any visits on record

  const fetchPatientData = () => {
    fetchWithReLogin(globalLoginDisplay, `/Subjects/${patientUuid}.data.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setPatientData(json);
      })
      .catch(() => setError("The patient record could not be loaded. Please try again later or contact the administrator for further assistance."));
  };
  const fetchVisitInformationQuestionnaire = () => {
    fetchWithReLogin(globalLoginDisplay, `/Questionnaires/${VISIT_INFORMATION_FORM_TITLE}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setVisitInformationQuestionnaire(json);
      })
      .catch(() => setError("The visit configuration could not be loaded. Please try again later or contact the administrator for further assistance."));
  };
  const fetchVisitInformationForms = () => {
    if (!visitInformationQuestionnaire || !patientData) {
      return;
    }
    let url = new URL("/query", window.location.origin);
    url.searchParams.set("query", `SELECT * FROM [cards:Form] as f where f.questionnaire = '${visitInformationQuestionnaire['jcr:uuid']}' and f.relatedSubjects = '${patientData['jcr:uuid']}' option (index tag cards)`);
    url.searchParams.set("limit", 1000);
    fetchWithReLogin(globalLoginDisplay, url)
      .then(response => response.json())
      .then(result => {
        setVisits(Array.from(result.rows))
      })
      .catch(() => setError("The visits could not be loaded for this patient. Please try again later or contact the administrator for further assistance."));
  };

  const formatVisits = () => {
    if (!visits) return;
    setVisitGridRows(
      visits?.map(visit => ({
        icon: visit?.statusFlags?.[visit.statusFlags.length - 1], // Get the latest status flag
        id: visit?.subject?.identifier,
        path: visit?.subject?.["@path"],
        time: visit?.time && new Date(visit?.time),
        location: visit?.location,
        provider: visit?.provider?.join(", "),
      }))
    );
  }

  useEffect(fetchPatientData, []);
  useEffect(fetchVisitInformationQuestionnaire, []);
  useEffect(fetchVisitInformationForms, [patientData, visitInformationQuestionnaire]);
  useEffect(formatVisits, [visits]);

  // --------------------------------------------------------------------------------------------------------------
  // Message screens are displayed if the data isn't loaded yet or if there's an error

  const displayMessageScreen = (message, type, icon) => (
    <Grid container {...FORM_ENTRY_CONTAINER_PROPS}>
      <Grid item>
        <Alert severity={type} icon={icon}>{message}</Alert>
      </Grid>
    </Grid>
  );

  if (error) {
    return displayMessageScreen(error, "error");
  }

  if (!patientData || !visits) {
    return displayMessageScreen(
      <AlertTitle>Loading...</AlertTitle>,
      "info",
      <CircularProgress size={24} />
    );
  }

  // ----------------------------------------------------------------------------------------------------------------
  //

  const displayPatientInfo = () => {
    let fName = patientData?.first_name;
    let lName = patientData?.last_name;
    let name = [lName, fName].filter(n => n).join(", ");

    let dobAnswer = patientData?.date_of_birth;
    let dob = DateQuestionUtilities.toPrecision(DateQuestionUtilities.stripTimeZone(dobAnswer));
    let dobString = !dob?.isValid ? "" : dob.toLocaleString(DateTime.DATE_FULL);
    let sex = patientData?.sex;
    let birthInfo = [dobString, sex].filter(i => i).join(", ");

    return (name || birthInfo) ?
      <>
        {name ? <> {name}<br/> </> : null}
        {birthInfo ? <> {birthInfo} </> : null}
      </>
    : null
  };

  const patientInfo = displayPatientInfo();

  // -----------------------------------------------------------------------------------------------------------------
  // Render the patient record:
  // * Resource header (sticky to the top) with no menu
  // * Patient information
  // * List of visits for this patient

  return (
    <Grid container {...FORM_ENTRY_CONTAINER_PROPS}>
      <ResourceHeader
        title={`Patient ${patientData?.identifier}`}
        breadcrumbs={(patientData?.parents && getHierarchyAsList(patientData.parents, true) || "")}
      />
      { patientInfo && <Grid item><Alert severity="info" icon={<ContactPageIcon/>}>{ patientInfo }</Alert></Grid> }
      <Grid item>
        <Box sx={{ height: 400, width: '100%' }}>
          <DataGrid
            sx={{cursor: "pointer"}}
            rows={visitGridRows}
            columns={visitGridColumns}
            initialState={{
              pagination: {
                paginationModel: {
                  pageSize: 5,
                },
              },
            }}
            onRowClick={(params, event) => {
               event?.preventDefault();
               history.push(`/content.html${params.row.path}`);
            }}
            pageSizeOptions={[5]}
          />
        </Box>
      </Grid>
    </Grid>
  );
}

export default withRouter(Patient);
