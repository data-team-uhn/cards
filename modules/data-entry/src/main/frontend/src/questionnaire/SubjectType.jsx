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
import PropTypes from "prop-types";
import moment from "moment";

import {
  CircularProgress,
  Grid,
  Typography
} from "@material-ui/core";

import LiveTable from "../dataHomepage/LiveTable.jsx";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";

/**
 * Component that displays a SubjectType.
 *
 * @example
 * <SubjectType id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a SubjectType; this is the JCR node name
 */
function SubjectType (props) {
  let { id } = props;
  // This holds the full SubjectType JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  // Column configuration for the LiveTables
  const columns = [
    {
      "key": "identifier",
      "label": "Identifier",
      "format": "string",
      "link": "dashboard+path",
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

  // Fetch the subject type as JSON from the server.
  // The response will contain metadata, such as authorship and versioning information.
  // Once the data arrives from the server, it will be stored in the `data` state variable.
  let fetchData = () => {
    fetch(`/SubjectTypes/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleResponse)
      .catch(handleError);
  };

  // Callback method for the `fetchData` method, invoked when the data successfully arrived from the server.
  let handleResponse = (json) => {
    setData(json);
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
    setData([]);  // Prevent an infinite loop if data was not set
  };

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    fetchData();
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  // If an error was returned, do not display any information, just report the error
  if (error) {
    return (
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error retrieving details about this subject type: {error.status} {error.statusText ? error.statusText : error.toString()}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  const customUrl='/Subjects.paginate?fieldname=type&fieldvalue='
        + encodeURIComponent(data['jcr:uuid']);

  return (
    <div>
      <Grid container direction="column" spacing={4} alignItems="stretch" justify="space-between" wrap="nowrap">
        <Grid item>
          {
              <Typography variant="h2">Subject Type: {data && data.identifier ? data.identifier : id}
                <DeleteButton
                  entryPath={data ? data["@path"] : "/SubjectTypes/" + id}
                  entryName={"Subject Type: " + (data && data.identifier ? data.identifier : id)}
                  entryType={"Subject Type"}
                  warning={data ? data["@referenced"] : false}
                  shouldGoBack={true}
                  buttonClass={classes.deleteButtonRight}
                />
              </Typography>
          }
          {
            data && data['jcr:createdBy'] && data['jcr:created'] ?
            <Typography variant="overline">Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
            : ""
          }
        </Grid>
        <Grid item>
          <Typography variant="h4">Subjects of type {data && (data.identifier || id)} </Typography>
          <LiveTable
            columns={columns}
            customUrl={customUrl}
            defaultLimit={10}
            actions={actions}
            entryType={(data && (data.identifier || id))}
            />
        </Grid>
      </Grid>
    </div>
  );
};

SubjectType.propTypes = {
  id: PropTypes.string
}

export default SubjectType;
