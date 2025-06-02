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
import { Link } from "react-router";
import { v4 as uuidv4 } from 'uuid';
import {
  Box,
  Button,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Typography
} from "@mui/material";
import ResponsiveDialog from "../components/ResponsiveDialog.jsx";
import AdminResourceListing from "../adminDashboard/AdminResourceListing.jsx";
import EditButton from "../dataHomepage/EditButton.jsx";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import Fields from "../questionnaireEditor/Fields.jsx";
import { camelCaseToWords } from "../questionnaireEditor/LabeledField.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";

/**
 * Create the MaterialTable cell contents for a given node. This generates a link to the
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
      let link = `../content.html/admin${questionnairePath}#${path}`;
      return <Link to={link} underline="hover">{label}</Link>
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

  const entryType = "Statistic";

  let columns = [
    {
      accessorKey: "name",
      header: "Name",
    },
    {
      accessorKey: "type",
      header: "Type",
    },
    {
      header: "X-axis",
      accessorFn: (row) => createTableCell(row.xVar),
      Cell: ({ row }) => (createTableCell(row.original.xVar)),
    },
    {
      header: "Y-axis",
      accessorFn: (row) => createTableCell(row.yVar),
      Cell: ({ row }) => (createTableCell(row.original.yVar)),
    },
    {
      header: "Split",
      accessorFn: (row) => createTableCell(row.splitVar),
      Cell: ({ row }) => (createTableCell(row.original.splitVar)),
    },
    {
      accessorKey: "order",
      header: "Order",
    },
  ]

  let makeActions = ({ row }) => (
          <Box sx={{ display: 'flex', flexWrap: 'nowrap', float: 'right'}}>
            <EditButton
              entryType={entryType}
              onClick={() => {setDialogOpen(true); setNewStat(false); setCurrentId(row.original["@name"]);}}
            />
            <DeleteButton
              entryPath={row.original["@path"]}
              entryName={row.original.name}
              onComplete={dialogSuccess}
              entryType={entryType}
            />
          </Box>
        )

  let dialogClose = () => {
    setDialogOpen(false);
  }

  // If a statistic was successfully added or deleted, perform fetch for new statistic
  let dialogSuccess = () => {
    setNumNewEntries((old) => (old+1));
  }

  return (
    <>
      <AdminResourceListing
        title="Statistics"
        buttonProps={{
          title: "Create new statistic",
          onClick: () => {
            setDialogOpen(true);
            setNewStat(true);
            setCurrentId();
          },
        }}
        columns={columns}
        entryType={entryType}
        tableActions={makeActions}
        updateData={numNewEntries}
      />
      <StatisticDialog
        open={dialogOpen}
        onClose={dialogClose}
        onSuccess={dialogSuccess}
        classes={classes}
        isNewStatistic={newStat}
        currentId={currentId}
      />
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
  const { onClose, onSuccess, open, isNewStatistic, currentId } = props;
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
        setError(`The ${camelCaseToWords(fieldName)} field is mandatory`);
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
          onSuccess?.();
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
            variant="outlined"
            >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={saveInProgress}
            >
            {isNewStatistic ? "Create" : "Save"}
          </Button>
      </DialogActions>
    </ResponsiveDialog>
  </form>
  )
}

export default AdminStatistics;
