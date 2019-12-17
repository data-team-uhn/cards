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

import {
  Button,
  CircularProgress,
  Grid,
  Typography,
  withStyles
} from "@material-ui/core";

import QuestionnaireStyle from "./QuestionnaireStyle";
import AnswerComponentManager from "./AnswerComponentManager";

// todo: once components from the login module can be imported, open the login Dialog instead of redirecting to the login page

// FIXME In order for the questions to be registered, they need to be loaded, and the only way to do that at the moment is to explicitly invoke them here. Find a way to automatically load all question types, possibly using self-declaration in a node, like the assets, or even by filtering through assets.

import BooleanQuestion from "./BooleanQuestion";
import DateQuestion from "./DateQuestion";
import NumberQuestion from "./NumberQuestion";
import PedigreeQuestion from "./PedigreeQuestion";
import TextQuestion from "./TextQuestion";
import VocabularyQuestion from "./VocabularyQuestion";

/**
 * Component that displays an editable Form.
 *
 * @example
 * <Form id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a form; this is the JCR node name
 */
function Form (props) {
  let { classes, id } = props;
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // Marks that a save operation is in progress
  let [ saveInProgress, setSaveInProgress ] = useState();
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);

  // Fetch the form's data as JSON from the server.
  // The data will contain the form metadata,
  // such as authorship and versioning information, the associated subject,
  // the questionnaire definition,
  // and all the existing answers.
  // Once the data arrives from the server, it will be stored in the `data` state variable.
  let fetchData = () => {
    fetch(`/Forms/${id}.deep.json`)
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

  // Event handler for the form submission event, replacing the normal browser form submission with a background fetch request.
  let saveData = (event) => {
    setSaveInProgress(true);
    // This stops the normal browser form submission
    event.preventDefault();
    // currentTarget is the element on which the event listener was placed and invoked, thus the <form> element
    let data = new FormData(event.currentTarget);
    fetch(`/Forms/${id}`, {
      method: "POST",
      body: data
    }).then((response) => response.ok ? true : Promise.reject(response))
      .then(() => setLastSaveStatus(true))
      // FIXME Use setError?
      .catch(() => {
        if (lastSaveStatus === false) {
          loginToSave();
        } else {
          // on first attempt to save while logged out, set status to false
          setLastSaveStatus(false);
        }
      })
      .finally(() => setSaveInProgress(false));
  }

  // method to open the login page in a new window, centered wrt the parent window
  let loginToSave = () => {
    const w = 600; // width of new window
    const h = 800; // height of new window
    const y = window.top.outerHeight / 2 + window.top.screenY - ( h / 2);
    const x = window.top.outerWidth / 2 + window.top.screenX - ( w / 2);
    window.open("/login.html", "loginOpenedByForm", `width=${w}, height=${h}, top=${y}, left=${x}`);
    setLastSaveStatus([]); // display 'save' on button
  }

  /**
   * Method responsible for displaying a question from the questionnaire, along with its answer(s).
   *
   * @param {Object} questionDefinition the question definition JSON
   * @param {string} key the node name of the question definition JCR node
   * @returns a React component that renders the question
   */
  let displayQuestion = (questionDefinition, key) => {
    const existingAnswer = Object.entries(data)
      .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
        && value["question"]["jcr:uuid"] === questionDefinition["jcr:uuid"]);
    // This variable must start with an upper case letter so that React treats it as a component
    const QuestionDisplay = AnswerComponentManager.getAnswerComponent(questionDefinition);
    return <QuestionDisplay key={key} questionDefinition={questionDefinition} existingAnswer={existingAnswer} />;
  };

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    fetchData();
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  // If an error was returned, do not display a form at all, but report the error
  if (error) {
    return (
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining form data: {error.status} {error.statusText}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  return (
    <form action={data["@path"]} method="POST" onSubmit={saveData} onChange={()=>setLastSaveStatus(undefined)}>
      <Grid container direction="column" spacing={4} alignItems="stretch" justify="space-between" wrap="nowrap">
        <Grid item>
          {
            data && data.questionnaire && data.questionnaire.title ?
              <Typography variant="overline">{data.questionnaire.title}</Typography>
            : ""
          }
          {
            data && data.subject && data.subject.identifier ?
              <Typography variant="h2">{data.subject.identifier}</Typography>
            : <Typography variant="h2">{id}</Typography>
          }
          {
            data && data['jcr:createdBy'] && data['jcr:created'] ?
            <Typography variant="overline">Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
            : ""
          }
        </Grid>
        {
          Object.entries(data.questionnaire)
            .filter(([key, value]) => value['jcr:primaryType'] == 'lfs:Question')
            .map(([key, questionDefinition]) => <Grid item key={key}>{displayQuestion(questionDefinition, key)}</Grid>)
        }
      </Grid>

      <Button
        type="submit"
        variant="contained"
        color="primary"
        disabled={saveInProgress}
        className={classes.saveButton}
      >
        {saveInProgress ? 'Saving' :
        lastSaveStatus === true ? 'Saved' :
        lastSaveStatus === false ? 'Save failed, log in and try again?' :
        'Save'}
      </Button>
    </form>
  );
};

export default withStyles(QuestionnaireStyle)(Form);
