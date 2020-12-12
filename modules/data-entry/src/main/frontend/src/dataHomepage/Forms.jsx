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
import Form from "../questionnaire/Form.jsx";
import { getHierarchy } from "../questionnaire/Subject.jsx";

import { Button, Card, CardContent, CardHeader, Grid, Link, withStyles } from "@material-ui/core";
import questionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import NewFormDialog from "./NewFormDialog.jsx";
import DeleteButton from "./DeleteButton.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";

function Forms(props) {
  const { match, location, classes } = props;

  const [ title, setTitle ] = useState("Forms");
  const [ titleFetchSent, setFetchStatus ] = useState(false);
  const [ questionnairePath, setQuestionnairePath ] = useState(undefined);
  const [ questionnaireDetails, setQuestionnaireDetails ] = useState();
  const questionnaireID = /questionnaire=([^&]+)/.exec(location.search);
  const pageNameWriter = usePageNameWriterContext();

  const entry = /Forms\/(.+)/.exec(location.pathname);

  // When moving from a specific form to the "Forms" page, ensure that the title properly changes
  useEffect(() => {
    if (!entry) {
      pageNameWriter("");
    }
  }, [entry]);

  if (entry) {
    return <Form id={entry[1]} key={location.pathname}/>;
  }

  // Convert from a questionnaire ID to the title of the form we're editing
  let getQuestionnaireTitle = (id) => {
    setFetchStatus(true);
    fetch('/query?query=' + encodeURIComponent(`select * from [lfs:Questionnaire] as n WHERE n.'jcr:uuid'='${id}'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setTitle(json["rows"][0]["title"]);
        setQuestionnairePath(json["rows"][0]["@path"]);
        setQuestionnaireDetails(json["rows"][0]);
      });
  }

  let customUrl = undefined;
  // Formulate a custom pagination request if a questionnaire ID is given
  if (questionnaireID) {
    customUrl='/Forms.paginate?fieldname=questionnaire&fieldvalue='
            + encodeURIComponent(questionnaireID[1]);

    // Also fetch the title if we haven't yet
    if (!titleFetchSent) {
      getQuestionnaireTitle(questionnaireID[1]);
    }
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
      "format": "date:YYYY-MM-DD HH:mm",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
  ]
  const actions = [
    DeleteButton
  ]

  return (
    <Card>
      <CardHeader
        color={"warning"/* Does nothing */}
        title={
          <Button className={classes.cardHeaderButton}>
            {title}
          </Button>
        }
        action={
        <div className={classes.mainPageAction}>
          <NewFormDialog presetPath={questionnairePath}>
            New form
          </NewFormDialog>
        </div>
        }
        classes={{
          action: classes.newFormButtonHeader
        }}
      />
      <CardContent>
        <LiveTable
          columns={columns}
          customUrl={customUrl}
          filters
          joinChildren="lfs:Answer"
          actions={actions}
          entryType="Form"
          />
      </CardContent>
    </Card>
  );
}

export default withStyles(questionnaireStyle)(Forms);
