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

import { Paper, Grid, Typography, Card, CardHeader, CardContent, withStyles } from "@material-ui/core";

import QuestionnaireStyle from "./QuestionnaireStyle";

// GUI for displaying answers
export default function Questionnaire (props) {
  let { id } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();

  let fetchData = () => {
    fetch(`/Questionnaires/${id}.deep.json`).then((response) => response.ok ? response.json() : Promise.reject(response)).then(handleResponse).catch(handleError);
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
  }

  return (
    <div>
      <Typography variant="h2">{data ? data['title'] : id} </Typography>
      <Grid container direction="column" spacing={8}>
        {
          data ?
            Object.entries(data)
              .filter(([key, value]) => value['jcr:primaryType'] == 'lfs:Question')
              .map(([key, value]) => <Question key={key} data={value}/>)
          :
            <Typography>Loading...</Typography>
        }
      </Grid>
    </div>
  );
};

let Question = (props) => {
  return <Grid item>
    <Card>
      <CardHeader title={props.data.text} />
      <CardContent>
        <dl>
          <dt>Label:</dt>
          <dd>{props.data.text}</dd>
          <dt>Description:</dt>
          <dd>{props.data.description}</dd>
          <dt>Answer type:</dt>
          <dd>{props.data.dataType}</dd>
          <dt>Minimum number of selected options:</dt>
          <dd>{props.data.minAnswers || 0}</dd>
          <dt>Maximum number of selected options:</dt>
          <dd>{props.data.maxAnswers || 0}</dd>
          <dt>Answer choices:</dt>
          {Object.values(props.data).filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption').map(value => <AnswerOption key={value['jcr:uuid']} data={value} />)}
        </dl>
      </CardContent>
    </Card>
  </Grid>;
};

let AnswerOption = (props) => {
  return <dd>{props.data.label} (<Typography variant="body2" component="span">{props.data.value}</Typography>)</dd>;
};
