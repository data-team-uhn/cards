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
import { v4 as uuidv4 } from 'uuid';
import { Button, Card, CardContent, Dialog, DialogActions, DialogContent, Grid, withStyles, Typography } from "@material-ui/core";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function AdminStatistics(props) {
  const { classes } = props;
  let [statistics, setStatistics] = useState([1, 2, 3]); // each statistic becomes one chart
  const [ dialogOpen, setDialogOpen ] = useState(false);

  //TODO:
  // use filters
  // y-axis: subjecttype

  // OR just get all variable names. might still be able to use filters

  //TODO: right now, questionnaire path is passed from userdash --> livetable --> filters. can filters be passed ALL questionnaires?
    // yes, just don't specify a questionnaire prop!

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

  // add selected name, x, y, split to state
  
  // fetch all variables in forms --> availableFields (xVar, splitVar)
  // fetch all subject types --> availableSubjects (yVar)

  let createStatistic = () => {
    // Make a POST request to create a new statistic, with a randomly generated UUID

    //HOW CAN i test this (reference)

    const URL = "/Statistics/" + uuidv4();
    var request_data = new FormData();
    request_data.append('jcr:primaryType', 'cards:Statistic');
    request_data.append('name', 'example name');
    request_data.append('xVar', 'x');
    request_data.append('yVar', 'y');
    request_data.append('splitVar', '');
    fetch( URL, { method: 'POST', body: request_data })
      .then( (response) => {
        setNumFetchRequests((num) => (num-1));
        if (response.ok) {
          // FIX: rerender content after this
          console.log("success!")
          onClose();
        } else {
          return(Promise.reject(response));
        }
      })
    setNumFetchRequests((num) => (num+1));
  }

  return (
    <Dialog open={open} onClose={onClose}>
    <DialogContent>
      {/* use select from filters! */}
      <Typography variant="body2" component="p">Name:</Typography>
      <Typography variant="body2" component="p">X-axis:</Typography>
      <Typography variant="body2" component="p">Y-axis:</Typography>
      <Typography variant="body2" component="p">Split:</Typography>
    </DialogContent>
    <DialogActions>
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
