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
import Subject from "../questionnaire/Subject.jsx";
// import SubjectSelectorList, { createSubjects, SubjectListItem } from "../questionnaire/SubjectSelector.jsx";

import { Button, Card, CardContent, CardHeader, Grid, Link, withStyles } from "@material-ui/core";

function Subjects(props) {

  const columns = [
    {
      "key": "identifier",
      "label": "Identifier",
      "format": "string",
      "link": "dashboard+field:@path",
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

  const entry = /Subjects\/(.+)/.exec(location.pathname);
  if (entry) {
    return <Subject id={entry[1]}/>;
  }

  return (
    <div>
      // add the subject dialog!
      //import the WHOLE new form dialog

      //the button will only set the subject popper state to true

      //make a new function for the subject popper so that it can have different return calls?
    <Card>
      <CardHeader
      />
      <CardContent>
        <LiveTable columns={columns} />
      </CardContent>
    </Card>
    </div>
  );
}

export default Subjects;

