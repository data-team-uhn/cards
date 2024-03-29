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
import { useHistory } from "react-router-dom";
import AdminScreen from "./AdminScreen.jsx";

import {
  CircularProgress,
  Grid,
  ListItem,
  ListItemIcon,
  ListItemText
} from "@mui/material";

// function to get the routes for the admin dashboard, also used in the navbar
async function getAdminRoutes() {
  return loadExtensions("AdminDashboard")
    .then(extensions => extensions.slice()
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"])
    )
}

function AdminDashboard(props) {
  let [ adminRoutes, setAdminRoutes ] = useState([]);
  let [ loading, setLoading ] = useState(true);

  let history = useHistory();

  useEffect(() => {
    getAdminRoutes()
      .then(routes => setAdminRoutes(routes))
      .catch(err => console.log("Something went wrong loading the admin dashboard", err))
      .finally(() => setLoading(false));
  }, [])

  if (loading) {
    return (
      <Grid container justifyContent="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  return (
    <AdminScreen disableBreadcrumb>
      <Grid container spacing={2}>
        { adminRoutes.map((route) => {
            const EntryIcon = route["cards:icon"];
            return (
              <Grid item
                key={route["cards:targetURL"]}
                xs={12} md={6} xl={4}
              >
                <ListItem button
                  onClick={() => history.push(route["cards:targetURL"])}
                >
                    <ListItemIcon>
                      <EntryIcon fontSize="large"/>
                    </ListItemIcon>
                    <ListItemText
                      primary={route["cards:extensionName"]}
                      secondary={route["cards:hint"]}
                    />
                </ListItem>
              </Grid>
            )
          })
        }
      </Grid>
    </AdminScreen>
  );
}

export default AdminDashboard;
