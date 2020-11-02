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
  Avatar,
  Button,
  Card,
  CardContent,
  CardHeader,
  CircularProgress,
  Grid,
  Icon,
  IconButton,
  Menu,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableRow,
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
    .filter(([key, value]) => (value['jcr:primaryType'] == 'lfs:Section'
                            || value['jcr:primaryType'] == 'lfs:Question'))
    .map(([key, value]) =>
      value['jcr:primaryType'] == 'lfs:Question'
      ? <Grid item key={key} className={additionalProps.classes.cardSpacing}><Question data={value} {...additionalProps}/></Grid> :
      value['jcr:primaryType'] == 'lfs:Section'
      ? <Grid item key={key} className={additionalProps.classes.cardSpacing}><Section data={value} {...additionalProps}/></Grid>
      : null
    );
}

// GUI for displaying details about a questionnaire.
let Questionnaire = (props) => {
  let { id, classes } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();
  let [ isEditing, setIsEditing ] = useState(false);
  let [ entityType, setEntityType ] = useState('Question');
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
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

  let openEditDialog = (isEdit, type) => {
    setIsEditing(isEdit);
    setEntityType(type);
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

  let reloadData = () => {
    setData(null);
    fetchData();
  }

  if (!data) {
    fetchData();
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
        <Grid item className={classes.cardSpacing}>
          <Button aria-controls="simple-menu-main" aria-haspopup="true" onClick={handleOpenMenu}>
            Add...
          </Button>
          <Menu
            id="simple-menu-main"
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleCloseMenu}
            disableAutoFocusItem
          >
            <MenuItem onClick={()=> { openEditDialog(false, 'Question'); handleCloseMenu();}} > Question </MenuItem>
            <MenuItem onClick={()=> { openEditDialog(false, 'Section'); handleCloseMenu();}} > Section </MenuItem>
          </Menu>
        </Grid>
      }
      { data &&
        <Grid item className={classes.cardSpacing}>
          <Card variant="outlined">
            <QuestionnaireCardHeader
              label="Questionnaire properties"
              action={
                <IconButton onClick={() => { openEditDialog(true, 'Questionnaire'); }}>
                  <EditIcon />
                </IconButton>
              }/>
            <CardContent>
              <FieldsGrid
                className={classes.tableSpacing}
                classes={classes}
                fields= {Array(
                          {name: "maxPerType", label: "Maximim forms of this type per subject", value : data.maxPerSubject || 'Unlimited'},
                          {name: "subjectTypes", label: "Subject types", value: data.requiredSubjectTypes?.map(t => t.label).join(', ') || 'Any'},
                        )}
              />
            </CardContent>
          </Card>
        </Grid>
      }
      { data ?  DisplayFormEntries(data, {onClose: reloadData, classes: classes})
             : <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
      }
      </Grid>
      { editDialogOpen && data && <EditDialog
                            isEdit={isEditing}
                            data={data}
                            type={entityType}
                            isOpen={editDialogOpen}
                            onClose={() => { reloadData(); setEditDialogOpen(false); }}
                            onCancel={() => { setEditDialogOpen(false); }}
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
  let { onClose, data, classes } = props;
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
  let [ deleteDialogOpen, setDeleteDialogOpen ] = useState(false);
  let answers = Object.values(data).filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption');

  let displayAnswers = () => {
    return (
        <Grid container key={data['jcr:uuid']} alignItems='flex-start' spacing={2}>
          <Grid item key="label" xs={4}>
            <Typography>Answer options:</Typography>
          </Grid>
          <Grid item key="values" xs={8}>
            { answers.map(item => <Typography key={item['jcr:uuid']}>{(item.label || item.value) + (item.label ? (" (" + item.value + ")") : "")}</Typography>) }
          </Grid>
        </Grid>
    );
  };

  return (
    <Card variant="outlined">
      <QuestionnaireCardHeader
        avatarColor="purple"
        type="Question"
        label={data.text}
        action={
          <div>
            <IconButton onClick={() => { setEditDialogOpen(true); }}>
              <EditIcon />
            </IconButton>
            <IconButton onClick={() => { setDeleteDialogOpen(true); }}>
              <DeleteIcon />
            </IconButton>
          </div>
        }
      />
      <CardContent className={classes.questionSpacing}>
        <Fields data={data} JSON={require('../questionnaireEditor/Question.json')[0]} edit={false} />
        { answers.length > 0 && displayAnswers() }
      </CardContent>
      { editDialogOpen && <EditDialog
                              isEdit={true}
                              data={data}
                              type="Question"
                              isOpen={editDialogOpen}
                              onClose={() => { onClose(); setEditDialogOpen(false); }}
                              onCancel={() => { setEditDialogOpen(false); }}
                            />
      }
      { deleteDialogOpen && <DeleteDialog
                              isOpen={deleteDialogOpen}
                              data={data}
                              type="Question"
                              onClose={() => { onClose(); setDeleteDialogOpen(false); }}
                              onCancel={() => { setDeleteDialogOpen(false); }}
                            />
      }
    </Card>
  );
};

Question.propTypes = {
  closeData: PropTypes.func,
  data: PropTypes.object.isRequired
};

let Section = (props) => {
  let { onClose, data, classes } = props;
  let [ anchorEl, setAnchorEl ] = useState(null);
  let [ isEditing, setIsEditing ] = useState(false);
  let [ entityType, setEntityType ] = useState('Question');
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
  let [ deleteDialogOpen, setDeleteDialogOpen ] = useState(false);

  let handleOpenMenu = (event) => {
    setAnchorEl(event.currentTarget);
  }

  let handleCloseMenu = () => {
    setAnchorEl(null);
  };

  let openEditDialog = (isEdit, type) => {
    setIsEditing(isEdit);
    setEntityType(type);
    setEditDialogOpen(true);
  }
  let extractProperties = (data) => {
    let p = Array();
    let spec = require('../questionnaireEditor/Section.json');
    Object.keys(spec[0]).filter(key => {return (key != 'label') && !!data[key]}).map(key => {
      p.push({name: key, label: key.charAt(0).toUpperCase() + key.slice(1).replace( /([A-Z])/g, " $1" ).toLowerCase(), value: data[key] + ""});
    });
    // Find conditionals
    Object.entries(data).filter(([key, value]) => (value['jcr:primaryType'] == 'lfs:Conditional')).map(([key, value]) => {
      p.push({
        name: "condition",
        label: "Condition",
        value : value?.operandA?.value.join(', ') + " " + value?.comparator + " " + value?.operandB?.value.join(', ')
      });
    })
    return p;
  }
  
  return (
    <Card variant="outlined">
      <QuestionnaireCardHeader
        avatar="view_stream"
        avatarColor="orange"
        type="Section"
        label={props.data.label}
        action={
          <div>
            <Button aria-controls={"simple-menu" + props.data['@name']} aria-haspopup="true" onClick={handleOpenMenu}>
              Add...
            </Button>
            <Menu
              id={"simple-menu" + props.data['@name']}
              anchorEl={anchorEl}
              keepMounted
              open={Boolean(anchorEl)}
              onClose={handleCloseMenu}
            >
              <MenuItem onClick={() => { openEditDialog(false, 'Question'); handleCloseMenu(); }}>Question</MenuItem>
              <MenuItem onClick={() => { openEditDialog(false, 'Section'); handleCloseMenu(); }}>Section</MenuItem>
            </Menu>
            <IconButton onClick={() => { openEditDialog(true, 'Section'); }}>
              <EditIcon />
            </IconButton>
            <IconButton onClick={() => { setDeleteDialogOpen(true); }}>
              <DeleteIcon />
            </IconButton>
          </div>
        }
      />
      <CardContent className={classes.questionSpacing}>
        <FieldsGrid fields={extractProperties(data)} classes={classes}/>
        <Grid container direction="column" spacing={8}>
          {
            data ?
                DisplayFormEntries(data, {onClose: onClose, classes: classes})
              : <Grid container justify="center"><Grid item><CircularProgress /></Grid></Grid>
          }
        </Grid>
      </CardContent>
      { editDialogOpen && <EditDialog
                            isEdit={isEditing}
                            data={data}
                            type={entityType}
                            isOpen={editDialogOpen}
                            onClose={() => { onClose(); setEditDialogOpen(false); }}
                            onCancel={() => { setEditDialogOpen(false); }}
                          />
      }
      { deleteDialogOpen && <DeleteDialog
                              isOpen={deleteDialogOpen}
                              data={data}
                              type="Section"
                              onClose={() => { onClose(); setDeleteDialogOpen(false); }}
                              onCancel={() => { setDeleteDialogOpen(false); }}
                            />
      }
    </Card>
  );
};

Section.propTypes = {
  data: PropTypes.object.isRequired
};

let QuestionnaireCardHeader = (props) => {
  return (
    <>
      <CardHeader
        disableTypography
        avatar={
          props.avatar || props.type ?
          <Avatar aria-label="recipe" style={{backgroundColor: props.avatarColor || "black"}}>{
            props.avatar ?
            <Icon>{props.avatar}</Icon> :
            props.type.charAt(0)
          }</Avatar>
          : null
        }
        title={
          <div>
            <Typography variant="overline">{props.type}</Typography>
            <Typography variant="h6">{props.label}</Typography>
          </div>
        }
        action={props.action}
      >
      </CardHeader>
    </>
  );
};

let FieldsGrid = (props) => {
  return (
    <Table aria-label="simple table">
      <TableBody>
        {props.fields?.map((row) => (
          <TableRow key={row.name}>
            <TableCell className={props.classes.tableThCell} component="th" scope="row">{row.label}:</TableCell>
            <TableCell align="left" className={props.classes.tableCell}>{row.value}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}