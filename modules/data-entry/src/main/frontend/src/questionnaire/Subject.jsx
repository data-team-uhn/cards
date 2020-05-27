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

import React, { useState, useEffect } from "react";
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
import Form from "./Form";
import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";
import FormEntry, { ENTRY_TYPES } from "./FormEntry";
import { FormProvider } from "./FormContext";



/**
 * Component that displays a Subject.
 *
 * @example
 * <Subject id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a subject; this is the JCR node name
 */
function Subject (props) {
  let { id, classes } = props;
  // This holds the full subject JSON, once it is received from the server
  let [ data, setData ] = useState();
  // This holds the full form JSONs, once it is received from the server
  let [ formData, setFormData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  let [ formIDs, setFormIDs ] = useState("");

  // Column configuration for the LiveTables
  const columns = [
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

  const requests = [];

  // Callback method for the `fetchFormData` method, invoked when the request failed.
  let handleFormError = (response) => {
    setError(response);
    setFormData([]);  // Prevent an infinite loop if data was not set
  };

  // Callback to receive formIDs
  let handleFormIds = (childData) => {
    console.log(childData);
    childData.map((entry) => {
      console.log(entry["@name"]);
      requests.push(
        fetch(`/Forms/${entry["@name"]}.deep.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => requests.push(json))
        .catch(handleFormError)
      )
    });
    
    // update formData state once data for all forms have been fetched
    Promise.all(requests).then(() => {
      console.log(requests)
      setFormData(requests.filter((data) => data["jcr:primaryType"] == "lfs:Form"));
      console.log(formData);
    });
  };

  const customUrl='/Forms.paginate?fieldname=subject&fieldvalue='
        + encodeURIComponent(data['jcr:uuid']);

  //todo: make the slice a variable
  let makeRow = (entry) => {
    return (
        <Grid container {...FORM_ENTRY_CONTAINER_PROPS} >
          <FormProvider>
            {
              Object.entries(entry.questionnaire)
                .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
                .slice(0, 4)
                .map(([key, entryDefinition]) => <FormEntry key={key} entryDefinition={entryDefinition} path={"."} depth={0} existingAnswers={entry} keyProp={key} classes={classes} defaultDisplay={false}></FormEntry>)
            }
          </FormProvider>
        </Grid>
    );
  };

  
  // todo later:  get subjects related to current subject
  //              if subject has related subjects, rerender a livetable with formPreview=true for each related subject
  //              make subject recursive somehow?
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
            onSendFormIds={handleFormIds}
            />
        </Grid>
        <Grid item>
          {formData ? 
          (
            formData.map((makeRow))
          ) 
          : ""}
        </Grid>
      </Grid>
    </div>
  );
};

Subject.propTypes = {
  id: PropTypes.string
}

export default Subject;
