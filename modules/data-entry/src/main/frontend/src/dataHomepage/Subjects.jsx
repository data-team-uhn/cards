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
import React, { useEffect, useState } from "react";
import LiveTable from "./LiveTable.jsx";
import Subject from "../questionnaire/Subject.jsx";
import { getHierarchy, getSubjectIdFromPath } from "../questionnaire/Subject.jsx";
import { NewSubjectDialog } from "../questionnaire/SubjectSelector.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

import { Button, Card, CardContent, CardHeader, Grid, Link, withStyles, ListItemText, Tooltip, Fab } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import DeleteButton from "./DeleteButton.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";

const tableColumns = [
    {
      "key": "identifier",
      "label": "Identifier",
      "format": getEntityIdentifier,
      "link": "dashboard+path",
    },
    {
      "key": "type/label",
      "label": "Type",
      "format": "string",
    },
    {
      "key": "",
      "label": "Parents",
      "format": (row) => (row['parents'] ? getHierarchy(row['parents']) : ''),
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
  ];

function Subjects(props) {

  const { classes } = props;
  // fix issue with classes

  let [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  // When a new subject is added, state will be updated and trigger a livetable refresh
  const [ requestFetchData, setRequestFetchData ] = useState(0);
  // subject types configured on the system
  let [ subjectTypes, setSubjectTypes ] = React.useState([]);
  let [ columns, setColumns ] = React.useState(tableColumns);

  // get subject types configured on the system
  if (subjectTypes.length === 0) {
    fetch('/query?query=' + encodeURIComponent(`select * from [lfs:SubjectType] as n WHERE n.'jcr:primaryType'='lfs:SubjectType'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let optionTypes = Array.from(json["rows"]);
        setSubjectTypes(optionTypes);
        if (optionTypes.length <= 1) {
          let result = columns.slice();
          result.splice(1, 2);
          setColumns(result);
        }
      });
  }

  const actions = [
    DeleteButton
  ]

  const entry = getSubjectIdFromPath(location.pathname);

  // Clear the page name overwriting if moving from a specific Subject to the Subjects page
  const pageNameWriter = usePageNameWriterContext();
  useEffect(() => {
    if (!entry) {
      pageNameWriter("");
    }
  }, [entry]);

  if (entry) {
    return <Subject id={entry} contentOffset={props.contentOffset} />;
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
          <div className={classes.mainPageAction}>
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

