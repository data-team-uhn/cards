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

import MaterialReactTable from "material-react-table";

import { loadExtensions } from "../uiextension/extensionManager";
import NewItemButton from "../components/NewItemButton.jsx";
import ResponsiveDialog from "../components/ResponsiveDialog"; // commons
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { MODE_DIALOG } from "../dataHomepage/NewFormDialog.jsx";

import {
  Button,
  CircularProgress,
  DialogActions,
  DialogContent,
  Grid,
} from "@mui/material";
import withStyles from '@mui/styles/withStyles';

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
      <Grid container justifyContent="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  return (
    <React.Fragment>
      <Grid container spacing={4} className={classes.dashboardContainer}>
        {
          dashboardExtensions.map((extension, index) => {
            let Extension = extension["cards:extensionRender"];
            return <Grid item xs={12} xl={6} key={"extension-" + index} className={classes.dashboardEntry}>
              <Extension />
            </Grid>
          })
        }
      </Grid>
      <ResponsiveDialog title="New" width="xs" open={open} onClose={onClose}>
        <DialogContent dividers className={classes.dialogContentWithTable}>
          <MaterialReactTable
            enableColumnActions={false}
            enableColumnFilters={false}
            enableSorting={false}
            enableGrouping={false}
            enableToolbarInternalActions={false}
            enableTopToolbar={creationExtensions.length > 5}
            enableBottomToolbar={creationExtensions.length > 5}
            muiTableHeadProps={{ sx: { display: creationExtensions.length < 5 ? 'none' : 'contents' } }}
            enablePagination={creationExtensions.length > 5}
            initialState={{ showGlobalFilter: (creationExtensions.length > 5),
                            pagination: { pageSize: 10, pageIndex: 0 }
                         }}
            columns={[
              { accessorKey: 'cards:extensionName' },
            ]}
            data={creationExtensions}
            muiTableBodyRowProps={({ row }) => ({
              sx: {
                cursor: 'pointer',
                backgroundColor:
                  selectedRow && selectedRow["jcr:uuid"] === row.original["jcr:uuid"] ? theme.palette.grey["200"] : theme.palette.background.default
              },
              onClick: (event) => {
                  setSelectedRow(row.original);
                },
            })}
            muiTableBodyCellProps={({ cell }) => ({
              sx: {
                fontSize: '1rem'
              },
            })}
          />
        </DialogContent>
        <DialogActions>
          <Button
            variant="outlined"
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
