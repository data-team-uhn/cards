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
  withStyles
} from '@material-ui/core';

import { MuiThemeProvider } from '@material-ui/core/styles';
import { appTheme } from "../themePalette.jsx";
import styles from "../styling/styles";

function PromsLandingPageDialog(props) {

  const { classes } = props;
  const [ isOpen, setIsOpen ] = useState(true);

  return (
    <Dialog
      fullScreen
      open={isOpen}
    >
      <MuiThemeProvider theme={appTheme}>
        <Paper
          className={`${classes.paper}`}
          elevation={0}
          style={{
            justifyContent: 'center',
            alignItems: 'center',
            height: '100%'
          }}
        >
          <Grid container direction="column" spacing={3} alignItems="center" alignContent="center">
            <Grid item>
              <img src="/libs/cards/resources/logo_light_bg.png" alt="" className={classes.logo} />
            </Grid>
            <Grid item>
              <Grid container spacing={1} direction="row" justify="center" alignItems="center" wrap="nowrap">
                <Grid item>
                  <Button
                    fullWidth
                    variant="contained"
                    color="primary"
                    className={classes.main}
                    onClick={() => {
                      window.location = "/Proms";
                    }}
                   >
                    <Typography>I am a</Typography>
                    <Typography style={{fontWeight: 'bold'}}>Patient</Typography>
                  </Button>
                </Grid>
                <Grid item>
                  <Button
                    fullWidth
                    variant="contained"
                    color="primary"
                    className={classes.main}
                    onClick={() => {
                      setIsOpen(false);
                    }}
                   >
                    <Typography>I am a</Typography>
                    <Typography style={{fontWeight: 'bold'}}>Healthcare Provider</Typography>
                  </Button>
                </Grid>
              </Grid>
            </Grid>
            <Grid item>
              <Breadcrumbs separator="by" className={classes.appInfo}>
                <Typography variant="subtitle2">DATA-PRO</Typography>
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

const StyledPromsLandingPageDialog = withStyles(styles)(PromsLandingPageDialog);

export default StyledPromsLandingPageDialog;
