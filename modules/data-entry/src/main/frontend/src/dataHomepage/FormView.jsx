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

import { Button, Card, CardContent, CardHeader, Tab, Tabs, withStyles } from "@material-ui/core";
import DeleteButton from "./DeleteButton.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

function FormView(props) {
  const { classes } = props;
  const [ activeTab, setActiveTab ] = useState(0);

  // Column configuration for the LiveTables
  const columns = [
    {
      "key": "@name",
      "label": "Identifier",
      "format": getEntityIdentifier,
      "link": "dashboard+path",
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
  const tabs = ["Completed", "Draft"];

  return (
    <Card>
      <CardHeader
        title={
          <Button className={classes.cardHeaderButton}>
            Forms
          </Button>
        }
      />
      <Tabs value={activeTab} onChange={(event, value) => setActiveTab(value)}>
        {tabs.map(value => {
          return <Tab label={value} />;
        })}
      </Tabs>
      <CardContent>
        <LiveTable
          columns={columns}
          customUrl={`/Forms.paginate?descending=true${activeTab == tabs.indexOf("Draft") ? '&fieldname=statusFlags&fieldvalue=INCOMPLETE' : ''}`}
          defaultLimit={10}
          filters
          entryType={"Form"}
          actions={actions}
        />
      </CardContent>
    </Card>
  );
}

export default withStyles(QuestionnaireStyle)(FormView);
