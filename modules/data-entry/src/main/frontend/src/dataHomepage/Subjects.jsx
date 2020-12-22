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
import Subject from "../questionnaire/Subject.jsx";
import SubjectView from "./SubjectView.jsx";
import { getHierarchy, getSubjectIdFromPath } from "../questionnaire/Subject.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

import { Button, Grid, Typography, withStyles } from "@material-ui/core";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
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

  // When a new subject is added, state will be updated and trigger a livetable refresh
  const [ requestFetchData, setRequestFetchData ] = useState(0);
  // subject types configured on the system
  let [ subjectTypes, setSubjectTypes ] = React.useState([]);
  let [ columns, setColumns ] = React.useState(tableColumns);

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

  // get subject types configured on the system
  if (subjectTypes.length === 0) {
    fetch('/query?query=' + encodeURIComponent(`select * from [lfs:SubjectType] as n WHERE n.'jcr:primaryType'='lfs:SubjectType' order by n.'lfs:defaultOrder'`))
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

  return (
    <Grid container direction="column" spacing={4}>
      <Grid item className={classes.dashboardEntry}>
         <Typography variant="h2">Subjects</Typography>
      </Grid>
      <Grid item>
        <SubjectView
          expanded
          disableHeader
          title=""
          columns={columns}
        />
      </Grid>
    </Grid>
  );
}

export default withStyles(QuestionnaireStyle)(Subjects);
