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
import React from 'react';

import { Fab, Grid, Paper, Typography } from '@mui/material';
import makeStyles from '@mui/styles/makeStyles';

import NavigationIcon from '@mui/icons-material/Navigation';

import Logo from "./Logo";
import FormattedText from "./FormattedText";

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
  extendedIcon: {
    marginRight: theme.spacing(1),
  },
}));

export default function ErrorPage(props) {
  const { errorCode, errorCodeColor, title, titleColor, message, messageColor, buttonLink, buttonLabel, ...rest } = props;
  const classes = useStyles();

  return (
      <Paper className={classes.paper} elevation={0} {...rest}>
        <Grid
          container
          direction="column"
          spacing={7}
          alignItems="center"
          alignContent="center"
        >
          <Logo maxWidth="360px" component={Grid} item/>
          <Grid item>
            {errorCode && <Typography variant="h1" color={errorCodeColor || "primary"}>
              {errorCode}
            </Typography> }
            {title && <Typography variant="h1" color={titleColor || "primary"} gutterBottom>
              {title}
            </Typography> }
            {message && <FormattedText variant="subtitle1" color={messageColor || "textSecondary"}>
              {message}
            </FormattedText> }
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
  );
}
