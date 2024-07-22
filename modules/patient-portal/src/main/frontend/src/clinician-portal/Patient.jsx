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
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

import {
  Typography,
} from "@mui/material";
import withStyles from '@mui/styles/withStyles';

function ClinicianPatient(props) {
  const patientUuid = props.match.params.patientId.replace(/\..*/, '');
  const [ patientData, setPatientData ] = useState();
  const [ visitInformationQuestionnaire, setVisitInformationQuestionnaire ] = useState();
  const [ visits, setVisits ] = useState();
  const [ error, setError ] = useState();
  const globalLoginDisplay = useContext(GlobalLoginContext);

  const fetchPatientData = () => {
    fetchWithReLogin(globalLoginDisplay, `/Subjects/${patientUuid}.data.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setPatientData(json);
      })
      .catch(() => setError("The survey data could not be loaded for this visit. Please try again later or contact the administrator for further assistance."));
  };
  const fetchVisitInformationQuestionnaire = () => {
    fetchWithReLogin(globalLoginDisplay, '/Questionnaires/Visit information.deep.json')
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setVisitInformationQuestionnaire(json);
      })
      .catch(() => setError("The survey data could not be loaded for this visit. Please try again later or contact the administrator for further assistance."));
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
      .catch(() => setError("The survey data could not be loaded for this visit. Please try again later or contact the administrator for further assistance."));
  };

  useEffect(fetchPatientData, []);
  useEffect(fetchVisitInformationQuestionnaire, []);
  useEffect(fetchVisitInformationForms, [patientData, visitInformationQuestionnaire]);
  return (
    error ? <Typography>{error}</Typography>
    :
    <Typography>
      Clinician patient view! Looking at {props.match.params.patientId}
      {visits?.map((visit, index) => {
        return <Typography key={index}>{visit['@name']}</Typography>
      })}
    </Typography>
  );
}

export default withStyles(QuestionnaireStyle)(ClinicianPatient);
