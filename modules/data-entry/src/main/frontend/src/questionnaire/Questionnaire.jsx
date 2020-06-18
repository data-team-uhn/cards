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
  Card,
  CardContent,
  CardHeader,
  CircularProgress,
  Grid,
  Menu,
  MenuItem,
  Typography,
  withStyles
} from "@material-ui/core";

import moment from "moment";

import QuestionnaireStyle from "./QuestionnaireStyle";
import EditDialog from "./EditDialog"
import DeleteDialog from "./DeleteDialog"

// GUI for displaying details about a questionnaire.
let Questionnaire = (props) => {
  let { id } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();
  const [ isFetching, setFetching ] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);

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

  const handleOpenMenu = (event) => {
    setAnchorEl(event.currentTarget);
  }

  const handleCloseMenu = () => {
    setAnchorEl(null);
  };

  let parseErrorResponse = (response) => {
    setFetching(false);
    setError(`New question request failed with error code ${response.status}: ${response.statusText}`);
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
      { data &&
        <Grid item>
          <Button aria-controls="simple-menu" aria-haspopup="true" onClick={handleOpenMenu}>
            Add...
          </Button>
          <Menu
            id="simple-menu"
            anchorEl={anchorEl}
            keepMounted
            open={Boolean(anchorEl)}
            onClose={handleCloseMenu}
          >
            <MenuItem>Question<EditDialog edit={false} data={data} type='Question' /></MenuItem>
            <MenuItem >Section<EditDialog edit={false} data={data} type='Section' /></MenuItem>
          </Menu>
        </Grid>
      }
      {
        data ?
        Object.entries(data)
        .filter(([key, value]) => (value['jcr:primaryType'] == 'lfs:Section' || value['jcr:primaryType'] == 'lfs:Question'))
        .map(([key, value]) => 
          value['jcr:primaryType'] == 'lfs:Question' 
          ? <Grid item key={key}><Question data={value}/></Grid>
          : <Grid item key={key}><Section data={value}></Section></Grid>
        )
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
  return (
    <Card>
      <CardHeader title={props.data.text} action={
        <div>
          <EditDialog data={props.data} type="Question" edit={true}/>
          <DeleteDialog data={props.data} type="Question" edit={true} />
        </div>
      }
      />
      <CardContent>
        <dl>
          <dt>
            <Typography>Label:</Typography>
          </dt>
          <dd>
            <Typography>{props.data.text}</Typography>
          </dd>
          <dt>
            <Typography>Description:</Typography>
          </dt>
          <dd>
            <Typography>{props.data.description}</Typography>
          </dd>
          <dt>
            <Typography>Answer type:</Typography>
          </dt>
          <dd>
            <Typography>{props.data.dataType}</Typography>
          </dd>
          <dt>
            <Typography>Minimum number of selected options:</Typography>
          </dt>
          <dd>
            <Typography>{props.data.minAnswers || 0}</Typography>
          </dd>
          <dt>
            <Typography>Maximum number of selected options:</Typography>
          </dt>
          <dd>
            <Typography>{props.data.maxAnswers || 0}</Typography>
          </dd>
          <dt>
            <Typography>Answer choices:</Typography>
          </dt>
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
  data: PropTypes.object.isRequired
};

let Section = (props) => {
  let { data } = props;
  const [ anchorEl, setAnchorEl ] = useState(null);
  const handleOpenMenu = (event) => {
    setAnchorEl(event.currentTarget);
  }

  const handleCloseMenu = () => {
    setAnchorEl(null);
  };
  return (
    <Card>
      <CardHeader title={data['title'] || ''}
        action={
          <div>
            <Button aria-controls="simple-menu" aria-haspopup="true" onClick={handleOpenMenu}>
              Add...
            </Button>
            <Menu
              id="simple-menu"
              anchorEl={anchorEl}
              keepMounted
              open={Boolean(anchorEl)}
              onClose={handleCloseMenu}
            >
              <MenuItem>Question<EditDialog edit={false} data={props.data} type='Question' /></MenuItem>
              <MenuItem >Section<EditDialog edit={false} data={props.data} type='Section' /></MenuItem>
            </Menu>
            <EditDialog edit={true} data={props.data} type='Section' />
            <DeleteDialog data={props.data} type='Section' />
            <Typography>{data['description'] || ''}</Typography>
          </div>
        }>
      </CardHeader>
      <CardContent>
        <Grid container direction="column" spacing={8}>
          {
            data ?
              Object.entries(data)
              .filter(([key, value]) => (value['jcr:primaryType'] == 'lfs:Section' || value['jcr:primaryType'] == 'lfs:Question'))
              .map(([key, value]) => 
                value['jcr:primaryType'] == 'lfs:Question' 
                ? <Grid item key={key}><Question data={value}/></Grid>
                : <Grid item key={key}><Section data={value}></Section></Grid>
              )
              :
              <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
          }
        </Grid>
      </CardContent>
    </Card>
  );
};

Section.propTypes = {
  data: PropTypes.object.isRequired
};

// A predefined answer option for a question.
let AnswerOption = (props) => {
  return <dd>{props.data.label} (<Typography variant="body2" component="span">{props.data.value}</Typography>)</dd>;
};

AnswerOption.propTypes = {
  data: PropTypes.object.isRequired
};
