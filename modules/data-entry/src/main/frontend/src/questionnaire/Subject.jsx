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

import React, { useState, useEffect, useRef } from "react";
import PropTypes from "prop-types";
import moment from "moment";
import { Link } from 'react-router-dom';

import {
  CircularProgress,
  Grid,
  Typography,
  Table,
  TableBody,
  TableRow,
  TableCell,
  Card,
  CardHeader,
  CardContent,
  CardActions,
  withStyles
} from "@material-ui/core";

// import { Paper, Table, TableHead, TableBody, TableRow, TableCell, TablePagination } from "@material-ui/core";
// import { Card, CardHeader, CardContent, CardActions, Chip, Typography, Button, withStyles } from "@material-ui/core";
// import { Link } from 'react-router-dom';

import LiveTable from "../dataHomepage/LiveTable.jsx";


const QUESTION_TYPES = ["lfs:Question"];
const SECTION_TYPES = ["lfs:Section"];
const ENTRY_TYPES = QUESTION_TYPES.concat(SECTION_TYPES);


// FIX: not rendering boolean/sections correctly
let displayQuestion = (questionDefinition, path, existingAnswer, key, classes) => {

  const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
    .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
      && value["question"]["jcr:uuid"] === questionDefinition["jcr:uuid"]);

  // question title, to be used when 'previewing' the form
  const questionTitle = questionDefinition["text"];

  let content = `${questionTitle}: null`

  if (existingQuestionAnswer && existingQuestionAnswer[1]["value"]) {
    content = `${questionTitle}: ${existingQuestionAnswer[1]["value"]}`
  }

  // component will either render the default question display, or a list of questions/answers from the form (used for subjects)
  return (
    <TableRow key={key}>{content}</TableRow>
  );
};

function SubjectEntry(props) {
  let { classes, entryDefinition, path, depth, existingAnswers, keyProp } = props;
  if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
    return displayQuestion(entryDefinition, path, existingAnswers, keyProp, classes);
  }
  else return null;
}


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
  // table data: related forms
  const [tableData, setTableData] = useState();

  let [ formIDs, setFormIDs ] = useState("");

  const [fetchStatus, setFetchStatus] = useState(
    {
      "currentRequestNumber": -1,
      "currentFetch": false,
      "fetchError": false,
    }
  );

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

  // Callback method for the `fetchFormData` method, invoked when the request failed. // put this in handleError
  let handleFormError = (response) => {
    setError(response);
    setFormData([]);  // Prevent an infinite loop if data was not set
  };

  // Callback to receive formIDs
  // let handleFormIds = (childData) => {
  //   console.log(childData);
  //   childData.map((entry) => {
  //     console.log(entry["@name"]);
  //     requests.push(
  //       fetch(`/Forms/${entry["@name"]}.deep.json`)
  //       .then((response) => response.ok ? response.json() : Promise.reject(response))
  //       .then((json) => requests.push(json))
  //       .catch(handleFormError)
  //     )
  //   });
    
  //   // update formData state once data for all forms have been fetched
  //   Promise.all(requests).then(() => {
  //     console.log(requests)
  //     setFormData(requests.filter((data) => data["jcr:primaryType"] == "lfs:Form"));
  //     console.log(formData);
  //   });
  // };

  const customUrl='/Forms.paginate?fieldname=subject&fieldvalue='
        + encodeURIComponent(data['jcr:uuid']);

  //function to create a row
  //todo: make the slice a variable
  let makeRow = (entry) => {
    return (
        <TableRow key={entry["@path"]}>
            <TableCell colSpan={6}>
                <Table>
                {
                  Object.entries(entry.questionnaire)
                    .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
                    .slice(0, 4)
                    .map(([key, entryDefinition]) => <SubjectEntry key={key} entryDefinition={entryDefinition} path={"."} depth={0} existingAnswers={entry} keyProp={key} classes={classes}></SubjectEntry>)
                }
              </Table>
            </TableCell>
        </TableRow>
    );
  };
 
  
  // todo later:  get subjects related to current subject
  //              if subject has related subjects, rerender a livetable with formPreview=true for each related subject
  //              make subject recursive somehow?
  // todo later: add 'subject type' button

  // FETCHING THE FORMS RELATED TO SUBJECTS

  const urlBase = (
    customUrl ?
      new URL(customUrl, window.location.origin)
    :
      new URL(window.location.pathname.substring(window.location.pathname.lastIndexOf("/")) + ".paginate", window.location.origin)
  );

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Define the component's behavior

  let fetchSubjectData = () => {
    if (fetchStatus.currentFetch) {
      // TODO: abort previous request
    }

    let url = new URL(urlBase);

    console.log(url);

    url.searchParams.set("req", ++fetchStatus.currentRequestNumber);

    let currentFetch = fetch(url);
    setFetchStatus(Object.assign({}, fetchStatus, {
      "currentFetch": currentFetch,
      "fetchError": false,
    }));
    currentFetch.then((response) => response.ok ? response.json() : Promise.reject(response)).then(handleSubjectResponse).catch(handleSubjectError);
    // TODO: update the displayed URL with pagination details, so that we can share/reload at the same page
  };

  let handleSubjectResponse = (json) => {
    if (+json.req !== fetchStatus.currentRequestNumber) {
      // This is the response for an older request. Discard it, wait for the right one.
      return;
    }
    console.log(json.rows);
    setTableData(json.rows);
  };

  let handleSubjectError = (response) => {
    setFetchStatus(Object.assign({}, fetchStatus, {
      "currentFetch": false,
      "fetchError": (response.statusText ? response.statusText : response.toString()),
    }));
    setTableData();
  };


  // useEffect(() => {
  //   if (tableData) {
  //     const requests = [];

  //     console.log(tableData);
  //     tableData.map((entry) => {
  //       console.log(entry["@name"]);
  //       requests.push(
  //         fetch(`/Forms/${entry["@name"]}.deep.json`)
  //         .then((response) => response.ok ? response.json() : Promise.reject(response))
  //         .then((json) => requests.push(json))
  //         .catch(handleFormError)
  //       )
  //     });
      
  //     // update formData state once data for all forms have been fetched
  //     Promise.all(requests).then(() => {
  //       console.log(requests)
  //       setFormData(requests.filter((data) => data["jcr:primaryType"] == "lfs:Form"));
  //       console.log(formData);
  //     });
  //   }
  // }, [tableData]);

  // initialize fetch
  if (fetchStatus.currentRequestNumber == -1) {
    fetchSubjectData();
  }


  //pass entry 'name' --> get form data --> make list for that ONE from

  let getFormData = (formID) => {
    fetch(`/Forms/${formID}.deep.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => displayFormData(json))
        .catch(handleFormError)
  }

  let displayFormData = (data) => {
    console.log(data);
    // return (
    //       <Table>
    //       {
    //         Object.entries(entry.questionnaire)
    //           .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
    //           .slice(0, 4)
    //           .map(([key, entryDefinition]) => <SubjectEntry key={key} entryDefinition={entryDefinition} path={"."} depth={0} existingAnswers={entry} keyProp={key} classes={classes}></SubjectEntry>)
    //       }
    //     </Table>
    // );
  }

  // return (
  //   <div>
  //     <Grid container direction="column" spacing={4} alignItems="stretch" justify="space-between" wrap="nowrap">
  //       <Grid item>
  //         {
  //           data && data.identifier ?
  //             <Typography variant="h2">SubjectType {data.identifier}</Typography>
  //           : <Typography variant="h2">SubjectType {id}</Typography>
  //         }
  //         {
  //           data && data['jcr:createdBy'] && data['jcr:created'] ?
  //           <Typography variant="overline">Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
  //           : ""
  //         }
  //       </Grid>
  //       <Grid item>
  //         {/* <Typography variant="h4">Forms involving {data && (data.identifier || id)} </Typography> */}
  //         <LiveTable
  //           columns={columns}
  //           customUrl={customUrl}
  //           defaultLimit={10}
  //           formPreview={true}
  //           // onSendFormIds={handleFormIds}
  //           />
  //       </Grid>
  //       <Grid item>
  //         {/* <Table><TableBody>{formData ? (formData.map((makeRow))) : null}</TableBody></Table> */}
  //       </Grid>
  //     </Grid>
  //   </div>
  // );

  return (
    <React.Fragment>
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
      {tableData ?
          (<Grid container spacing={3}>
            {tableData.map( (entry) => { // map each result from the subject fetch (each form)
              return(
                <Grid item lg={12} xl={6}>
                  <Card>
                    <CardHeader
                      title = {entry.questionnaire["@name"]}
                    />
                    <CardContent>
                      {(entry["@name"]) ? getFormData(entry["@name"]) : null}
                    </CardContent>
                  </Card>
                </Grid>
              )
            })}
          </Grid>
          ) : <div>Please Wait...</div>
        }
    </React.Fragment>
  );
};

Subject.propTypes = {
  id: PropTypes.string
}

export default Subject;
