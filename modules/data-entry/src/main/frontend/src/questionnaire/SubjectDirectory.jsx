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
 * <SubjectDirectory id="ae137c46-c22e-4bf1-8238-953e6315cffc/>
 *
 * @param {string} id the identifier of a subjectType; this is the JCR node name
 */

 // TODO: would the user inupt the uuid or the label

import React, { useState } from "react";
import LiveTable from "../dataHomepage/LiveTable.jsx";

import { Button, Card, CardContent, CardHeader, Grid, Link, withStyles, Typography, Tooltip, Fab } from "@material-ui/core";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import MaterialTable from "material-table";

/***
 * Create a URL that checks for the existance of a subject
 */
let createQueryURL = (query, type) => {
    let url = new URL("/query", window.location.origin);
    url.searchParams.set("query", `SELECT * FROM [${type}] as n` + query);
    return url;
  }

function SubjectDirectory(props) {

  const { classes, id } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // This holds the subjectID
  let [subjectID, setSubjectID ] = useState();

  // const columns = [
  //   {
  //     "key": "identifier",
  //     "label": "Identifier",
  //     "format": "string",
  //     "link": "dashboard+field:@path",
  //   },
  //   {
  //     "key": "jcr:createdBy",
  //     "label": "Created by",
  //     "format": "string",
  //   },
  //   {
  //     "key": "jcr:created",
  //     "label": "Created on",
  //     "format": "date:YYYY-MM-DD HH:mm",
  //   },
  // ]

  const COLUMNS = [
    { title: 'Identifier', field: 'identifier' },
    { title: 'Created by', field: 'jcr:createdBy'},
    { title: 'Created on', field: 'jcr:created'},
  ];

  // check if n.label = inputted label
  let fetchData = () => {
    let url = createQueryURL(` WHERE n.label='${id}'`, "lfs:SubjectType");
    fetch(url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => {
        console.log(response);
        // subjectID = uuid of desired subjecttype
        setSubjectID(response["rows"][0]);
      })
      .catch(handleError);
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.statusText ? response.statusText : response.toString());
    setSubjectID([]);
    setSubjects([]);  // Prevent an infinite loop if data was not set
  };

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

  // If no forms can be obtained, we do not want to keep on re-obtaining questionnaires
  if (!subjectID) {
    fetchData();
  }

  return (
    <div>
      <Card>
        <CardHeader
          title={
            <Button className={classes.cardHeaderButton}>
              SubjectType
            </Button>
          }
        />
        <CardContent>
          {/* on name click.. */}
          {
            subjectID &&
            <MaterialTable
              columns={COLUMNS}
              data={query => {
                let url = createQueryURL(` WHERE n.type='${subjectID?.["jcr:uuid"]}'`, "lfs:Subject");
                url.searchParams.set("limit", query.pageSize);
                url.searchParams.set("offset", query.page*query.pageSize);
                return fetch(url)
                  .then(response => response.json())
                  .then(result => {
                    return {
                      data: result["rows"],
                      page: Math.trunc(result["offset"]/result["limit"]),
                      totalCount: result["totalrows"],
                    }}
                  )
                }
              }
              options={{
                // search: true,
                // addRowPosition: 'first'
                // add styling!! look at groupsmanager.jsx
              }}
            />
          }

        </CardContent>
      </Card>
    </div>
  );
}

export default withStyles(QuestionnaireStyle)(SubjectDirectory);

