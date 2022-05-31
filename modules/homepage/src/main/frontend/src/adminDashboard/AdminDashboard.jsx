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
import { NavLink, Route } from "react-router-dom";
import adminStyle from "./AdminDashboardStyle.jsx";

import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CircularProgress,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText, 
  Typography,
} from "@mui/material";

import withStyles from '@mui/styles/withStyles';

// function to get the routes for the admin dashboard, also used in the navbar
async function getAdminRoutes() {
  return loadExtensions("AdminDashboard")
    .then(extensions => extensions.slice()
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"])
    )
}

function AdminDashboard(props) {
  const { classes } = props;
  let [ adminRoutes, setAdminRoutes ] = useState([]);
  let [ loading, setLoading ] = useState(true);

  useEffect(() => {
    getAdminRoutes()
      .then(routes => setAdminRoutes(routes))
      .catch(err => console.log("Something went wrong loading the admin dashboard", err))
      .finally(() => setLoading(false));
  }, [])

  if (loading) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  return (
    <Card>
      <CardHeader
        title={
          <Button>
            Administration
          </Button>
        }
      />
      <CardContent>
        <List>
          {
            adminRoutes.map((route) => {
              const EntryIcon = route["cards:icon"];
              return (
                <NavLink
                  to={route["cards:targetURL"]}
                  key={route["cards:targetURL"]}
                  className={classes.listItem}
                >
                  <ListItem button className={classes.listButton}>
                    <ListItemIcon>
                      <EntryIcon fontSize="large"/>
                    </ListItemIcon>
                    <ListItemText
                      className={classes.listText}
                      primary={<Typography variant="body1">{route["cards:extensionName"]}</Typography>}
                      secondary={<Typography variant="body2">{route["cards:hint"]}</Typography>}
                      disableTypography={true}
                    />
                  </ListItem>
                </NavLink>
              )
            })
          }
        </List>
      </CardContent>
    </Card>
  );
}

export default withStyles(adminStyle)(AdminDashboard);
