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

// FIXME In order for the questions to be registered, they need to be loaded, and the only way to do that at the moment is to explicitly invoke them here. Find a way to automatically load all question types, possibly using self-declaration in a node, like the assets, or even by filtering through assets.

import BooleanQuestion from "./BooleanQuestion";
import DateQuestion from "./DateQuestion";
import NumberQuestion from "./NumberQuestion";
import PedigreeQuestion from "./PedigreeQuestion";
import TextQuestion from "./TextQuestion";

// GUI for displaying answers
export default function Form (props) {
  let { id } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();
  let [ saveInProgress, setSaveInProgress ] = useState();
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);

  let fetchData = () => {
    fetch(`/Forms/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleResponse)
      .catch(handleError);
  };

  let handleResponse = (json) => {
    setData(json);
  };

  let handleError = (response) => {
    setError(response);
  };

  let saveData = (event) => {
    setSaveInProgress(true);
    event.preventDefault();
    let data = new FormData(event.currentTarget);
    fetch(`/Forms/${id}`, {
      method: "POST",
      body: data
    }).then((response) => response.ok ? true : Promise.reject(response))
      .then(() => setLastSaveStatus(true))
      .catch(() => setLastSaveStatus(false))
      .finally(() => setSaveInProgress(false));
  }

  let displayQuestion = (questionDefinition, key) => {
    const existingAnswer = Object.entries(data)
      .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
        && value["question"]["jcr:uuid"] === questionDefinition["jcr:uuid"]);
    // This variable must start with an upper case letter so that React treats it as a component
    const QuestionDisplay = AnswerComponentManager.getAnswerComponent(questionDefinition);
    return <QuestionDisplay key={key} questionDefinition={questionDefinition} existingAnswer={existingAnswer} />;
  };

  if (!data) {
    fetchData();
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
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
            : id
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
        <Grid item><Button type="submit" variant="contained" color="primary" disabled={saveInProgress}>{saveInProgress ? 'Saving' : lastSaveStatus === true ? 'Saved' : lastSaveStatus === false ? 'Save failed, try again?' : 'Save'}</Button></Grid>
      </Grid>
    </form>
  );
};
