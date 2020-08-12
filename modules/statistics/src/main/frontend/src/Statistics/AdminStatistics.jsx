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
import React, { useState, useEffect } from "react";
import { v4 as uuid } from 'uuid';
import { 
  Button, 
  Card, 
  CardContent,
  CardHeader,
  Dialog, 
  DialogActions, 
  DialogContent, 
  DialogTitle,
  Fab,
  Grid,
  IconButton,
  withStyles, 
  MenuItem, 
  Select, 
  TextField, 
  Tooltip,
  Typography 
} from "@material-ui/core";
import CreateIcon from "@material-ui/icons/Create";
import Filters from "../dataHomepage/Filters.jsx";
import statisticsStyle from "./statisticsStyle.jsx";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";


function AdminStatistics(props) {
  const { classes } = props;
  // This holds the statistics data, once it is received from the server
  let [statistics, setStatistics] = useState();
  const [ dialogOpen, setDialogOpen ] = useState(false);
  const [ error, setError ] = useState();
  const [ message, setMessage ] = useState();
  // If stat should be created or edited
  const [ newStat, setNewStat ] = useState(true);
  const [ currentId, setCurrentId ] = useState();

  // fetch all statistics
  let fetchStatistics = () => {
      fetch("/query?query=select * from [lfs:Statistic]")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => {
        if (response.totalrows == 0) {
          setMessage("No statistics have been added yet."); // displayed to user
        }
        else {
          setMessage(null);
          setStatistics(response["rows"]);
        }
      })
      .catch(handleError);
  };

  // Callback method for the `fetchStatistics` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
    setStatistics([]);  // Prevent an infinite loop if data was not set
  };

  // If the data has not yet been fetched, initiate
  if (!statistics) {
    fetchStatistics();
  }

  // If an error was returned, do not display statistics at all, but report the error
  if (error) {
    return (
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining statistics: {error.status} {error.statusText ? error.statusText : error.toString()}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let dialogClose = () => {
    setDialogOpen(false);
  }

  // If a statistic was successfully added or deleted, perform fetch for new statistic
  let dialogSuccess = () => {
    fetchStatistics();
  }

  return (
    <Grid container spacing={3}>
      {!message && statistics ? (statistics.map((stat) => {
        return(
          <Grid item lg={12} xl={6} key={stat["@path"]}>
            <Card>
              <CardHeader
                action={
                  <div>
                    <Tooltip aria-label="Edit" title="Edit Statistic">
                      <IconButton component="span" onClick={() => {setDialogOpen(true); setNewStat(false); setCurrentId(stat["@name"]);}}>
                        <CreateIcon />
                      </IconButton>
                    </Tooltip>
                    <DeleteButton
                      entryPath={stat["@path"]}
                      entryName={"Statistic: " + stat["name"]}
                      entryType={"Statistic"}
                      warning={stat ? stat["@referenced"] : false}
                      onComplete={dialogSuccess}
                    />
                  </div>
                }
              />
              <CardContent>
                <Grid container alignItems='flex-end' spacing={2}>
                  <Grid item xs={12}><Typography variant="body2" component="p">Name: {stat.name}</Typography></Grid>
                  <Grid item xs={12}><Typography variant="body2" component="p">X-axis: {stat.xVar.text}</Typography></Grid>
                  <Grid item xs={12}><Typography variant="body2" component="p">Y-axis: {stat.yVar.label}</Typography></Grid>
                  <Grid item xs={12}><Typography variant="body2" component="p">Split: {stat.splitVar ? stat.splitVar.text : "none"}</Typography></Grid>
                </Grid>
              </CardContent>
            </Card>
          </Grid>
        )
      })) : (
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography>{message}</Typography>
            </CardContent>
          </Card>
        </Grid>
        )
      }
      <Grid item xs={12}>
        <Button onClick={() => {setDialogOpen(true); setNewStat(true); setCurrentId();}}>New Statistic</Button>
      </Grid>
      <StatisticDialog open={dialogOpen} onClose={dialogClose} classes={classes} onSuccess={dialogSuccess} isNewStatistic={newStat} currentId={currentId}/>
    </Grid>
  );
}

/**
 * Statistic Dialog
 * @param {func} onClose callback for when dialog is closed
 * @param {func} onSuccess callback for when statistic is created or edited successfully
 * @param {bool} open true if dialog is open
 * @param {bool} isNewsStatistic true if statistic is being created, false if being edited
 * @param {string} currentId uuid of statistic to be edited (pre-existing)
 */
function StatisticDialog(props) {
  const { onClose, onSuccess, open, classes, isNewStatistic, currentId } = props;
  const [ numFetchRequests, setNumFetchRequests ] = useState(0);
  const [ availableSubjects, setAvailableSubjects ] = useState([]);
  const [ existingData, setExistingData ] = useState(false);
  const [ initialized, setInitialized ] = useState(false);
  const [ error, setError ] = useState();
  const [ currentUrl, setCurrentUrl ] = useState();

  const [ name, setName ] = useState('');
  const [ xVar, setXVar ] = useState();
  const [ yVar, setYVar ] = useState();
  const [ splitVar, setSplitVar ] = useState();
  const [ yVarLabel, setYVarLabel ] = useState('');
  const [ xVarLabel, setXVarLabel ] = useState('');
  const [ splitVarLabel, setSplitVarLabel ] = useState('');

  let reset = () => {
    // reset all fields
    setXVar(null);
    setYVar(null);
    setSplitVar(null);
    setName('');
    setYVarLabel('');
    setXVarLabel('');
    setSplitVarLabel('');
    setError();
    setExistingData(false);
  }

  useEffect(() => {
    if (!open) {
      reset();
    }
    if (!isNewStatistic && currentId) { // TO FIX: date of birth appears twice (in 2 diff forms), so it has 2 uuids.
      setCurrentUrl("/Statistics/" + currentId);
      let fetchExistingData = () => {
        fetch(`/Statistics/${currentId}.deep.json`)
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then(handleResponse)
          .catch(handleFetchError);
      };
      let handleResponse = (json) => {
        // if data alread exists, fill out fields
        setExistingData(true);
        setName(json.name);
        onYChange(json.yVar.label);
        onXChange(json.xVar, true);
        if (json.splitVar) {
          onSplitChange(json.splitVar, true);
        }
      };
      if (!existingData) {
        fetchExistingData();
      }
    } else {
      setCurrentUrl("/Statistics/" + uuid());
    }
  }, [open])

  let saveStatistic = () => {
    // Handle unfilled form errors
    if (!name) {
      setError("Please enter a name for this statistic.");
    } else if (!xVar) {
      setError("Please select a variable for the x-axis.");
    } else if (!yVar) {
      setError("Please select a variable for the y-axis.");
    }
    else {
      const URL = currentUrl;
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
            reset();
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
    setExistingData([]);
  };

  // update each
  let onXChange = (e, onLoad) => {
    if (onLoad) {
      // this is called if previous data is being loaded
      setXVarLabel(e['@name']);
      setXVar(e['jcr:uuid']);
    } else {
      setXVar(e);
    }
  }

  let onYChange = (e) => {
    setYVarLabel(e);
    setYVar(availableSubjects.filter((x) => x['label'] == e)[0]['jcr:uuid']);
  }

  let onSplitChange = (e, onLoad) => {
    if (onLoad) {
      // this is called if previous data is being loaded
      setSplitVarLabel(e['@name']);
      setSplitVar(e['jcr:uuid']);
    } else {
      setSplitVar(e);
    }
  }

  if (!initialized) {
    initialize();
  }

  // 'filter' for y-axis
  const subjectTypeFilters = (
    <Grid item xs={10}>
        <Select
          value={(yVarLabel || "")}
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
    <DialogTitle>{isNewStatistic ? "Create New Statistic" : "Edit Statistic"}</DialogTitle>
    <DialogContent>
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
        <Filters statisticFilters={true} parentHandler={onXChange} statisticFiltersValue={xVarLabel} />
        <Grid item xs={2}>
          <Typography>Y-axis:</Typography>
        </Grid>
        {subjectTypeFilters}
        <Grid item xs={2}>
          <Typography>Split:</Typography>
        </Grid>
        <Filters statisticFilters={true} parentHandler={onSplitChange} statisticFiltersValue={splitVarLabel} />
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
          onClick={saveStatistic}
          variant="contained"
          color="primary"
          >
          {isNewStatistic ? "Create" : "Save"}
        </Button>
    </DialogActions>
  </Dialog>
  )
}

export default withStyles(statisticsStyle)(AdminStatistics);
