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
  const [ questionnairePath, setQuestionnairePath ] = useState();
  const [ title, setTitle ] = useState(props.title);
  const [ acronym, setAcronym ] = useState();

  // At startup, load questionnaire
  useEffect(() => {
    if (data) {
        setColumns(JSON.parse(data["view"] || "[]"));
        setQuestionnaireId(data.questionnaire?.["jcr:uuid"] || "");
        setQuestionnairePath(json.questionnaire?.["@path"] || "");
        setTitle(data.questionnaire?.["title"]);
        setAcronym(data.questionnaire?.["@name"]);
    }
  }, [data["@path"]]);

  if (!questionnairePath || !visitInfo) {
    return <CircularProgress/>;
  }

  let filters = {
    surveys : {
      comparator: "=",
      name: "surveys",
      title: visitInfo?.surveys?.["text"],
      type: visitInfo?.surveys?.["dataType"],
      uuid: visitInfo?.surveys?.["jcr:uuid"],
      value: surveysId,
      hidden: true
    },
    status : {
      comparator: "<>",
      name: "status",
      title: visitInfo?.status?.["text"],
      type: visitInfo?.status?.["dataType"],
      uuid: visitInfo?.status?.["jcr:uuid"],
      value: "cancelled",
      hidden: true
    },
    submitted : {
      comparator: "=",
      name: "surveys_submitted",
      title: visitInfo?.surveys_submitted?.["text"],
      type: visitInfo?.surveys_submitted?.["dataType"],
      uuid: visitInfo?.surveys_submitted?.["jcr:uuid"],
      value: 1,
      hidden: true
    },
    date : {
      comparator: "=",
      name: "time",
      title: visitInfo?.time?.["text"],
      type: visitInfo?.time?.["dataType"],
      uuid: visitInfo?.time?.["jcr:uuid"],
      dateFormat: "yyyy-MM-dd",
      value: '__DATE_FILTER_PLACEHOLDER__',
      hidden: true
    }
  };

  return (
    <ClinicFormList
      color={color}
      avatar={<Typography variant="caption">{acronym?.substring(0,4)}</Typography>}
      title={title}
      columns={columns}
      key={data?.["@path"]}
      enableTimeTabs={dashboardConfig?.enableTimeTabs}
      questionnairePath={questionnairePath}
      query={`/Forms.paginate?fieldname=questionnaire&fieldvalue=${encodeURIComponent(questionnaireId)}&orderBy=${visitInfo?.time?.["jcr:uuid"]}`}
      filters={filters}
    />
  );
}

export default ClinicForms;
