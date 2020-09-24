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
  Card,
  CardContent,
  CardHeader,
  CircularProgress,
  Grid,
  IconButton,
  Typography,
  withStyles
} from "@material-ui/core";

import moment from "moment";

import QuestionnaireStyle from "./QuestionnaireStyle";
import EditQuestionnaireDialog from "./EditQuestionnaireDialog"
import OpenWithIcon from "@material-ui/icons/OpenWith";

// GUI for displaying details about a questionnaire.
let Questionnaire = (props) => {
  let { id } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();

  let fetchData = () => {
    fetch(`/Questionnaires/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleResponse)
      .catch(handleError);
  };

  let handleResponse = (json) => {
    setData(json);
  };

  let handleError = (response) => {
    // FIXME Display errors to the users
    setError(response);
    setData([]);
  }

  if (!data) {
    fetchData();
  }

  return (
    <div>
      {
        error && <Typography variant="h2" color="error">
            Error obtaining questionnaire info: {error.status} {error.statusText}
          </Typography>
      }
      <Grid container direction="column" spacing={8}>
      <Grid item>
        <Typography variant="h2">{data ? data['title'] : id} </Typography>
        {
          data && data['jcr:createdBy'] && data['jcr:created'] ?
            <Typography variant="overline">Created by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
            : ""
        }
      </Grid>
        {
          data ?
            Object.entries(data)
              .filter(([key, value]) => value['jcr:primaryType'] == 'lfs:Question')
              .map(([key, value]) => <Grid item key={key}><Question data={value} id={id}/></Grid>)
          :
            <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
        }
      </Grid>
    </div>
  );
};

Questionnaire.propTypes = {
    id: PropTypes.string.isRequired
};

export default withStyles(QuestionnaireStyle)(Questionnaire);

// Details about a particular question in a questionnaire.
// Not to be confused with the public Question component responsible for rendering questions inside a Form.
let Question = (props) => {
  let { data, id } = props;
  return (
    <Card>
      <CardHeader title={props.data.text} action={
        <div>
          <IconButton>
            <OpenWithIcon />
          </IconButton>
          <EditQuestionnaireDialog key={location.pathname} edit={true} data={props.data} id={id}></EditQuestionnaireDialog>
          </div>
      }
      />
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
          {
            Object.values(props.data)
              .filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption')
              .map(value => <AnswerOption key={value['jcr:uuid']} data={value} />)
          }
        </dl>
      </CardContent>
    </Card>
  );
};

Question.propTypes = {
    data: PropTypes.object.isRequired,
    id: PropTypes.string.isRequired
};

// A predefined answer option for a question.
let AnswerOption = (props) => {
  return <dd>{props.data.label} (<Typography variant="body2" component="span">{props.data.value}</Typography>)</dd>;
};

AnswerOption.propTypes = {
    data: PropTypes.object.isRequired
};
