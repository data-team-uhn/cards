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
import Form from "../questionnaire/Form.jsx";
import { getHierarchy } from "../questionnaire/SubjectIdentifier.jsx";

import { Grid } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import questionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import FormView from "./FormView.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";

function Forms(props) {
  const { location, classes } = props;
  const questionnaire = /questionnaire=([^&]+)/.exec(location.search)?.[1];
  const pageNameWriter = usePageNameWriterContext();

  const entry = /Forms\/([^.\/]+)/.exec(location.pathname);

  // When moving from a specific form to the "Forms" page, ensure that the title properly changes
  useEffect(() => {
    if (!entry) {
      pageNameWriter("");
    }
  }, [entry]);

  if (entry) {
    return <Form id={entry[1]} key={location.pathname} contentOffset={props.contentOffset} />;
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
      "format": (row) => (row.subject ? getHierarchy(row.subject) : ''),
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
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
  ]

  return (
    <Grid container className={classes.dashboardContainer}>
      <Grid item className={classes.dashboardEntry} xs={12}>
        <FormView
          expanded
          columns={columns}
          questionnaire={questionnaire}
        />
      </Grid>
    </Grid>
  );
}

export default withStyles(questionnaireStyle)(Forms);
