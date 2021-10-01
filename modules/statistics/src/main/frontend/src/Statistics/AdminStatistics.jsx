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
import React, { useContext, useEffect, useState } from "react";
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
  IconButton,
  Tooltip,
  Typography,
  withStyles,
} from "@material-ui/core";
import statisticsStyle from "./statisticsStyle.jsx";
import NewItemButton from "../components/NewItemButton.jsx";
import ResponsiveDialog from "../components/ResponsiveDialog.jsx";
import LiveTable from "../dataHomepage/LiveTable.jsx";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import Fields from "../questionnaireEditor/Fields.jsx";
import EditIcon from "@material-ui/icons/Edit";
import { formatIdentifier } from "../questionnaireEditor/EditorInput.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

/**
 * Create the LiveTable cell contents for a given node. This generates a link to the
 * given node (via its admin page), or returns the node's label if no valid link can be made.
 *
 * @param {Object} node The cards:SubjectType or cards:Question node to generate a link for
 */
function createTableCell(node) {
  // Subjects use label, questions use text
  if (!node) {
    return <></>;
  }

  if (node["jcr:primaryType"] == "cards:Question") {
    // For a question node, we can generate a link directly to the question
    let label = node.text;
    let path = node["@path"];
    try {
      let questionnairePath = /^(.*\/Questionnaires\/[^\/]+)/.exec(path)[0];
      let link = `/content.html/admin${questionnairePath}#${path}`;
      return <Link to={link}>{label}</Link>
    } catch {
      return label;
    }
  } else {
    return node.label || node.text;
  }

}

function EditStatisticButton(props) {
  const { onClick } = props;
  return(
    <Tooltip title={"Edit Statistic"}>
      <IconButton onClick={onClick}>
        <EditIcon />
      </IconButton>
    </Tooltip>
  )
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
      "format": (stat) => createTableCell(stat?.xVar),
    },
    {
      "key": "yVar",
      "label": "Y-axis",
      "format": (stat) => createTableCell(stat?.yVar),
    },
    {
      "key": "splitVar",
      "label": "Split",
      "format": (stat) => createTableCell(stat?.splitVar),
    },
    {
      "key": "order",
      "label": "Order",
      "format": "string",
    },
    {
      "key": "",
      "label": "Actions",
      "type": "actions",
      "format": (row) => (<>
                            <DeleteButton
                              entryPath={row["@path"]}
                              entryName={row.name}
                              onComplete={dialogSuccess}
                              entryType={"Statistic"}
                            />
                            <EditStatisticButton
                              onClick={() => {setDialogOpen(true); setNewStat(false); setCurrentId(row["@name"]);}}
                            />
                          </>),
    },
  ]

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
          <NewItemButton
            title="Create new statistic"
            onClick={() => {setDialogOpen(true); setNewStat(true); setCurrentId();}}
            inProgress={dialogOpen}
            />
        }
        />
        <CardContent>
          <LiveTable
            columns={columns}
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
  const [ existingData, setExistingData ] = useState(false);
  const [ error, setError ] = useState();
  const [ saveInProgress, setSaveInProgress ] = useState(false);
  const [ initialized, setInitialized ] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let statisticsSpecs = require('./Statistics.json');

  let reset = () => {
    // reset all fields
    setError();
    setExistingData(false);
    setInitialized(false);
  }

  let handleError = console.log;

  useEffect(() => {
    if (!open) {
      reset();
      return;
    }
    if (!isNewStatistic && currentId) {
      let fetchExistingData = () => {
        // We want to keep references the way they are, since reference inputs will expect their UUIDs
        fetch(`/Statistics/${currentId}.-dereference.json`)
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then(setExistingData)
          .then(() => setInitialized(true))
          .catch(handleError);
      };
      if (!existingData) {
        fetchExistingData();
      }
    } else {
      setInitialized(true);
    }
  }, [open]);

  let saveData = (event) => {
    event.preventDefault();

    let requestData = new FormData(event.currentTarget);
    requestData.append('jcr:primaryType', 'cards:Statistic');

    // Verify that the name, xVar, and yVar variables have been filled out
    let mandatoryFields = statisticsSpecs["//REQUIRED"];
    for (const fieldName of mandatoryFields) {
      if ((!requestData.has(fieldName)) || requestData.get(fieldName) == "") {
        setError(`The ${formatIdentifier(fieldName)} field is mandatory`);
        return;
      }
    }

    // If this statistic does not exist, we need to create a new path for it
    let URL = isNewStatistic ? "/Statistics/" + uuidv4() : "/Statistics/" + currentId;
    setSaveInProgress(true);
    fetchWithReLogin(globalLoginDisplay,
      URL,
      {
        method: 'POST',
        body: requestData
      })
      .then((response) => {
        if (response.ok) {
          setSaveInProgress(false);
          onSuccess && onSuccess();
          onClose();
        } else {
          setError(response);
        }
      })
      .catch(setError);
  }

  return (
    <form action='/Statistics' method='POST' onSubmit={saveData}>
      <ResponsiveDialog disablePortal open={open} onClose={onClose}>
      <DialogTitle>{isNewStatistic ? "Create New Statistic" : "Edit Statistic"}</DialogTitle>
      <DialogContent>
        { error && <Typography color="error">{error}</Typography>}
        <Grid container direction="column" spacing={2}>
          {
            // We don't want to load the Fields component until we are fully initialized
            // since otherwise the default values will be empty and cannot be assigned
            initialized && <Fields data={existingData || {}} JSON={statisticsSpecs} edit />
          }
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
            type="submit"
            variant="contained"
            color="primary"
            disabled={saveInProgress}
            >
            {isNewStatistic ? "Create" : "Save"}
          </Button>
      </DialogActions>
    </ResponsiveDialog>
  </form>
  )
}

export default withStyles(statisticsStyle)(AdminStatistics);
