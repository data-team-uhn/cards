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
  Typography,
  TableRow,
  TableCell
} from "@material-ui/core";

import LiveTable from "../dataHomepage/LiveTable.jsx";

/**
 * Component that displays a Subject.
 *
 * @example
 * <Subject id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a subject; this is the JCR node name
 */
function Subject (props) {
  let { id } = props;
  // This holds the full subject JSON, once it is received from the server
  let [ data, setData ] = useState();
  // This holds the full form JSONs, once it is received from the server
  let [ formData, setFormData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  let [ formID, setFormID ] = useState("");

  // Column configuration for the LiveTables
  const columns = [
    // {
    //   "key": "@name",
    //   "label": "Identifier",
    //   "format": "string",
    //   "link": "dashboard+path",
    // },
    // {
    //   "key": "questionnaire/title",
    //   "label": "Questionnaire",
    //   "format": "string",
    //   "link": "dashboard+field:questionnaire/@path",
    // },
    // {
    //   "key": "jcr:createdBy",
    //   "label": "Created by",
    //   "format": "string",
    // },
    // {
    //   "key": "jcr:created",
    //   "label": "Created on",
    //   "format": "date:YYYY-MM-DD HH:mm",
    // },
    {
      "key": "questionnaire/@name",
      "label": "Questionnaire",
      "format": "string",
      "link": "dashboard+path",  
    },
  ]

  // Fetch the subject's data as JSON from the server.
  // The data will contain the subject metadata,
  // such as authorship and versioning information.
  // Once the data arrives from the server, it will be stored in the `data` state variable.
  let fetchData = () => {
    fetch(`/Subjects/${id}.deep.json`)
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

  // Fetch each form's data as JSON from the server.
  // todo: get form id from each form associated with subject (this data gets fetched in livetable - send to parent somehow?)
  // load first n (by default 2) questions with their answers
  let fetchFormData = () => {
    //fetch data the same way as forms do
    fetch(`/Forms/${id}.deep.json`)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then(handleFormResponse)
    .catch(handleFormError);
    // to display first n question, 'see more' links to full form
  }

   // Callback method for the `fetchFormData` method, invoked when the data successfully arrived from the server.
   let handleFormResponse = (json) => {
    setFormData(json);
  };

  // Callback method for the `fetchFormData` method, invoked when the request failed.
  let handleFormError = (response) => {
    setError(response);
    setFormData([]);  // Prevent an infinite loop if data was not set
  };

  // Callback to receive formID
  let getID = (childData) => {
    console.log(childData);
    setFormID(childData);
  };

  // If an error was returned, do not display a subject at all, but report the error
  if (error) {
    return (
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining subject data: {error.status} {error.statusText}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  const customUrl='/Forms.paginate?fieldname=subject&fieldvalue='
        + encodeURIComponent(data['jcr:uuid']);

  
  // todo later: get subjects related to current subject, repeat everything for each related subject - maybe put rendered grid with livetable in its own function
  // todo later: add 'subject type' button

  return (
    <div>
      <Grid container direction="column" spacing={4} alignItems="stretch" justify="space-between" wrap="nowrap">
        <Grid item>
          {
            data && data.identifier ?
              <Typography variant="h2">SubjectType {data.identifier}</Typography>
            : <Typography variant="h2">SubjectType {id}</Typography>
          }
          {
            data && data['jcr:createdBy'] && data['jcr:created'] ?
            <Typography variant="overline">Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
            : ""
          }
        </Grid>
        <Grid item>
          {/* <Typography variant="h4">Forms involving {data && (data.identifier || id)} </Typography> */}
          <LiveTable
            columns={columns}
            customUrl={customUrl}
            defaultLimit={10}
            formPreview={true}
            getID={getID}
            />
        </Grid>
      </Grid>
    </div>
  );
};

Subject.propTypes = {
  id: PropTypes.string
}

export default Subject;
