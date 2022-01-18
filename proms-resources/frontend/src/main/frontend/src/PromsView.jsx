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

import { Grid, Typography, withStyles } from "@material-ui/core";
import questionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import FormView from "./FormView.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";

function PromsView(props) {
  const { data, classes } = props;

  const [ columns, setColumns ] = useState();
  const [ questionnaireId, setQuestionnaireId ] = useState();

  if (data) {
    fetch(data + ".deep.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        json["view"] && setColumns(JSON.parse(json["view"]));

        if (json["questionnaire"]) {
          fetch(json["questionnaire"] + ".deep.json")
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then((json) => {
            setQuestionnairePath(json["@path"]);
          });
        }
      });
  }

  return (
    <Grid container direction="column" spacing={4}>
      <Grid item className={classes.dashboardEntry}>
        <FormView
          expanded
          columns={columns}
          questionnaire={questionnaireId}
        />
      </Grid>
    </Grid>
  );
}

export default withStyles(questionnaireStyle)(PromsView);
