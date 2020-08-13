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
import uuid from "uuid/v4";
import { Button, Card, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, Grid, withStyles, MenuItem, Select, TextField, Typography } from "@material-ui/core";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import Filters from "./Filters";

function AdminStatistics(props) {
  const { classes } = props;
  let [statistics, setStatistics] = useState([1, 2, 3]); // each statistic becomes one chart. TODO: fetch statistics
  const [ dialogOpen, setDialogOpen ] = useState(false);

  let dialogClose = () => {
    setDialogOpen(false);
  }

  let addSuccess = () => {
    console.log('success!')
  }

  return (
    <React.Fragment>
      <Grid container spacing={3}>
        {statistics.map((statistic) => {
          return(
            <Grid item lg={12} xl={6} key={statistic}>
              <Card>
                <CardContent>
                  <Typography variant="body2" component="p">Name:</Typography>
                  <Typography variant="body2" component="p">X-axis:</Typography>
                  <Typography variant="body2" component="p">Y-axis:</Typography>
                  <Typography variant="body2" component="p">Split:</Typography>
                </CardContent>
              </Card>
            </Grid>
          )
        })}
      </Grid>
      <Button onClick={() => setDialogOpen(true)}>New chart</Button>
      <NewStatisticDialog open={dialogOpen} onClose={dialogClose} classes={classes} onSuccess={addSuccess} />
    </React.Fragment>
  );
}

function NewStatisticDialog(props) {
  const { onClose, onSuccess, open, classes } = props;
  const [ numFetchRequests, setNumFetchRequests ] = useState(0);
  const [ availableSubjects, setAvailableSubjects ] = useState([]);
  const [ initialized, setInitialized ] = useState(false);
  const [ error, setError ] = useState();

  const [ name, setName ] = useState('');
  const [ xVar, setXVar ] = useState();
  const [ yVar, setYVar ] = useState();
  const [ splitVar, setSplitVar ] = useState();
  const [ subjectLabel, setSubjectLabel ] = useState('')

  let createStatistic = () => {
    // Handle unfilled form errors
    if (!name) {
      setError("Please enter a name for this statistic.");
    } else if (!xVar) {
      setError("Please select a variable for the x-axis.");
    } else if (!yVar) {
      setError("Please select a variable for the y-axis.");
    }
    else {
      // Make a POST request to create a new statistic, with a randomly generated UUID
      const URL = "/Statistics/" + uuid();
      var request_data = new FormData();
      request_data.append('jcr:primaryType', 'lfs:Statistic');
      request_data.append('name', name);
      request_data.append('xVar', xVar);
      request_data.append('xVar@TypeHint', 'Reference');
      request_data.append('yVar', yVar);
      request_data.append('yVar@TypeHint', 'Reference');
      if (splitVar) {
        request_data.append('splitVar', splitVar);
        request_data.append('splitVar@TypeHint', 'Reference');
      }
      fetch( URL, { method: 'POST', body: request_data })
        .then( (response) => {
          setNumFetchRequests((num) => (num-1));
          if (response.ok) {
            // reset fields
            setXVar(null);
            setYVar(null);
            setSplitVar(null);
            setName('');
            setSubjectLabel('');
            setError();
            // successful callback to parent
            onSuccess();
            // close dialog
            onClose();
          } else {
            setError(response.statusText ? response.statusText : response.toString());
            return(Promise.reject(response));
          }
        })
      setNumFetchRequests((num) => (num+1));
    }
  }

  let initialize = () => {
    setInitialized(true);
    // Fetch the SubjectTypes
    fetch("/query?query=select * from [lfs:SubjectType]")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => {
        setAvailableSubjects(response["rows"]);
      })
      .catch(handleFetchError);
  }

  let handleFetchError = (response) => {
    setError(response.statusText ? response.statusText : response.toString());
    setAvailableSubjects([]);  // Prevent an infinite loop if data was not set
  };

  let onXChange = (filterableUUID) => {
    setXVar(filterableUUID);
  }

  let onYChange = (e) => {
    setSubjectLabel(e);
    setYVar(availableSubjects.filter((x) => x['label'] == e)[0]['jcr:uuid']);
  }

  let onSplitChange = (filterableUUID) => {
    setSplitVar(filterableUUID);
  }

  if (!initialized) {
    initialize();
  }

  const subjectTypeFilters = (
    <Grid item xs={10}>
        <Select
          value={(subjectLabel || "")}
          onChange={(event) => {onYChange(event.target.value)}}
          className={classes.subjectFilterInput}
          displayEmpty
          >
            <MenuItem value="" disabled>
              <span className={classes.filterPlaceholder}>Select Variable</span>
            </MenuItem>
            {(availableSubjects.map( (subjectType) =>
                <MenuItem value={subjectType.label} key={subjectType.label} className={classes.categoryOption}>{subjectType.label}</MenuItem>
            ))}
        </Select>
      </Grid>
  )

  return (
    <Dialog open={open} onClose={onClose}>
    <DialogTitle>Create New Statistic</DialogTitle>
    <DialogContent className={classes.NewFormDialog}>
      { error && <Typography color="error">{error}</Typography>}
      <Grid container alignItems='flex-end' spacing={2}>
        <Grid item xs={2}>
          <Typography>Name:</Typography>
        </Grid>
        <Grid item xs={10}>
          <TextField value={name} onChange={(event)=> { setName(event.target.value); }} className={classes.subjectFilterInput} placeholder="Enter Statistic Name"/>
        </Grid>
        <Grid item xs={2}>
          <Typography>X-axis:</Typography>
        </Grid>
        <Filters statisticFilters={true} parentHandler={onXChange}/>
        <Grid item xs={2}>
          <Typography>Y-axis:</Typography>
        </Grid>
        {subjectTypeFilters}
        <Grid item xs={2}>
          <Typography>Split:</Typography>
        </Grid>
        <Filters statisticFilters={true} parentHandler={onSplitChange}/>
      </Grid>
    </DialogContent>
    <DialogActions>
      <Button
          onClick={onClose}
          variant="contained"
          color="default"
          >
          Cancel
        </Button>
        <Button
          onClick={createStatistic}
          variant="contained"
          color="primary"
          >
          Create
        </Button>
    </DialogActions>
  </Dialog>
  )
}

export default withStyles(QuestionnaireStyle)(AdminStatistics);
