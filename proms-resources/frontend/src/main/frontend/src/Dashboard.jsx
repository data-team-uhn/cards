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
import React, { useState, useEffect, useContext } from "react";

import { useLocation } from 'react-router-dom';

import MaterialTable from "material-table";

import { loadExtensions } from "./uiextension/extensionManager";
import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

import {
  CircularProgress,
  Grid,
  Typography,
  makeStyles,
} from "@material-ui/core";



async function getDashboardExtensions(name) {
  return loadExtensions("DashboardViews" + name)
    .then(extensions => extensions.slice()
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"])
    )
  // To do: also load the default dashboard if the extension point is invalid
}

const useStyles = makeStyles(theme => ({
  dashboardName: {
    marginTop: theme.spacing(-4),
  },
  dashboardEntry: {
    "& > *": {
      height: "100%",
      marginTop: theme.spacing(4),
    },
    "& .MuiCardHeader-root" : {
      paddingBottom: 0,
    },
    "& .MuiCardHeader-avatar" : {
      marginTop: theme.spacing(-.5),
    },
    "& .MuiTab-root": {
      width: "auto",
      minWidth: theme.spacing(10),
      paddingBottom: theme.spacing(1),
      paddingLeft: theme.spacing(2),
      paddingRight: theme.spacing(2),
      textTransform: "none",
      fontWeight: "300",
    },
    "& .MuiTabs-indicator" : {
      height: theme.spacing(.5),
    },
    "& .MuiCardContent-root": {
      padding: theme.spacing(3, 0),
    },
  },
}));

// Component that renders the proms dashboard, with one LiveTable per questionnaire.
// Each LiveTable contains all forms that use the given questionnaire.
function PromsDashboard(props) {
  let [ name, setName ] = useState();
  let [ dashboardTitle, setDashboardTitle ] = useState();
  let [ dashboardExtensions, setDashboardExtensions ] = useState([]);
  let [ loading, setLoading ] = useState(true);
  let [ visitInfo, setVisitInfo ] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);
  const location = useLocation();

  // If there's an extra path segment, we use it to obtain the extension point
  // Otherwise default to "DashboardViews" (main dashboard)
  useEffect(() => setName(location.pathname.split("/Dashboard/")?.[1] || ""), [location]);

  // At startup, load the visit information questionnaire to pass it to all extensions
  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, "/Questionnaires/Visit information.deep.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => setVisitInfo(json));
  }, []);

  // Also load all extensions
  useEffect(() => {
    getDashboardExtensions(name)
      .then(extensions => setDashboardExtensions(extensions))
      .catch(err => console.log("Something went wrong loading the proms dashboard", err))
      .finally(() => setLoading(false));
  }, [name])

  // And the extension point definition to obtain its name
  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, `/apps/cards/ExtensionPoints/DashboardViews${name}.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => setDashboardTitle(json?.["cards:extensionPointName"]));
  }, [name]);

  const classes = useStyles();

  // Colors assigned to the dashboard widgets
  // If we have more widgets than colors, start reusing golors from the top
  const colors = [
    "#a61c00",
    "#f94900",
    "#ff9900",
    "#36b37e",
    "#00b8d9",
    "#3c78d8",
    "#974efd",
    "#9e4973",
  ];

  const getColor = (index) => (colors[index % colors.length])

  if (loading || !visitInfo) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  return (
    <>
      <Typography variant="h4" className={classes.dashboardName}>{name}</Typography>
      <Typography variant="overline">{dashboardTitle}</Typography>
      <Grid container spacing={3}>
        {
          dashboardExtensions.map((extension, index) => {
            let Extension = extension["cards:extensionRender"];
            return <Grid item lg={12} xl={6} key={"extension-" + index} className={classes.dashboardEntry}>
              <Extension data={extension["cards:data"]} color={getColor(index)} visitInfo={visitInfo} />
            </Grid>
          })
        }
      </Grid>
    </>
  );
}

export default PromsDashboard;
