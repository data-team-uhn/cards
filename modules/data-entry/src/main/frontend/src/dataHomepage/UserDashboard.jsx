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

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

import { Button, Card, CardContent, CardHeader, Grid, Link, withStyles } from "@material-ui/core";
import NewFormDialog from "./NewFormDialog.jsx";

// Component that renders the user's dashboard, with one LiveTable per questionnaire
// visible by the user. Each LiveTable contains all forms that use the given
// questionnaire. 
function UserDashboard(props) {
  const { classes } = props;
  // Store information about each questionnaire and whether or not we have
  // initialized
  let [questionnaires, setQuestionnaires] = useState([]);
  let [initialized, setInitialized] = useState(false);

  // Column configuration for the LiveTables
  const columns = [
    {
      "key": "jcr:uuid",
      "label": "Identifier",
      "format": "string",
      "link": "dashboard+path",
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
      "link": "dashboard+field:subject/@path",
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

  // Obtain information about the questionnaires available to the user
  let initialize = () => {
    setInitialized(true);

    // Fetch the questionnaires
    fetch("/query?query=select * from [lfs:Questionnaire]")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(setQuestionnaires);
  }

  // If no forms can be obtained, we do not want to keep on re-obtaining questionnaires
  if (!initialized) {
    initialize();
  }

  return (
    <React.Fragment>
      <Grid container spacing={3}>
        {questionnaires.map( (questionnaire) => {
          const customUrl='/Forms.paginate?fieldname=questionnaire&fieldvalue='
            + encodeURIComponent(questionnaire["jcr:uuid"]);
          return(
            <Grid item lg={12} xl={6} key={questionnaire["jcr:uuid"]}>
              <Card>
                <CardHeader
                  title={
                    <Link href={`/content.html/Forms?questionnaire=${questionnaire["jcr:uuid"]}`}>
                      <Button className={classes.cardHeaderButton}>
                        {questionnaire["title"]}
                      </Button>
                    </Link>
                  }
                  action={
                    <NewFormDialog presetPath={questionnaire["@path"]}>
                      New form
                    </NewFormDialog>
                  }
                  classes={{
                    action: classes.newFormButtonHeader
                  }}
                />
                <CardContent>
                  <LiveTable
                    columns={columns}
                    customUrl={customUrl}
                    defaultLimit={10}
                    joinChildren="lfs:Answer"
                    />
                </CardContent>
              </Card>
            </Grid>
          )
        })}
      </Grid>
    </React.Fragment>
  );
}

export default withStyles(QuestionnaireStyle)(UserDashboard);
