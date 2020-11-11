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

import { Button, Card, CardContent, CardHeader, Grid, Link, Typography, withStyles } from "@material-ui/core";
import NewFormDialog from "./NewFormDialog.jsx";
import DeleteButton from "./DeleteButton.jsx";
import { MatchIdentifier } from "../themePage/QuickSearchResults.jsx";

// Component that renders the user's dashboard, with one LiveTable per questionnaire
// visible by the user. Each LiveTable contains all forms that use the given
// questionnaire.
function UserDashboard(props) {
  const { classes } = props;
  // Store information about each questionnaire and whether or not we have
  // initialized
  let [questionnaires, setQuestionnaires] = useState([]);
  let [initialized, setInitialized] = useState(false);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  // Column configuration for the LiveTables
  const columns = [
    {
      "key": "",
      "label": "Identifier",
      "format": MatchIdentifier,
    },
    {
      "key": "questionnaire/title",
      "label": "Questionnaire",
      "format": "string",
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
  const actions = [
    DeleteButton
  ]

  // Obtain information about the questionnaires available to the user
  let initialize = () => {
    setInitialized(true);

    // Fetch the questionnaires
    fetch("/query?query=select * from [lfs:Questionnaire]")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => {
        if (response.totalrows == 0) {
          setError("Access to data is pending the approval of your account");
        }
        setQuestionnaires(response["rows"]);
      })
      .catch(handleError);
  }

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.statusText ? response.statusText : response.toString());
    setQuestionnaires([]);  // Prevent an infinite loop if data was not set
  };

  // If no forms can be obtained, we do not want to keep on re-obtaining questionnaires
  if (!initialized) {
    initialize();
  }

  // If an error was returned, report the error
  if (error) {
    return (
      <Card>
        <CardHeader title="Error"/>
        <CardContent>
          <Typography>{error}</Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <React.Fragment>
      <Grid container spacing={3}>
        <Grid item lg={12} xl={6}>
          <Card>
            <CardHeader
              title={
                  <Button className={classes.cardHeaderButton}>
                    Incomplete Forms
                  </Button>
              }
            />
            <CardContent>
              <LiveTable
                columns={columns}
                customUrl='/Forms.paginate?fieldname=statusFlags&fieldvalue=INCOMPLETE'
                defaultLimit={10}
                joinChildren="lfs:Answer"
                filters
                entryType={"Form"}
                actions={actions}
              />
            </CardContent>
          </Card>
        </Grid>
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
                    questionnaire={questionnaire["@path"]}
                    entryType={questionnaire["title"]}
                    filters
                    actions={actions}
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
