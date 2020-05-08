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
import React, { useState } from "react";
import LiveTable from "./LiveTable.jsx";
import Subject from "../questionnaire/Subject.jsx";
import { SelectorDialog } from "../questionnaire/SubjectSelector.jsx";

import { Button, Card, CardContent, CardHeader, Grid, Link, withStyles, ListItemText, Tooltip, Fab } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";

function Subjects(props) {

  // const { children, classes } = props;
  // fix issue with classes

  let [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  // When a new subject is added, state will be updated and trigger a livetable refresh
  const [ requestFetchData, setRequestFetchData ] = useState(0);

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

  // import the function

  return (
    <div>
      <Card>
        <CardHeader
          title="Subjects"
          action={
            <Tooltip aria-label="add" title="New Subject">
              <Fab
                color="primary"
                aria-label="add"
                onClick={() => {setNewSubjectPopperOpen(true)}}
              >
                <AddIcon />
              </Fab>
            </Tooltip>
          }
        />
        <CardContent>
          <LiveTable columns={columns} updateData={requestFetchData}/>
        </CardContent>
      </Card>
      <SelectorDialog
        open={false}
        popperOpen={newSubjectPopperOpen}
        onPopperClose={() => {setNewSubjectPopperOpen(false); setRequestFetchData(requestFetchData+1);}}
      />
    </div>
  );
}

export default Subjects;

