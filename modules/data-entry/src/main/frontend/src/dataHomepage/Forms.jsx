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

import { Button, Grid, Link, withStyles } from "@material-ui/core";
import { Card, CardHeader, CardBody } from "MaterialDashboardReact";
import questionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function Forms(props) {
  const { match, location, classes } = props;
  const [ title, setTitle ] = useState("Forms");
  const [ titleFetchSent, setFetchStatus ] = useState(false);
  const questionnaireID = /questionnaire=([^&]+)/.exec(location.search);

  // Convert from a questionnaire ID to the title of the form we're editing
  let getQuestionnaireTitle = (id) => {
    setFetchStatus(true);
    fetch('/query?query=' + encodeURIComponent(`select * from [lfs:Questionnaire] as n WHERE n.'jcr:uuid'='${id}'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {setTitle(json[0]["title"])});
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
      "key": "jcr:uuid",
      "label": "Identifier",
      "format": "string",
      "link": "path",
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
      "link": "field:subject/@path",
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
      <CardHeader color="warning">
        <Button className={classes.cardHeaderButton}>
          {title}
        </Button>
        <Button variant="contained" color="primary" className={classes.newFormButton}>
          New form
        </Button>
      </CardHeader>
      <CardBody>
        <LiveTable columns={columns} customUrl={customUrl}/>
      </CardBody>
    </Card>
  );
}

export default withStyles(questionnaireStyle)(Forms);