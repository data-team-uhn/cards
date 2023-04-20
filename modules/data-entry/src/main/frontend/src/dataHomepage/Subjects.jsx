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
import { getHierarchy, getSubjectIdFromPath } from "../questionnaire/SubjectIdentifier.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

import { Grid } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";

function Subjects(props) {
  const { classes } = props;
  const entry = getSubjectIdFromPath(location.pathname);

  // Clear the page name overwriting if moving from a specific Subject to the Subjects page
  const pageNameWriter = usePageNameWriterContext();
  useEffect(() => {
    if (!entry) {
      pageNameWriter("");
    }
  }, [entry]);

  if (entry) {
    return <Subject id={entry} contentOffset={props.contentOffset} key={entry} />;
  }

  const columns = [
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
      "format": "date:yyyy-MM-dd HH:mm",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
  ];

  return (
    <Grid container className={classes.dashboardContainer}>
      <Grid item className={classes.dashboardEntry} xs={12}>
        <SubjectView
          expanded
          columns={columns}
        />
      </Grid>
    </Grid>
  );
}

export default withStyles(QuestionnaireStyle)(Subjects);
