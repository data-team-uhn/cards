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
import React, { useState, useEffect } from "react";

import {
  CircularProgress,
  Typography
} from "@mui/material";

import ClinicFormList from "./ClinicFormList.jsx";

function ClinicForms(props) {
  const { data, clinicId, color, visitInfo, dashboardConfig } = props;

  const [ columns, setColumns ] = useState();
  const [ questionnaireId, setQuestionnaireId ] = useState();
  const [ title, setTitle ] = useState(props.title);
  const [ acronym, setAcronym ] = useState();

  // At startup, load questionnaire
  useEffect(() => {
    if (data) {
        setColumns(JSON.parse(data["view"] || "[]"));
        setQuestionnaireId(data.questionnaire?.["jcr:uuid"] || "");
        setTitle(data.questionnaire?.["title"]);
        setAcronym(data.questionnaire?.["@name"]);
    }
  }, [data["@path"]]);

  if (!questionnaireId || !visitInfo) {
    return <CircularProgress/>;
  }

  let query = (
"select distinct dataForm.* " +
  "from " +
    "[cards:Subject] as visitSubject " +
    "inner join [cards:Form] as visitInformation on visitSubject.[jcr:uuid] = visitInformation.subject " +
      "inner join [cards:DateAnswer] as visitDate on visitDate.form = visitInformation.[jcr:uuid] " +
      "inner join [cards:ResourceAnswer] as visitClinic on visitClinic.form = visitInformation.[jcr:uuid] " +
      "inner join [cards:TextAnswer] as visitStatus on visitStatus.form = visitInformation.[jcr:uuid] " +
    "inner join [cards:Form] as dataForm on visitSubject.[jcr:uuid] = dataForm.subject " +
  "where " +
    `visitInformation.questionnaire = '${visitInfo?.["jcr:uuid"]}' ` +
      `and visitDate.question = '${visitInfo?.time?.["jcr:uuid"]}' and __DATE_FILTER_PLACEHOLDER__ ` +
      `and visitClinic.question = '${visitInfo?.clinic?.["jcr:uuid"]}' and visitClinic.value = '/Survey/ClinicMapping/${clinicId}' ` +
      `and visitStatus.question = '${visitInfo?.status?.["jcr:uuid"]}' and visitStatus.value <> 'cancelled' and visitStatus.value <> 'entered-in-error' ` +
    `and dataForm.questionnaire = '${questionnaireId}' ` +
      `and dataForm.statusFlags = 'SUBMITTED' ` +
  "order by visitDate.value __SORT_ORDER_PLACEHOLDER__ option (index tag cards)"
  )

  return (
    <ClinicFormList
      color={color}
      avatar={<Typography variant="caption">{acronym?.substring(0,4)}</Typography>}
      title={title}
      columns={columns}
      query={query}
      dateField="visitDate"
      questionnaireId={questionnaireId}
      key={data?.["@path"]}
      enableTimeTabs={dashboardConfig?.enableTimeTabs}
    />
  );
}

export default ClinicForms;
