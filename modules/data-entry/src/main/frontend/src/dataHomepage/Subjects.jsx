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
import { NewSubjectDialog } from "../questionnaire/SubjectSelector.jsx";
import { EntityIdentifier } from "../themePage/EntityIdentifier.jsx";

import { Button, Card, CardContent, CardHeader, Grid, Link, withStyles, ListItemText, Tooltip, Fab } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import DeleteButton from "./DeleteButton.jsx";

function Subjects(props) {

  const { classes } = props;
  // fix issue with classes

  let [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  // When a new subject is added, state will be updated and trigger a livetable refresh
  const [ requestFetchData, setRequestFetchData ] = useState(0);

  const columns = [
    {
      "key": "subject/identifier",
      "label": "Identifier",
      "format": EntityIdentifier,
    },
    {
      "key": "jcr:created",
      "label": "Created on",
      "format": "date:YYYY-MM-DD HH:mm",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
    {
      "key": "type/label",
      "label": "Type",
      "format": "string",
    },
  ]
  const actions = [
    DeleteButton
  ]

  const entry = /Subjects\/(.+)/.exec(location.pathname);
  if (entry) {
    return <Subject id={entry[1]} contentOffset={props.contentOffset} />;
  }

  // import the function

  return (
    <div>
      <Card>
        <CardHeader
          title={
            <Button className={classes.cardHeaderButton}>
              Subjects
            </Button>
          }
          action={
            <div className={classes.newFormButtonWrapper}>
              <Tooltip aria-label="add" title="New Subject">
                <Fab
                  color="primary"
                  aria-label="add"
                  onClick={() => {setNewSubjectPopperOpen(true)}}
                >
                  <AddIcon />
                </Fab>
              </Tooltip>
            </div>
          }
        />
        <CardContent>
          <LiveTable
            columns={columns}
            updateData={requestFetchData}
            actions={actions}
            entryType={"Subject"}
          />
        </CardContent>
      </Card>
      <NewSubjectDialog
        onClose={() => { setNewSubjectPopperOpen(false); setRequestFetchData(requestFetchData+1);}}
        onSubmit={() => { setNewSubjectPopperOpen(false); setRequestFetchData(requestFetchData+1);}}
        open={newSubjectPopperOpen}
        />
    </div>
  );
}

export default withStyles(QuestionnaireStyle)(Subjects);

