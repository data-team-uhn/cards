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
  IconButton,
  Menu,
  MenuItem,
  Typography,
  withStyles
} from "@material-ui/core";

import moment from "moment";

import QuestionnaireStyle from "./QuestionnaireStyle";
import EditDialog from "./EditDialog"
import DeleteDialog from "./DeleteDialog"
import Fields from './Fields'
import DeleteIcon from "@material-ui/icons/Delete";
import EditIcon from '@material-ui/icons/Edit';

// GUI for displaying details about a questionnaire.
let Questionnaire = (props) => {
  let { id } = props;
  let [ data, setData ] = useState();
  let [ questionKey, setQuestionKey ] = useState(Math.random());
  let [ error, setError ] = useState();
  let [ edit, setEdit ] = useState(false);
  let [ open, setOpen ] = useState(false);
  let [ type, setType ] = useState('Question');
  const [ isFetching, setFetching ] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);

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

  let openDialog = (edit, type) => {
    setEdit(edit);
    setType(type);
    setOpen(true);
  }

  let fetchData = () => {
    fetch(`/Questionnaires/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleResponse)
      .catch(handleError);
  };

  let parseErrorResponse = (response) => {
    setFetching(false);
    setError(`New question request failed with error code ${response.status}: ${response.statusText}`);
  }

  let closeDialog = () => {
    setData(null);
    fetchData();
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
            open={Boolean(anchorEl)}
            onClose={handleCloseMenu}
            disableAutoFocusItem
          >
            <MenuItem onClick={()=> { openDialog(false, 'Question'); handleCloseMenu}}>Question</MenuItem>
            <MenuItem onClick={()=> { openDialog(false, 'Section'); handleCloseMenu}}>Section</MenuItem>
          </Menu>
        </Grid>
      }
      {
        data ?
        Object.entries(data)
        .filter(([key, value]) => (value['jcr:primaryType'] == 'lfs:Section' || value['jcr:primaryType'] == 'lfs:Question'))
        .map(([key, value]) => 
          value['jcr:primaryType'] == 'lfs:Question' 
          ? <Grid item key={key}><Question data={value} closeDialog={closeDialog}/></Grid>
          : <Grid item key={key}><Section data={value} closeDialog={closeDialog}/></Grid>
        )
        :
          <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
      }
      </Grid>
      <EditDialog edit={edit} data={data} type={type} open={open} onClose={() => { closeDialog(); setOpen(false); handleCloseMenu(); }} />
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
  let [ open, setOpen ] = useState(false);
  let [ openDelete, setOpenDelete ] = useState(false);
  let openDialog = () => {
    setOpen(true);
  }
  let openDeleteDialog = () => {
    setOpenDelete(true);
  }

  return (
    <Card>
      <CardHeader title={props.data.text} action={
        <div>
          <IconButton onClick={() => { openDialog(); }}>
            <EditIcon />
          </IconButton>
          <IconButton onClick={() => { openDeleteDialog() }}>
            <DeleteIcon />
          </IconButton>
      </div>
      }
      />
      <CardContent>
        <dl>
          <Fields data={props.data} JSON={require('./Question.json')[0]} edit={false} />
        </dl>
      </CardContent>
      <EditDialog edit={true} data={props.data} type='Question' open={open} onClose={() => { props.closeDialog(); setOpen(false); }} />
      <DeleteDialog open={openDelete} data={props.data} onClose={() => { props.closeDialog(); setOpenDelete(false); }} type="Question" />
    </Card>
  );
};

Question.propTypes = {
  data: PropTypes.object.isRequired
};

let Section = (props) => {
  const [ anchorEl, setAnchorEl ] = useState(null);
  const handleOpenMenu = (event) => {
    setAnchorEl(event.currentTarget);
  }

  const handleCloseMenu = () => {
    setAnchorEl(null);
  };
  let [ edit, setEdit ] = useState(false);
  let [ type, setType ] = useState('Question');
  let [ open, setOpen ] = useState(false);
  let [ openDelete, setOpenDelete ] = useState(false);
  let openDialog = (edit, type) => {
    setEdit(edit);
    setType(type);
    setOpen(true);
  }
  let openDeleteDialog = () => {
    setOpenDelete(true);
  }
  
  return (
    <Card>
      <CardHeader title={props.data['title'] || ''}
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
              <MenuItem onClick={()=> { openDialog(false, 'Question'); handleCloseMenu}}>Question</MenuItem>
              <MenuItem onClick={()=> { openDialog(false, 'Section'); handleCloseMenu}}>Section</MenuItem>
            </Menu>
            <IconButton onClick={() => { openDialog(true, 'Section'); }}>
              <EditIcon />
            </IconButton>
            <IconButton onClick={() => { openDeleteDialog() }}>
              <DeleteIcon />
            </IconButton>
            <Typography>{props.data['description'] || ''}</Typography>
          </div>
        }>
      </CardHeader>
      <CardContent>
        <Grid container direction="column" spacing={8}>
          {
            props.data ?
              Object.entries(props.data)
              .filter(([key, value]) => (value['jcr:primaryType'] == 'lfs:Section' || value['jcr:primaryType'] == 'lfs:Question'))
              .map(([key, value]) => 
                value['jcr:primaryType'] == 'lfs:Question' 
                ? <Grid item key={key}><Question data={value} closeDialog={props.closeDialog} /></Grid>
                : <Grid item key={key}><Section data={value} closeDialog={props.closeDialog}></Section></Grid>
              )
              :
              <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
          }
        </Grid>
      </CardContent>
      <EditDialog edit={edit} data={props.data} type={type} open={open} onClose={() => { props.closeDialog(); setOpen(false); }} />
      <DeleteDialog open={openDelete} data={props.data} onClose={() => { props.closeDialog(); setOpenDelete(false); }} type="Section" />
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
