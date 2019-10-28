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
import LiveTable from "./LiveTable.jsx";

export default function Forms(props) {
  const columns = [
    {
      "key": "jcr:uuid",
      "label": "Identifier",
      "format": "string",
      "link": "path",
    },
    {
      "key": "questionnaire/title",
      "label": "Questionnaire",
      "format": "string",
      "link": "dashboard+field:questionnaire/@path",
    },
    {
      "key": "subject/identifier",
      "label": "Subject",
      "format": "string",
      "link": "field:subject/@path",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
    {
      "key": "jcr:created",
      "label": "Created on",
      "format": "date:YYYY-MM-DD HH:mm",
    },
  ]
  return (
    <LiveTable columns={columns} />
  );
}
