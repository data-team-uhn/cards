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
import { loadRemoteComponents, loadRemoteIcons, loadContentNodes } from "../themePage/routes";
import { NavLink, Route } from "react-router-dom";
import adminStyle from "./AdminDashboardStyle.jsx";

import { CircularProgress, Grid, Typography, List, ListItem, ListItemText, ListItemIcon, withStyles, Card, CardContent, CardHeader, Button } from "@material-ui/core";

// function to get the routes for the admin dashboard
export function getAdminRoutes(pathPrefix) {
  return new Promise(function(resolve, reject) {
    let adminRoutes = [];

    let handleRoutes = (uixData) => {
      var routes = adminRoutes.slice();
      uixData.sort((firstEl, secondEl) => {return firstEl.order - secondEl.order;});
      for (var id in uixData) {
        var uixDatum = uixData[id];
        routes.push({
          path: uixDatum.path,
          name: uixDatum.name,
          icon: uixDatum.icon,
          component: uixDatum.reactComponent,
          hint: uixDatum.hint,
          layout: pathPrefix
        });
      }
      if (routes) {resolve(routes)}
      else reject(Error("Something went wrong"))
    }

    loadContentNodes("AdminDashboard")
    .then(loadRemoteComponents)
    .then(loadRemoteIcons)
    .then(handleRoutes)
    .catch(function(err) {
      console.log("Something went wrong: " + err);
    });
  })
}

function AdminDashboard(props) {
  const { classes } = props;
  let [ adminRoutes, setAdminRoutes ] = useState([]);
  let [ loading, setLoading ] = useState(true);

  let pathPrefix = "/content.html/admin"; //admin is the url for this component

  useEffect(() => {
    getAdminRoutes(pathPrefix)
    .then(function(response) {
      setAdminRoutes(response);
      setLoading(false);
    })
    .catch(function(err) {
      console.log("Something went wrong: " + err);
    });
  }, [])

  if (loading) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  // render either the list of items in the admin dashboard or an item
  return (
    <div>
      <Route exact path={pathPrefix} component={() => <AdminDashboardDefault adminRoutes={adminRoutes} classes={classes}/>}/>
      {adminRoutes.map((prop, key) => {
        return (
          <Route
            path={prop.layout + prop.path}
            component={prop.component}
            key={key}
          />
        );
      })}
    </div>
  );
}

function AdminDashboardDefault(props) {
  const { adminRoutes, classes } = props;

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
              return (
                <NavLink
                  to={route.layout + route.path}
                  key={route.path}
                  className={classes.listItem}
                >
                  <ListItem button className={classes.listButton}>
                    <ListItemIcon>
                      <route.icon fontSize="large"/>
                    </ListItemIcon>
                    <ListItemText
                      className={classes.listText}
                      primary={<Typography variant="body1">{route.name}</Typography>}
                      secondary={<Typography variant="caption">{route.hint}</Typography>}
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
