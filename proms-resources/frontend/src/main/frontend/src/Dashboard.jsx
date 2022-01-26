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
import QuestionnaireStyle from "./questionnaire/QuestionnaireStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

import {
  CircularProgress,
  Grid,
  Typography,
  withStyles,
} from "@material-ui/core";



async function getDashboardExtensions(name) {
  return loadExtensions("DashboardViews" + name)
    .then(extensions => extensions.slice()
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"])
    )
  // To do: also load the default dashboard if the extension point is invalid
}

// Component that renders the proms dashboard, with one LiveTable per questionnaire.
// Each LiveTable contains all forms that use the given questionnaire.
function PromsDashboard(props) {
  const { classes, theme } = props;
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

  if (loading || !visitInfo) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  return (
    <>
      <Typography variant="h4">{name}</Typography>
      <Typography variant="overline">{dashboardTitle}</Typography>
      <Grid container spacing={3}>
        {
          dashboardExtensions.map((extension, index) => {
            let Extension = extension["cards:extensionRender"];
            return <Grid item lg={12} xl={6} key={"extension-" + index} className={classes.dashboardEntry}>
              <Extension data={extension["cards:data"]} visitInfo={visitInfo} />
            </Grid>
          })
        }
      </Grid>
    </>
  );
}

export default withStyles(QuestionnaireStyle, {withTheme: true})(PromsDashboard);
