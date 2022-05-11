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
import React, { useEffect, useState } from 'react';

import { Fab, Grid, Paper, Typography } from '@material-ui/core';
import { MuiThemeProvider } from '@material-ui/core/styles';

import makeStyles from '@material-ui/styles/makeStyles';

import NavigationIcon from '@material-ui/icons/Navigation';
import { appTheme } from "../themePalette.jsx";

const useStyles = makeStyles(theme => ({
  paper: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: theme.spacing(12, 3, 3),
    textAlign: "center",
    "& .MuiGrid-item" : {
      textAlign: "center",
    },
  },
  logo: {
    maxWidth: "240px",
  },
  extendedIcon: {
    marginRight: theme.spacing(1),
  },
}));

export default function ErrorPage(props) {
  const { errorCode, errorCodeColor, title, titleColor, message, buttonLink, buttonLabel } = props;
  const classes = useStyles();

  return (
    <MuiThemeProvider theme={appTheme}>
      <Paper className={classes.paper} elevation={0}>
        <Grid
          container
          direction="column"
          spacing={7}
          alignItems="center"
          alignContent="center"
        >
          <Grid item>
            <img src={document.querySelector('meta[name="logoLight"]').content} alt="" className={classes.logo}/>
          </Grid>
          <Grid item>
            {errorCode && <Typography variant="h1" color={errorCodeColor || "primary"}>
              {errorCode}
            </Typography> }
            <Typography variant="h1" color={titleColor || "primary"} gutterBottom>
              {title}
            </Typography>
            {message && <Typography variant="subtitle1" color="textSecondary">
              {message}
            </Typography> }
          </Grid>
          { buttonLabel &&
            <Grid item>
              <Fab
                variant="extended"
                color="primary"
                onClick={() => window.location.href = buttonLink}
              >
                <NavigationIcon className={classes.extendedIcon} />
                {buttonLabel}
              </Fab>
            </Grid>
          }
        </Grid>
      </Paper>
    </MuiThemeProvider>
  );
}
