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

import MaterialTable from "material-table";

import { loadExtensions } from "../uiextension/extensionManager";
import NewItemButton from "../components/NewItemButton.jsx";
import ResponsiveDialog from "../components/ResponsiveDialog"; // commons
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { MODE_DIALOG } from "../dataHomepage/NewFormDialog.jsx";

import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Typography,
  withStyles
} from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";

async function getDashboardExtensions() {
  return loadExtensions("DashboardViews")
    .then(extensions => extensions.slice()
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"])
    )
}

async function getMenuItems() {
  return loadExtensions("DashboardMenuItems")
    .then(extensions => extensions.slice()
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"])
    )
}

// Component that renders the user's dashboard, with one LiveTable per questionnaire
// visible by the user. Each LiveTable contains all forms that use the given
// questionnaire.
function UserDashboard(props) {
  const { classes, theme } = props;
  let [ dashboardExtensions, setDashboardExtensions ] = useState([]);
  let [ creationExtensions, setCreationExtensions ] = useState([]);
  let [ loading, setLoading ] = useState(true);
  let [ creationLoading, setCreationLoading ] = useState(true);
  let [ selectedCreation, setSelectedCreation ] = useState(-1);
  let [ selectedRow, setSelectedRow ] = useState(undefined);
  let [ open, setOpen ] = useState(false);
  let [ pageSize, setPageSize ] = useState(10);

  let onClose = () => {
    setSelectedCreation(-1);
    setSelectedRow(undefined);
    setOpen(false);
  }

  useEffect(() => {
    getDashboardExtensions()
      .then(extensions => setDashboardExtensions(extensions))
      .catch(err => console.log("Something went wrong loading the user dashboard", err))
      .finally(() => setLoading(false));
    getMenuItems()
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
      <Grid container spacing={4} className={classes.dashboardContainer}>
        {
          dashboardExtensions.map((extension, index) => {
            let Extension = extension["cards:extensionRender"];
            return <Grid item lg={12} xl={6} key={"extension-" + index} className={classes.dashboardEntry}>
              <Extension />
            </Grid>
          })
        }
      </Grid>
      <ResponsiveDialog title="New" width="xs" open={open} onClose={onClose}>
        <DialogContent dividers className={classes.dialogContentWithTable}>
          <MaterialTable
            columns={[
              { field: 'cards:extensionName' },
            ]}
            data={creationExtensions}
            options={{
              toolbar: false,
              header: false,
              pageSize: pageSize,
              paging: creationExtensions.length > 5,
              rowStyle: rowData => ({
                // /* It doesn't seem possible to alter the className from here */
                backgroundColor: (selectedRow && selectedRow["jcr:uuid"] === rowData["jcr:uuid"]) ? theme.palette.grey["200"] : theme.palette.background.default
              })
            }}
            onRowClick={(event, rowData) => {
              setSelectedRow(rowData);
            }}
            onChangeRowsPerPage={setPageSize}
            />
        </DialogContent>
        <DialogActions>
          <Button
            variant="contained"
            onClick={onClose}
            >
            Cancel
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={ () => {
              setOpen(false);
              setSelectedCreation(creationExtensions.indexOf(selectedRow));
            }}
            disabled={typeof(selectedRow) === "undefined"}
            >
            Next
          </Button>
        </DialogActions>
      </ResponsiveDialog>
      <NewItemButton
        title="New..."
        onClick={() => setOpen(true)}
        inProgress={creationLoading}
      />
      {
        creationExtensions.map((extension, index) => {
          let Extension = extension["cards:extensionRender"];
          return <Extension
            open={index === selectedCreation}
            onClose={onClose}
            onSubmit={onClose}
            // NewFormDialog specific argument
            mode={MODE_DIALOG}
            // NewSubjectDialog specific argument
            openNewSubject={true}
            key={"extensionDialog-" + index}
            />
        })
      }
    </React.Fragment>
  );
}

export default withStyles(QuestionnaireStyle, {withTheme: true})(UserDashboard);
