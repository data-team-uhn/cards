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
import { Link } from "react-router-dom";
import { v4 as uuidv4 } from 'uuid';
import { 
  Button, 
  Card, 
  CardContent,
  CardHeader,
  Dialog, 
  DialogActions, 
  DialogContent, 
  DialogTitle,
  Grid,
  withStyles, 
  MenuItem, 
  Select, 
  TextField,
  Typography 
} from "@material-ui/core";
import Filters from "../dataHomepage/Filters.jsx";
import statisticsStyle from "./statisticsStyle.jsx";
import NewItemComponent from "../components/NewItemButton.jsx";
import LiveTable from "../dataHomepage/LiveTable.jsx";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import EditButton from "../dataHomepage/EditButton.jsx";
import Fields from "../questionnaireEditor/Fields.jsx";

/**
 * Createa the LIveTable cell contents for a given node. This generates a link to the
 * given node (via its admin page), or returns the node's label if no valid link can be made.
 *
 * @param {Object} node The lfs:SubjectType or lfs:Question node to generate a link for
 */
function CreateTableCell(node) {
  // Subjects use label, questions use text
  if (!node) {
    return <></>;
  }

  if (node["jcr:primaryType"] == "lfs:Question") {
    // For a question node, we can generate a link directly to the question
    let label = node.text;
    let path = node["@path"];
    try {
      let questionnairePath = /(.+)\//.exec(path)[0];
      let link = `/content.html/admin${questionnairePath}#${path}`;
      return <Link to={link}>{label}</Link>
    } catch {
      return label;
    }
  } else {
    return node.label || node.text;
  }

}

function AdminStatistics(props) {
  const { classes } = props;
  const [ dialogOpen, setDialogOpen ] = useState(false);
  // Count the number of new entries, so we can force refreshing of the LiveTable when necessary
  const [ numNewEntries, setNumNewEntries ] = useState(0);
  // If stat should be created or edited
  const [ newStat, setNewStat ] = useState(true);
  const [ currentId, setCurrentId ] = useState();

  let columns = [
    {
      "key": "name",
      "label": "Name",
      "format": "string",
    },
    {
      "key": "type",
      "label": "Type",
      "format": "string",
    },
    {
      "key": "xVar",
      "label": "X-axis",
      "format": (stat) => CreateTableCell(stat?.xVar),
    },
    {
      "key": "yVar",
      "label": "Y-axis",
      "format": (stat) => CreateTableCell(stat?.yVar),
    },
    {
      "key": "splitVar",
      "label": "Split",
      "format": (stat) => CreateTableCell(stat?.splitVar),
    },
    {
      "key": "order",
      "label": "Order",
      "format": "string",
    },
  ]
  const actions = [
    DeleteButton,
    EditButton
  ];

  let dialogClose = () => {
    setDialogOpen(false);
  }

  // If a statistic was successfully added or deleted, perform fetch for new statistic
  let dialogSuccess = () => {
    setNumNewEntries((old) => (old+1));
  }

  return (
    <>
      <Card>
       <CardHeader
        title={
          <Button className={classes.cardHeaderButton}>
            Statistics
          </Button>
        }
        action={
          <NewItemComponent
            title="Create new statistic"
            onClick={() => {setDialogOpen(true); setNewStat(true); setCurrentId();}}
            inProgress={false}
            />
        }
        classes={{
          action: classes.newFormButtonHeader
        }}
        />
        <CardContent>
          <LiveTable
            columns={columns}
            actions={actions}
            entryType={"Statistic"}
            admin={true}
            updateData={numNewEntries}
            />
        </CardContent>
      </Card>
      <StatisticDialog open={dialogOpen} onClose={dialogClose} classes={classes} onSuccess={dialogSuccess} isNewStatistic={newStat} currentId={currentId}/>
    </>
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

  let statisticsSpecs = require('./Statistics.json');
  let [ userInput, setUserInput ] = useState({});

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
    setUserInput({});
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
      setCurrentUrl("/Statistics/" + uuidv4());
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
      xVar.split(",").forEach((variable) => request_data.append('xVar', variable));
      request_data.append('xVar@TypeHint', 'Reference');
      request_data.append('yVar', yVar);
      request_data.append('yVar@TypeHint', 'Reference');
      if (splitVar) {
        splitVar.split(",").forEach((variable) => request_data.append('splitVar', variable));
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

  let test = require('./Statistics.json');
  console.log(test);

  return (
    <Dialog open={open} onClose={onClose}>
    <DialogTitle>{isNewStatistic ? "Create New Statistic" : "Edit Statistic"}</DialogTitle>
    <DialogContent>
      { error && <Typography color="error">{error}</Typography>}
      <Grid container direction="column" spacing={2}>
        <Fields data={{}} JSON={require('./Statistics.json')} edit={true} />
        {/*Object.keys(statisticsSpecs).map((spec) => {
          console.log(statisticsSpecs[spec]);
          return <>
            <Grid item xs={2}>
              <Typography>{spec}:</Typography>
            </Grid>
            <Grid item xs={10}>
              <>Text: {statisticsSpecs[spec].toString()}</>
            </Grid>
          </>
        })*/
        /*<Grid item xs={2}>
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
        </Grid>*/}
        {/*<Filters statisticFilters={true} parentHandler={onSplitChange} statisticFiltersValue={splitVarLabel} />*/}
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
