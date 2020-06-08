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

/**
 * Component that displays a Subject.
 *
 * @example
 * <SubjectDirectory id="ae137c46-c22e-4bf1-8238-953e6315cffc" title="Tumors"/>
 *
 * @param {string} id the identifier of a subjectType; this is the JCR node name
 * @param {string} title the title of the displayed title
 */

 // TODO: would the user inupt the uuid or the label

import React, { useState } from "react";
import LiveTable from "../dataHomepage/LiveTable.jsx";

import { Button, Card, CardContent, CardHeader, withStyles } from "@material-ui/core";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

function SubjectDirectory(props) {

  const { classes, id, title } = props;

  const columns = [
    {
      "key": "identifier",
      "label": "Identifier",
      "format": "string",
      "link": "dashboard+field:@path",
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
    <div>
      <Card>
        <CardHeader
          title={
            <Button className={classes.cardHeaderButton}>
              {title}
            </Button>
          }
        />
        <CardContent>
          <LiveTable
            columns={columns}
            customUrl={'/Subjects.paginate?fieldname=type&fieldvalue='+ encodeURIComponent(id)}
            defaultLimit={10}
            />
        </CardContent>
      </Card>
    </div>
  );
}

export default withStyles(QuestionnaireStyle)(SubjectDirectory);

