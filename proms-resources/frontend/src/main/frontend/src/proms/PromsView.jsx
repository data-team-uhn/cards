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

import {
  CircularProgress,
  Typography,
  makeStyles,
} from "@material-ui/core";

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import PromsViewInternal from "./PromsViewInternal.jsx";

function PromsView(props) {
  const { data, surveysId, color, visitInfo } = props;

  const [ columns, setColumns ] = useState();
  const [ questionnaireId, setQuestionnaireId ] = useState();
  const [ title, setTitle ] = useState(props.title);
  const [ acronym, setAcronym ] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // At startup, load questionnaire
  useEffect(() => {
    if (data && !columns) {
      fetchWithReLogin(globalLoginDisplay, data + ".deep.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setColumns(JSON.parse(json["view"] || "[]"));
        setQuestionnaireId(json.questionnaire?.["jcr:uuid"] || "");
        setTitle(json.questionnaire?.["title"]);
        setAcronym(json.questionnaire?.["@name"]);
      });
    }
  }, [data]);

  if (!questionnaireId || !visitInfo) {
    return <CircularProgress/>;
  }

  let query = (
"select distinct dataForm.* " +
  "from " +
    "[cards:Subject] as visitSubject " +
    "inner join [cards:Form] as visitInformation on visitSubject.[jcr:uuid] = visitInformation.subject " +
      "inner join [cards:Answer] as visitDate on isdescendantnode(visitDate, visitInformation) " +
      "inner join [cards:Answer] as visitSurveys on isdescendantnode(visitSurveys, visitInformation) " +
      "inner join [cards:Answer] as visitStatus on isdescendantnode(visitStatus, visitInformation) " +
      "inner join [cards:Answer] as patientSubmitted on isdescendantnode(patientSubmitted, visitInformation) " +
    "inner join [cards:Form] as dataForm on visitSubject.[jcr:uuid] = dataForm.subject " +
  "where " +
    `visitInformation.questionnaire = '${visitInfo?.["jcr:uuid"]}' ` +
      `and visitDate.question = '${visitInfo?.time?.["jcr:uuid"]}' and __DATE_FILTER_PLACEHOLDER__ ` +
      `and visitSurveys.question = '${visitInfo?.surveys?.["jcr:uuid"]}' and visitSurveys.value = '${surveysId}' ` +
      `and visitStatus.question = '${visitInfo?.status?.["jcr:uuid"]}' and visitStatus.value <> 'cancelled' ` +
      `and patientSubmitted.question = '${visitInfo?.surveys_submitted?.["jcr:uuid"]}' and patientSubmitted.value = 1 ` +
    `and dataForm.questionnaire = '${questionnaireId}' ` +
  "order by visitDate.value __SORT_ORDER_PLACEHOLDER__"
  )

  return (
    <PromsViewInternal
      color={color}
      avatar={<Typography variant="caption">{acronym?.substring(0,4)}</Typography>}
      title={title}
      columns={columns}
      query={query}
      dateField="visitDate"
      questionnaireId={questionnaireId}
    />
  );
}

export default PromsView;
