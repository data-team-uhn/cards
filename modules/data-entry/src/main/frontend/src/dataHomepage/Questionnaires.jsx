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
import React from "react";
import Questionnaire from "../questionnaire/Questionnaire.jsx";
import AdminResourceListing from "../adminDashboard/AdminResourceListing.jsx";
import NewQuestionnaireDialog from "../questionnaireEditor/NewQuestionnaireDialog.jsx";
import DeleteButton from "./DeleteButton.jsx";
import EditButton from "./EditButton.jsx";
import ExportButton from "./ExportButton.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

function Questionnaires(props) {
  const entry = /Questionnaires\/([^.]+)/.exec(location.pathname);

  if (entry) {
    return <Questionnaire id={entry[1]} key={location.pathname} contentOffset={props.contentOffset}/>;
  }

  let columns = [
    {
      "key": "title",
      "label": "Title",
      "format": getEntityIdentifier,
      "link": "dashboard+path",
    },
    {
      "key": "jcr:created",
      "label": "Created on",
      "format": "date:yyyy-MM-dd HH:mm",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
  ]
  const actions = [
    DeleteButton,
    ExportButton,
    EditButton
  ]

  return (
    <>
      <AdminResourceListing
        title="Questionnaires"
        action={
          <NewQuestionnaireDialog />
        }
        columns={columns}
        actions={actions}
        entryType={"Questionnaire"}
        admin={true}
      />
    </>
  );
}

export default Questionnaires;
