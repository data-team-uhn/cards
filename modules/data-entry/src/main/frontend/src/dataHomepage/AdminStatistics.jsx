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
import React from "react";
import LiveTable from "./LiveTable.jsx";

import { Button, Card, CardContent, CardHeader, withStyles } from "@material-ui/core";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function AdminStatistics(props) {
  const { classes } = props;

  const columns = [
    {
      "key": "label",
      "label": "Label",
      "format": "string",
      "link": "dashboard+field:@path",
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
        title={
          <Button className={classes.cardHeaderButton}>
            Subject Types
          </Button>
        }
      />
      <CardContent>
        <LiveTable columns={columns} />
      </CardContent>
    </Card>
  );
}

export default withStyles(QuestionnaireStyle)(AdminStatistics);
