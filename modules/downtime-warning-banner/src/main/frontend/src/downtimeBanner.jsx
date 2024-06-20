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

import {
  AppBar,
  Avatar,
  Grid,
  Toolbar,
  Typography,
} from '@mui/material';

import { withStyles } from '@mui/styles';

import BuildIcon from '@mui/icons-material/Build';

const appbarStyle = theme => ({
  root: {
    backgroundColor: theme.palette.info.main,
    boxShadow: "none",
    [theme.breakpoints.down('md')]: {
      position: 'absolute',
    },
    "& .MuiAvatar-root" : {
      backgroundColor: theme.palette.background.paper,
      color: theme.palette.info.main
    },
    "& b": {
      backgroundColor: theme.palette.action.selected,
      padding: "2px 4px",
      borderRadius: "2px",
    }
  }
});

export default function DowntimeWarning(props) {
  const StyledAppBar = withStyles(appbarStyle)(AppBar);
  const appName = document.querySelector('meta[name="title"]')?.content;

  // The the configuration values specified by the Administration
  const [ enabled, setEnabled ] = useState(false);
  const [ fromDate, setFromDate ] = useState();
  const [ toDate, setToDate ] = useState();
  // Error message set when fetching the data from the server fails
  const [ error, setError ] = useState();

  // Load the configurations only once, upon initialization
  useEffect(() => {
    fetch("/apps/cards/config/DowntimeWarning.deep.json")
      .then((response) => response.json())
      .then((json) => {
        if (!json.enabled) {
          return;
        }
        setEnabled(json.enabled == 'true');
        if (json.fromDate) {
          let date = new Date(json.fromDate);
          (date != "Invalid Date") && setFromDate(date.toDateString() + " " + date.toLocaleTimeString().replace(":00 ", " "));
        }
        if (json.toDate) {
          let date = new Date(json.toDate);
          (date != "Invalid Date") && setToDate(date.toDateString() + " " + date.toLocaleTimeString().replace(":00 ", " "));
        }
      })
      .catch((error) => {
        setError(error.statusText ? error.statusText : error);
      });
  }, []);

  if (!enabled || !fromDate || !toDate) {
      return null;
  }

  return (
    <StyledAppBar position="fixed" style={props.style} ref={props.onRender}>
      <Toolbar>
      {error && <Typography color='error'>{errorText}</Typography>}
      <Grid container spacing={1} direction="row" justifyContent="center" alignItems="center" wrap="nowrap">
        <Grid item><Avatar><BuildIcon/></Avatar></Grid>
        <Grid item>
        <Typography variant="body2">
          {appName} will be down for maintenance from <b>{fromDate}</b> to <b>{toDate}</b>. We appologize for the inconvenience this may cause.
        </Typography>
        </Grid>
      </Grid>
      </Toolbar>
    </StyledAppBar>
  );
}
