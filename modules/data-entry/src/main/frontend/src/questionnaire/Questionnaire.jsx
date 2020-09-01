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

import React, { useRef, useState, useEffect } from "react";
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
import EditDialog from "../questionnaireEditor/EditDialog";
import DeleteDialog from "../questionnaireEditor/DeleteDialog";
import Fields from "../questionnaireEditor/Fields";
import DeleteIcon from "@material-ui/icons/Delete";
import EditIcon from '@material-ui/icons/Edit';

// Given the JSON object for a section or question, display it and its children
let DisplayFormEntries = (json, additionalProps) => {
  return Object.entries(json)
    .filter(([key, value]) => (value['jcr:primaryType'] == 'lfs:Section' || value['jcr:primaryType'] == 'lfs:Question'))
    .map(([key, value]) =>
      value['jcr:primaryType'] == 'lfs:Question'
      ? <Grid item key={key}><Question data={value} {...additionalProps}/></Grid>
      : <Grid item key={key}><Section data={value} {...additionalProps}/></Grid>
    );
}

// GUI for displaying details about a questionnaire.
let Questionnaire = (props) => {
  let { id, classes } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();
  let [ isEditing, setIsEditing ] = useState(false);
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
  let [ infoDialogOpen, setInfoDialogOpen ] = useState(false);
  let [ curEntryType, setCurEntryType ] = useState('Question');
  let [ anchorEl, setAnchorEl ] = useState(null);

  let handleError = (response) => {
    setError(response);
    setData([]);
  }

  let handleOpenMenu = (event) => {
    setAnchorEl(event.currentTarget);
  }

  let handleCloseMenu = () => {
    setAnchorEl(null);
  };

  let openDialog = (edit, type) => {
    setIsEditing(edit);
    setCurEntryType(type);
    setEditDialogOpen(true);
  }

  const questionRef = useRef();
  const anchor = location.hash.substr(1);
  // create a ref to store the question container DOM element
  useEffect(() => {
    const timer = setTimeout(() => {
      questionRef?.current?.scrollIntoView();
    }, 500);
    return () => clearTimeout(timer);
  }, [questionRef]);

  let fetchData = () => {
    fetch(`/Questionnaires/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(setData)
      .catch(handleError);
  };

  let closeDialog = () => {
    setData(null);
    fetchData();
  }

  if (!data) {
    fetchData();
  }

  let displayQuestion = (data) => {
    return Object.entries(data)
      .map(([key, value]) => {
        if (value['jcr:primaryType'] == 'lfs:Question') {
          // if autofocus is needed and specified in the url
          const questionPath = value["@path"];
          const doHighlight = (anchor == questionPath);

          return (
            <Grid item key={key} ref={doHighlight ? questionRef : undefined} className={(doHighlight ? classes.highlightedSection : undefined)}>
              <Question data={value}/>
            </Grid>
          );
        }
        // f-n calls itself recursively to display all question in nested sections
        if (value['jcr:primaryType'] == 'lfs:Section') {
          return displayQuestion(value);
        }
      });
  }

  return (
    <div>
      { error &&
        <Typography variant="h2" color="error">
          Error obtaining questionnaire info: {error.status} {error.statusText}
        </Typography>
      }
      <Grid container direction="column" spacing={8}>
      <Grid item>
        <Typography variant="h2">{data ? data['title'] : id} </Typography>
        {
          data?.['jcr:createdBy'] && data?.['jcr:created'] &&
            <Typography variant="overline">
              Created by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}
            </Typography>
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
            <MenuItem
              onClick={()=> {
                openDialog(false, 'Question');
                handleCloseMenu();
              }}
              >
              Question
            </MenuItem>
            <MenuItem
              onClick={()=> {
                openDialog(false, 'Section');
                handleCloseMenu();
              }}
              >
              Section
            </MenuItem>
          </Menu>
        </Grid>
      }
      { data &&
        <Grid item>
          <Card>
            <CardHeader
              title={'Questionnaire Properties'}
              action={
                <IconButton onClick={() => {openDialog(true, 'Questionnaire');}}>
                  <EditIcon />
                </IconButton>
              }/>
            <CardContent>
              <dl>
                <dt>
                  <Typography>Max per Subject:</Typography>
                </dt>
                <dd>
                  <Typography>{data.maxPerSubject || 'Unlimited'}</Typography>
                </dd>
                <dt>
                  <Typography>Subject Types:</Typography>
                </dt>
                <dd>
                  { data?.requiredSubjectTypes?.map( subjectType =>
                    (subjectType ?
                      <Typography key={subjectType.label}>{subjectType.label}</Typography>
                      : <Typography>'Any'</Typography>
                    )
                  )}
                </dd>
              </dl>
            </CardContent>
          </Card>
        </Grid>
      }
      {
        data ?
            DisplayFormEntries(data, {onClose: closeDialog})
          : <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
      }
      </Grid>
      {
        data &&
          <EditDialog
            edit={isEditing}
            data={data}
            type={curEntryType}
            open={editDialogOpen}
            onClose={() => {
              closeDialog();
              setEditDialogOpen(false);
              handleCloseMenu();
            }}
            />
      }
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
  let { onClose, data } = props;
  let [ editOpen, setEditOpen ] = useState(false);
  let [ deleteOpen, setDeleteOpen ] = useState(false);

  let showEditDialog = (isOpen) => {
    setEditOpen(isOpen);
    if (!isOpen && onClose) {
      onClose();
    }
  }

  let showDeleteDialog = (isOpen) => {
    setDeleteOpen(isOpen);
    if (!isOpen && onClose) {
      onClose();
    }
  }

  return (
    <Card>
      <CardHeader
        title={data.text}
        action={
          <div>
            <EditDialog
              edit={true}
              data={data}
              type='Question'
              open={editOpen}
              onOpen={() => {showEditDialog(true)}}
              onClose={() => {showEditDialog(false)}}
              />
            <IconButton onClick={() => {showDeleteDialog(true)}}>
              <DeleteIcon />
            </IconButton>
          </div>
        }
        />
      <CardContent>
        <dl>
          <Fields data={data} JSON={require('../questionnaireEditor/Question.json')[0]} edit={false} />
        </dl>
        {
          Object.values(data)
            .filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption')
            .map(value => <AnswerOption key={value['jcr:uuid']} data={value} />)
        }
      </CardContent>
      <DeleteDialog
        open={deleteOpen}
        data={data}
        onClose={() => {showDeleteDialog(false)}}
        type="Question"
        />
    </Card>
  );
};

Question.propTypes = {
  closeData: PropTypes.func,
  data: PropTypes.object.isRequired
};

let Section = (props) => {
  let { onClose, data } = props;
  let [ anchorEl, setAnchorEl ] = useState(null);
  let [ isEditing, setIsEditing ] = useState(false);
  let [ newFieldType, setNewFieldType ] = useState('Question');
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
  let [ deleteDialogOpen, setDeleteDialogOpen ] = useState(false);

  const handleOpenMenu = (event) => {
    setAnchorEl(event.currentTarget);
  }

  const handleCloseMenu = () => {
    setAnchorEl(null);
  };

  const openDialog = (isNew, type) => {
    setIsEditing(!isNew);
    setNewFieldType(type);
    setEditDialogOpen(true);
  }
  
  return (
    <Card>
      <CardHeader title={props.data['label'] || ''}
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
              <MenuItem onClick={()=> { openDialog(true, 'Question'); handleCloseMenu(); }}>Question</MenuItem>
              <MenuItem onClick={()=> { openDialog(true, 'Section'); handleCloseMenu(); }}>Section</MenuItem>
            </Menu>
            <EditDialog
              edit={isEditing}
              data={data}
              type={newFieldType}
              open={editDialogOpen}
              onOpen={() => {openDialog(false, 'Section')}}
              onClose={() => { onClose(); setEditDialogOpen(false); }}
              />
            <IconButton onClick={() => { setDeleteDialogOpen(false); }}>
              <DeleteIcon />
            </IconButton>
            <Typography>{data['description'] || ''}</Typography>
          </div>
        }>
      </CardHeader>
      <CardContent>
        <Grid container direction="column" spacing={8}>
          {
            data ?
                DisplayFormEntries(data, {onClose: onClose})
              : <Grid container justify="center"><Grid item><CircularProgress /></Grid></Grid>
          }
        </Grid>
      </CardContent>
      <DeleteDialog open={deleteDialogOpen} data={data} onClose={() => { onClose(); setDeleteDialogOpen(false); }} type="Section" />
    </Card>
  );
};

Section.propTypes = {
  data: PropTypes.object.isRequired
};

// A predefined answer option for a question.
let AnswerOption = (props) => {
  return <dd><Typography>{props.data.label}</Typography><Typography>{props.data.value}</Typography></dd>;
};

AnswerOption.propTypes = {
  data: PropTypes.object.isRequired
};
