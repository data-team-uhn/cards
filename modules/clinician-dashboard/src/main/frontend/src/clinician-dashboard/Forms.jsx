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
import DefaultForms from "../dataHomepage/Forms.jsx";
import { getHierarchy } from "../questionnaire/SubjectIdentifier.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

function Forms(props) {

  const actionSwitches = {
    edit: () => false,
    delete: () => false,
    create: () => false,
    expand: () => false,
  }

  const columns = [
    {
      "key": "@name",
      "label": "Identifier",
      "format": getEntityIdentifier,
      "link": "dashboard+path",
    },
    {
      "key": "",
      "label": "Subject",
      "format": (row) => (row.subject ? getHierarchy(row.subject, undefined, undefined, props.extensionURL) : ''),
    },
    {
      "key": "questionnaire/title",
      "label": "Questionnaire",
      "format": "string",
    },
    {
      "key": "jcr:created",
      "label": "Created on",
      "format": "date:yyyy-MM-dd HH:mm",
    },
  ]

  return (
    <DefaultForms
      actionSwitches={actionSwitches}
      columns={columns}
      {...props}
    />
  );
}

export default Forms;
