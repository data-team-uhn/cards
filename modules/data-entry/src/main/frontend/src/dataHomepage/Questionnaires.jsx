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
import Questionnaire from "../questionnaire/Questionnaire.jsx";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle";
import NewQuestionnaireDialog from "./NewQuestionnaireDialog.jsx";
import { Card, CardHeader, Button, CardContent, withStyles } from "@material-ui/core";


function Questionnaires(props) {
  let [ forms, setForms ] = useState({});
  let [ uuid, setUuid] = useState("")
  const { match, classes } = props;
  const entry = /Questionnaires\/(.+)/.exec(location.pathname);
  
  let getFormCount = (id) => {
    // Get uuid of questionnaire
    fetch(`/Questionnaires/${id}.deep.json`)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((response) => { setUuid(response['jcr:uuid']) });

    // Find all forms with that questionnaire uuid
    fetch('/query?query=' + encodeURIComponent(`select * from [lfs:Form] as n WHERE n.'questionnaire'='${uuid}'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => { parseResult(json, id); });
   }

   // Count the number of returned forms
   let parseResult = (forms, id) => {
    let filteredForms = Object.values(forms['rows']).length;
    setForms(forms.push({ id, filteredForms }));
   }

  if (entry) {
    return <Questionnaire id={entry[1]} key={location.pathname}/>;
  }

  if (!forms) {
    getFormCount(columns.title);
    columns.push({
      "key": `${forms[columns.title]}`,
      "label": "Forms",
      "format": "string",
    });
  }
  
  let columns = [
    {
      "key": "title",
      "label": "Title",
      "format": "string",
      "link": "dashboard+path",
      "admin": true,
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
  return (
    <Card>
       <CardHeader
        color={"warning"/* Does nothing */}
        title={
          <Button className={classes.cardHeaderButton}>
            Questionnaires
          </Button>
        }
        action={
          <NewQuestionnaireDialog presetpath={true}>
          </NewQuestionnaireDialog>
        }
        classes={{
          action: classes.newFormButtonHeader
        }}
      />
      <CardContent>
        <LiveTable columns={columns} />
      </CardContent>
    </Card>
  );
}

export default withStyles(QuestionnaireStyle)(Questionnaires);