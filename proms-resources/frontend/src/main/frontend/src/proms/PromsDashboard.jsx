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

import { loadExtensions } from "../uiextension/extensionManager";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

import { CircularProgress, Grid, Typography, useMediaQuery } from "@material-ui/core";

import makeStyles from '@material-ui/styles/makeStyles';

import { useTheme, styled } from '@material-ui/core/styles';

import PromsView from "./PromsView";
import VisitView from "./VisitView";

const PREFIX = 'PromsDashboard';

const classes = {
  dashboardTitle: `${PREFIX}-dashboardTitle`,
  withMargin: `${PREFIX}-withMargin`,
  dashboardContainer: `${PREFIX}-dashboardContainer`,
  dashboardEntry: `${PREFIX}-dashboardEntry`
};

// TODO jss-to-styled codemod: The Fragment root was replaced by div. Change the tag if needed.
const Root = styled('div')((
  {
    theme
  }
) => ({
  [`& .${classes.dashboardTitle}`]: {
    marginTop: theme.spacing(-4),
    marginRight: theme.spacing(4),
  },

  [`& .${classes.withMargin}`]: {
    marginRight: "320px",
  },

  [`& .${classes.dashboardContainer}`]: {
    marginTop: theme.spacing(2),
  },

  [`& .${classes.dashboardEntry}`]: {
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
  }
}));

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
  let [ clinicId, setClinicId ] = useState();
  let [ title, setTitle ] = useState();
  let [ description, setDescription ] = useState();
  let [ surveysId, setSurveysId ] = useState("");
  let [ surveys, setSurveys ] = useState();
  let [ dashboardExtensions, setDashboardExtensions ] = useState([]);
  let [ defaultsLoading, setDefaultsLoading ] = useState(true);
  let [ extensionsLoading, setExtensionsLoading ] = useState(true);
  let [ visitInfo, setVisitInfo ] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);
  const location = useLocation();

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
      .catch(err => console.log("Something went wrong loading the proms dashboard", err))
      .finally(() => setExtensionsLoading(false));
  }, [clinicId])

  // Also load the clinic configuration...
  useEffect(() => {
    if (!clinicId) return;
    fetchWithReLogin(globalLoginDisplay, `/Proms/ClinicMapping/${clinicId}.deep.json`)
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
    fetchWithReLogin(globalLoginDisplay, `/Proms/${surveysId}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => setSurveys(
        Object.values(json || {})
          .filter(e => e["jcr:primaryType"] == "cards:QuestionnaireRef")
          .sort((a, b) => a.order - b.order)
      ))
      .finally(() => setDefaultsLoading(false));
  }, [surveysId]);



  // Colors assigned to the dashboard widgets
  // If we have more widgets than colors, start reusing golors from the top
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
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  return (
    (<Root>
      <Typography variant="h4" className={classes.dashboardTitle + (appbarExpanded ? ' ' + classes.withMargin : '')}>{title}</Typography>
      { description && <Typography variant="overline">{description}</Typography>}
      <Grid container spacing={4} className={classes.dashboardContainer}>
        {/* Appointments view */}
        <Grid item lg={12} xl={6} key={`view-appointments-${clinicId}`} className={classes.dashboardEntry}>
          <VisitView color={getColor(0)} visitInfo={visitInfo} surveysId={surveysId} />
        </Grid>
        {/* Survey views */}
        { surveys?.map((s, index) => (
            <Grid item lg={12} xl={6} key={`view-survey-${clinicId}-${s["@name"]}`} className={classes.dashboardEntry}>
              <PromsView data={s} color={getColor(index + 1)} visitInfo={visitInfo} surveysId={surveysId} />
            </Grid>
          ))
        }
        {/* Extensions */}
        {
          dashboardExtensions.map((extension, index) => {
            let Extension = extension["cards:extensionRender"];
            return <Grid item lg={12} xl={6} key={`extension-${clinicId}-${index}`} className={classes.dashboardEntry}>
              <Extension data={extension["cards:data"]} color={getColor(index)} visitInfo={visitInfo} surveysId={surveysId} />
            </Grid>
          })
        }
      </Grid>
    </Root>)
  );
}

export default PromsDashboard;
