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
import TextQuestion from "./TextQuestion";

// GUI for displaying answers
export default function Form (props) {
  let { id } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();

  let fetchData = () => {
    fetch(`/Forms/${id}.deep.json`).then((response) => response.ok ? response.json() : Promise.reject(response)).then(handleResponse).catch(handleError);
  };

  let handleResponse = (json) => {
    setData(json);
  };

  let handleError = (response) => {
    console.error(response);
    setError(response);
  }

  if (!data) {
    fetchData();
    return (
      <div>
        <Typography variant="h2">{id}</Typography>
        <Grid container direction="column" spacing={8}>
          <Typography>Loading...</Typography>
        </Grid>
      </div>
    );
  }

  return (
    <div>
      <Typography variant="h2">{id}</Typography>
      <Grid container direction="column" spacing={8}>
        {
          Object.entries(data.questionnaire)
            .filter(([key, value]) => value['jcr:primaryType'] == 'lfs:Question')
            .map(([key, questionDefinition]) =>
              <Typography key={key}>Will display
                {questionDefinition.text}
                with
                {AnswerComponentManager.getAnswerComponent(questionDefinition).name}
                for answers
                {JSON.stringify(
                  Object.entries(data)
                    .filter(([key, value]) => value['sling:resourceSuperType'] == 'lfs/Answer')
                    .filter(([key, answer]) => answer['question']['jcr:uuid'] === questionDefinition['jcr:uuid'])
                    .map(([key, answer]) => answer.value))
                }
              </Typography>
            )
        }
      </Grid>
    </div>
  );
};
