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
  const [ questionnairePath, setQuestionnairePath ] = useState();
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
        setQuestionnairePath(json.questionnaire?.["@path"] || "");
        setTitle(json.questionnaire?.["title"]);
        setAcronym(json.questionnaire?.["@name"]);
      });
    }
  }, [data]);

  if (!questionnairePath || !visitInfo) {
    return <CircularProgress/>;
  }

  let generateFilters = () => {
    let result = [];
    result.surveys = {
      comparator: "=",
      name: "surveys",
      title: visitInfo?.surveys?.["text"],
      type: visitInfo?.surveys?.["dataType"],
      uuid: visitInfo?.surveys?.["jcr:uuid"],
      value: surveysId
    };
    result.submitted = {
      comparator: "=",
      name: "surveys_submitted",
      title: visitInfo?.surveys_submitted?.["text"],
      type: visitInfo?.surveys_submitted?.["dataType"],
      uuid: visitInfo?.surveys_submitted?.["jcr:uuid"],
      value: 1
    };
    result.date = {
      comparator: "=",
      name: "time",
      title: visitInfo?.time?.["text"],
      type: visitInfo?.time?.["dataType"],
      uuid: visitInfo?.time?.["jcr:uuid"],
      value: '__DATE_FILTER_PLACEHOLDER__'
    };
    return result;
  }

  return (
    <PromsViewInternal
      color={color}
      avatar={<Typography variant="caption">{acronym?.substring(0,4)}</Typography>}
      title={title}
      columns={columns}
      questionnaireId={questionnairePath}
      query={`/Forms.paginate?fieldname=questionnaire&fieldvalue=${encodeURIComponent(questionnaireId)}&orderBy=${visitInfo?.time?.["jcr:uuid"]}`}
      filters={generateFilters()}
    />
  );
}

export default PromsView;
