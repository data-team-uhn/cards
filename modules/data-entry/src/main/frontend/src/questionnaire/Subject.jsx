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
  Card,
  CardHeader,
  CardContent,
  CardActions,
  withStyles,
  Button
} from "@material-ui/core";

const QUESTION_TYPES = ["lfs:Question"];
const SECTION_TYPES = ["lfs:Section"];
const ENTRY_TYPES = QUESTION_TYPES.concat(SECTION_TYPES);


// FIX: not rendering boolean/sections correctly. maybe place in formentry 
let displayQuestion = (questionDefinition, path, existingAnswer, key, classes) => {

  const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
    .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
      && value["question"]["jcr:uuid"] === questionDefinition["jcr:uuid"]);

  // question title, to be used when 'previewing' the form
  const questionTitle = questionDefinition["text"];

  let content = null;

  if (existingQuestionAnswer && existingQuestionAnswer[1]["value"]) {
    content = `${questionTitle}: ${existingQuestionAnswer[1]["value"]}`
  }

  return (
    <Typography variant="body2" component="p">{content}</Typography>
  );
};

function SubjectEntry(props) {
  let { classes, entryDefinition, path, depth, existingAnswers, keyProp } = props;
  if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
    return displayQuestion(entryDefinition, path, existingAnswers, keyProp, classes);
  }
  // TODO: handle filtering to get the questions from the section --> displayQuestion
  else return null;
}

  function FormData(props) {
    let { classes, formID, maxDisplayed } = props; // set maxDisplayed default to 2
    // This holds the full form JSON, once it is received from the server
    let [ data, setData ] = useState();

    let getFormData = (formID) => {
      fetch(`/Forms/${formID}.deep.json`)
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then((json) => setData(json))
          .catch(handleFormError)
    }

    let handleFormError = (response) => {
      // setError(response); // TODO: display for error handling
      setData([]);  // Prevent an infinite loop if data was not set
    };

    if (!data) {
      getFormData(formID);
      return (
        <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
      );
    }

    // TODO: rearrange: if data --> object.entries --> return the 'subject entry' - no need for separate component

    // if form is not filled out, should display 'No data yet'

    // console.log(data.questionnaire);

    // if (data && data.questionnaire) {
    //   return (
    //     <React.Fragment>
    //       {
    //         Object.entries(data.questionnaire)
    //         .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
    //         .slice(0, maxDisplayed) // should it be sliced here or just display first filled values
    //         .map(([key, entryDefinition]) => displayData(key, entryDefinition, data))
    //       }
    //     </React.Fragment>
    //   );
 
    // }

    // //fix error: displaydata is not a function??
    // let displayData = (key, entryDefinition, existingAnswer) => {
    //   if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {

    //     const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
    //     .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
    //       && value["question"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);

    //     // question title, to be used when 'previewing' the form
    //     const questionTitle = entryDefinition["text"];

    //     let content = null;

    //     if (existingQuestionAnswer && existingQuestionAnswer[1]["value"]) {
    //       content = `${questionTitle}: ${existingQuestionAnswer[1]["value"]}`
    //     }

    //     return (
    //       <Typography variant="body2" component="p" key={key}>{content}</Typography>
    //     );
    //   }
    //   // else if (SECTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
    //   //   // get the questions from within the section and call the same displayData function
    //   // }
    // }

    // return (
    //   <Typography>Form not found</Typography> // retry button?
    // );

    return (
      <React.Fragment>
        {data && data.questionnaire ?
          (Object.entries(data.questionnaire)
            .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
            .slice(0, maxDisplayed) // should it be sliced here or just display first filled values
            .map(([key, entryDefinition]) => <SubjectEntry key={key} entryDefinition={entryDefinition} path={"."} depth={0} existingAnswers={data} keyProp={key} classes={classes}></SubjectEntry>)
        )
        : <Typography>Form not found</Typography> // retry button?
        }
      </React.Fragment>
    );
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
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // table data: related forms
  const [tableData, setTableData] = useState();

  const [fetchStatus, setFetchStatus] = useState(
    {
      "currentRequestNumber": -1,
      "currentFetch": false,
      "fetchError": false,
    }
  );

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

  const customUrl='/Forms.paginate?fieldname=subject&fieldvalue='
        + encodeURIComponent(data['jcr:uuid']);
  
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

    url.searchParams.set("req", ++fetchStatus.currentRequestNumber);

    let currentFetch = fetch(url);
    setFetchStatus(Object.assign({}, fetchStatus, {
      "currentFetch": currentFetch,
      "fetchError": false,
    }));
    currentFetch.then((response) => response.ok ? response.json() : Promise.reject(response)).then(handleSubjectResponse).catch(handleSubjectError);
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
  }; // TODO: handle all errors the same?

  // initialize fetch
  if (fetchStatus.currentRequestNumber == -1) {
    fetchSubjectData();
  }

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
                <Grid item lg={12} xl={6} key={entry.questionnaire["jcr:uuid"]}>
                  <Card>
                    <CardHeader
                      title={
                        <Link to={"/content.html" + entry["@path"]}>
                        <Button size="small"> 
                          {/* classname styling - import from livetable styling? or add new styles to questionnaire */}
                          {entry.questionnaire["@name"]}
                        </Button>
                      </Link>
                    }
                    />
                    <CardContent>
                      <FormData formID={entry["@name"]} maxDisplayed={4}/>
                      <Link to={"/content.html" + entry["@path"]}>
                        <Button size="small">See More...</Button>
                      </Link>
                    </CardContent>
                  </Card>
                </Grid>
              )
            })}
          </Grid>
          ) : <Typography>Loading...</Typography>
        }
    </React.Fragment>
  );
};

Subject.propTypes = {
  id: PropTypes.string
}

export default Subject;
