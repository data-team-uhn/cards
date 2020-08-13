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
import { Button, Card, CardContent, Dialog, DialogActions, DialogContent, Grid, withStyles, TextField, Typography } from "@material-ui/core";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import Filters from "./Filters";

function AdminStatistics(props) {
  const { classes } = props;
  let [statistics, setStatistics] = useState([1, 2, 3]); // each statistic becomes one chart. TODO: fetch statistics
  const [ dialogOpen, setDialogOpen ] = useState(false);

  let dialogClose = () => {
    setDialogOpen(false);
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
      <NewStatisticDialog open={dialogOpen} onClose={dialogClose} classes={classes}/>
    </React.Fragment>
  );
}

function NewStatisticDialog(props) {
  const { onClose, open, classes } = props;
  const [ numFetchRequests, setNumFetchRequests ] = useState(0);
  const [availableFields, setAvailableFields] = useState([]);
  const [availableSubjects, setAvailableSubjects] = useState([]);
  const [ name, setName ] = useState();
  const [ xVar, setXVar ] = useState();
  const [ yVar, setYVar ] = useState();
  const [ splitVar, setSplitVar ] = useState();
  
  // TODO: fetch all subject types --> availableSubjects (yVar)

  let createStatistic = () => {
    // Make a POST request to create a new statistic, with a randomly generated UUID
    const URL = "/Statistics/" + uuid();
    var request_data = new FormData();
    request_data.append('jcr:primaryType', 'lfs:Statistic');
    request_data.append('name', name);
    request_data.append('xVar', xVar);
    request_data.append('xVar@TypeHint', 'Reference');
    request_data.append('yVar', yVar);
    request_data.append('yVar@TypeHint', 'Reference');
    request_data.append('splitVar', splitVar);
    request_data.append('splitVar@TypeHint', 'Reference');
    fetch( URL, { method: 'POST', body: request_data })
      .then( (response) => {
        setNumFetchRequests((num) => (num-1));
        if (response.ok) {
          // FIX: rerender content after this
          console.log("success!")
          onClose();
        } else {
          return(Promise.reject(response));
          // TODO: error occurs if no forms have been created yet! not sure why (fix)
        }
      })
    setNumFetchRequests((num) => (num+1));

    // TODO: clear state on submit / reset state when opening
  }

  let onXChange = (filterableUUID) => {
    setXVar(filterableUUID);
  }

  let onYChange = (filterableUUID) => {
    setYVar(filterableUUID);
  }

  let onSplitChange = (filterableUUID) => {
    setSplitVar(filterableUUID);
  }

  return (
    <Dialog open={open} onClose={onClose}>
    <DialogContent className={classes.NewFormDialog}>
      {/* use select from filters! */}
      <Grid container alignItems='flex-end' spacing={2}>
        <Grid item xs={6}><Typography>Name:</Typography></Grid>
        <Grid item xs={6}><TextField value={name} onChange={(event)=> { setName(event.target.value); }} /></Grid>
        <Grid item xs={6}><Typography>X-axis:</Typography></Grid>
        <Filters statisticFilters={true} parentHandler={onXChange}/>
        <Grid item xs={6}><Typography>Y-axis:</Typography></Grid>
        <Filters statisticFilters={true} parentHandler={onYChange}/>
        <Grid item xs={6}><Typography>Split:</Typography></Grid>
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
