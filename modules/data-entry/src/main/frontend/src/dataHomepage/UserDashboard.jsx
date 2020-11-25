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

import { loadExtensions } from "../uiextension/extensionManager";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { MODE_DIALOG } from "../dataHomepage/NewFormDialog.jsx"

import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Fab,
  Grid,
  Tooltip,
  Typography,
  withStyles
} from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";
import NewFormDialog from "./NewFormDialog";

async function getDashboardExtensions() {
  return loadExtensions("DashboardViews")
    .then(extensions => extensions.slice()
      .sort((a, b) => a["lfs:defaultOrder"] - b["lfs:defaultOrder"])
    )
}

async function getDashboardCreations() {
  return loadExtensions("DashboardCreation")
    .then(extensions => extensions.slice()
      .sort((a, b) => a["lfs:defaultOrder"] - b["lfs:defaultOrder"])
    )
}

// Component that renders the user's dashboard, with one LiveTable per questionnaire
// visible by the user. Each LiveTable contains all forms that use the given
// questionnaire.
function UserDashboard(props) {
  const { classes } = props;
  let [ dashboardExtensions, setDashboardExtensions ] = useState([]);
  let [ creationExtensions, setCreationExtensions ] = useState([]);
  let [ loading, setLoading ] = useState(true);
  let [ creationLoading, setCreationLoading ] = useState(true);
  let [ selectedCreation, setSelectedCreation ] = useState(-1);
  let [ open, setOpen ] = useState(false);

  useEffect(() => {
    getDashboardExtensions()
      .then(extensions => setDashboardExtensions(extensions))
      .catch(err => console.log("Something went wrong loading the user dashboard", err))
      .finally(() => setLoading(false));
    getDashboardCreations()
      .then(creations => {
        setCreationExtensions(creations);
      })
      .catch(err => console.log("Something went wrong loading the user dashboard", err))
      .finally(() => setCreationLoading(false));
  }, [])

  if (loading) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  return (
    <React.Fragment>
      <Grid container spacing={3}>
        {
          dashboardExtensions.map((extension) => {
            let Extension = extension["lfs:extensionRender"];
            return <Grid item lg={12} xl={6}>
              <Extension />
            </Grid>
          })
        }
      </Grid>
      <Dialog open={open} onClose={() => setOpen(false)}>
        <DialogTitle disableTypography>
          <Typography variant="h6" color="error" className={classes.dialogTitle}>Add</Typography>
        </DialogTitle>
        <DialogActions>
          <Button
            variant="contained"
            color="default"
            onClick={() => setOpen(false)}
            >
            Cancel
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={ () => {
              setOpen(false);
              setSelectedCreation(0);
            }}
            >
            Create Subject
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={ () => {
              setOpen(false);
              setSelectedCreation(1);
            }}
            >
            Create Form
          </Button>
        </DialogActions>
      </Dialog>
      <Tooltip title={"Add"} aria-label="add">
        <Fab
          color="primary"
          aria-label="add"
          onClick={() => setOpen(true)}
          disabled={creationLoading}
        >
          <AddIcon />
        </Fab>
      </Tooltip>
      {
        creationExtensions.map((extension, index) => {
          let Extension = extension["lfs:extensionRender"];
          return <Extension
            open={index === selectedCreation}
            onClose={() => setSelectedCreation(-1)}
            onSubmit={() => setSelectedCreation(-1)}
            // NewFormDialog specific argument
            mode={MODE_DIALOG}
            // NewSubjectDialog specific argument
            openNewSubject={true}
            />
        })
      }
    </React.Fragment>
  );
}

export default withStyles(QuestionnaireStyle)(UserDashboard);
