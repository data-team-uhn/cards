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

import { loadExtensions } from "../uiextension/extensionManager";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

import {
  CircularProgress,
  Grid,
  Typography,
  useMediaQuery
} from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import { useTheme } from '@mui/material/styles';

import ClinicForms from "./ClinicForms";
import ClinicVisits from "./ClinicVisits";

async function getDashboardExtensions(name) {
  return loadExtensions("DashboardViews" + name)
    .then(extensions => extensions.slice()
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"])
    )
  // To do: also load the default dashboard if the extension point is invalid
}

const useStyles = makeStyles(theme => ({
  dashboardTitle: {
    marginTop: theme.spacing(-4),
    marginRight: theme.spacing(4),
  },
  withMargin: {
    marginRight: "320px",
  },
  dashboardContainer: {
    marginTop: theme.spacing(2),
  },
  dashboardEntry: {
    "& > *": {
      height: "100%",
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
      padding: theme.spacing(3, 0, 1, 0),
    },
  },
}));

// Component that renders the clinic dashboard, with one LiveTable per questionnaire.
// Each LiveTable contains all forms that use the given questionnaire.
function ClinicDashboard(props) {
  let [ clinicId, setClinicId ] = useState();
  let [ title, setTitle ] = useState();
  let [ description, setDescription ] = useState();
  let [ surveysId, setSurveysId ] = useState("");
  let [ surveys, setSurveys ] = useState();
  let [ dashboardExtensions, setDashboardExtensions ] = useState([]);
  let [ defaultsLoading, setDefaultsLoading ] = useState(true);
  let [ extensionsLoading, setExtensionsLoading ] = useState(true);
  let [ visitInfo, setVisitInfo ] = useState();

  const [ dashboardConfig, setDashboardConfig ] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);
  const location = useLocation();

  // Fetch saved settings for time grouping tabs of dashboard boxes
  useEffect(() => {
    fetch(`/Survey/DashboardSettings.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setDashboardConfig(json);
      })
      .catch((response) => {
        console.log(`Loading the dashboard settings failed with error code ${response.status}: ${response.statusText}`);
      });
  }, []);

  // If there's an extra path segment, we use it to obtain the extension point
  // Otherwise default to "DashboardViews" (main dashboard)
  useEffect(() => setClinicId(location.pathname.split("/Dashboard/")?.[1] || ""), [location]);

  // At startup, load the visit information questionnaire to pass it to all extensions
  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, "/Questionnaires/Visit information.deep.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => setVisitInfo(json));
  }, []);

  // Load the extensions, if any
  useEffect(() => {
    if (!clinicId) return;
    getDashboardExtensions(clinicId)
      .then(extensions => setDashboardExtensions(extensions))
      .catch(err => console.log("Something went wrong loading the clinic dashboard", err))
      .finally(() => setExtensionsLoading(false));
  }, [clinicId])

  // Also load the clinic configuration...
  useEffect(() => {
    if (!clinicId) return;
    fetchWithReLogin(globalLoginDisplay, `/Survey/ClinicMapping/${clinicId}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setTitle(json.displayName || json.clinicName || clinicId);
        setDescription(json.description || "");
        setSurveysId(json.survey);
      })
  }, [clinicId])

  // ... and the surveys configured for the clinic
  useEffect(() => {
    if (!surveysId) {
      // if we have a clinic but no surveys, nothing more to load
      setDefaultsLoading(!!!clinicId);
      return;
    }
    fetchWithReLogin(globalLoginDisplay, `/Survey/${surveysId}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => setSurveys(
        Object.values(json || {})
          .filter(e => e["jcr:primaryType"] == "cards:QuestionnaireRef")
          .sort((a, b) => a.order - b.order)
      ))
      .finally(() => setDefaultsLoading(false));
  }, [surveysId]);

  const classes = useStyles();

  // Colors assigned to the dashboard widgets
  // If we have more widgets than colors, start reusing colors from the top
  const colors = [
    "#003366",
    "#f94900",
    "#ff9900",
    "#36b37e",
    "#00b8d9",
    "#3c78d8",
    "#974efd",
    "#9e4973",
  ];

  const getColor = (index) => (colors[index % colors.length])

  const theme = useTheme();
  const appbarExpanded = useMediaQuery(theme.breakpoints.up('md'));

  if (defaultsLoading || extensionsLoading || !visitInfo) {
    return (
      <Grid container justifyContent="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  return (
    <>
      <Typography variant="h4" className={classes.dashboardTitle + (appbarExpanded ? ' ' + classes.withMargin : '')}>{title}</Typography>
      { description && <Typography variant="overline">{description}</Typography>}
      <Grid container spacing={4} className={classes.dashboardContainer}>
        {/* Appointments view */}
        <Grid item xs={12} xl={6} key={`view-appointments-${clinicId}`} className={classes.dashboardEntry}>
          <ClinicVisits color={getColor(0)} visitInfo={visitInfo} clinicId={clinicId} dashboardConfig={dashboardConfig}/>
        </Grid>
        {/* Survey views */}
        { surveys?.map((s, index) => (
            <Grid item xs={12} xl={6} key={`view-survey-${clinicId}-${s["@name"]}`} className={classes.dashboardEntry}>
              <ClinicForms data={s} color={getColor(index + 1)} visitInfo={visitInfo} clinicId={clinicId} dashboardConfig={dashboardConfig}/>
            </Grid>
          ))
        }
        {/* Extensions */}
        {
          dashboardExtensions.map((extension, index) => {
            let Extension = extension["cards:extensionRender"];
            return <Grid item xs={12} xl={6} key={`extension-${clinicId}-${index}`} className={classes.dashboardEntry}>
              <Extension data={extension["cards:data"]} color={getColor(index)} visitInfo={visitInfo} clinicId={clinicId} dashboardConfig={dashboardConfig}/>
            </Grid>
          })
        }
      </Grid>
    </>
  );
}

export default ClinicDashboard;
