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

import React, { useState } from "react";

import {
  Breadcrumbs,
  Button,
  Dialog,
  Grid,
  Paper,
  Tooltip,
  Typography,
  makeStyles
} from '@material-ui/core';

import { MuiThemeProvider } from '@material-ui/core/styles';
import { appTheme } from "../themePalette.jsx";

const useStyles = makeStyles(theme => ({
  paper: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 0,
    height: '100%',
  },
  logo : {
    maxWidth: "200px",
    marginBottom: theme.spacing(6),
  },
  button : {
    textTransform: "none",
    minWidth: "250px",
    padding: theme.spacing(3, 1),
    fontWeight: 400,
  },
  appInfo : {
    marginTop: theme.spacing(6),
  }
}));

function PromsLandingPage(props) {

  const classes = useStyles();

  const [ isOpen, setIsOpen ] = useState(true);

  const appInfo = document.querySelector('meta[name="title"]').content;

  return (
    <Dialog
      fullScreen
      open={isOpen}
    >
      <MuiThemeProvider theme={appTheme}>
        <Paper
          className={classes.paper}
          elevation={0}
        >
          <Grid container direction="column" spacing={4} alignItems="center" alignContent="center">
            <Grid item>
              <img src="/libs/cards/resources/logo_light_bg.png" alt="" className={classes.logo} />
            </Grid>
            <Grid item>
              <Typography variant="h6">Sign in as a:</Typography>
            </Grid>
            <Grid item>
              <Grid container spacing={4} direction="row" justify="center" alignItems="center">
                <Grid item>
                  <Button
                    fullWidth
                    variant="contained"
                    color="primary"
                    className={classes.button}
                    onClick={() => {
                      window.location = "/Proms";
                    }}
                   >
                    <Typography variant="h6">Patient</Typography>
                  </Button>
                </Grid>
                <Grid item>
                  <Button
                    fullWidth
                    variant="contained"
                    className={classes.button}
                    onClick={() => {
                      setIsOpen(false);
                    }}
                   >
                    <Typography variant="h6">Health Care Provider</Typography>
                  </Button>
                </Grid>
              </Grid>
            </Grid>
            <Grid item>
              <Breadcrumbs separator="by" className={classes.appInfo}>
                <Typography variant="subtitle2">{appInfo}</Typography>
                <Tooltip title="DATA Team @ UHN">
                  <a href="https://uhndata.io/" target="_blank">
                    <img src="/libs/cards/resources/data-logo_light_bg.png" width="80" alt="DATA" />
                  </a>
                </Tooltip>
              </Breadcrumbs>
            </Grid>
          </Grid>
        </Paper>
      </MuiThemeProvider>
    </Dialog>
  );
}

export default PromsLandingPage;
