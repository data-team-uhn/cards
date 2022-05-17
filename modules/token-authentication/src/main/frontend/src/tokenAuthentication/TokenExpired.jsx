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
import ReactDOM from 'react-dom';

import { Fab, Grid, Paper, Typography } from '@mui/material';
import makeStyles from '@mui/styles/makeStyles';
import { ThemeProvider, StyledEngineProvider, styled } from '@mui/material/styles';
import NavigationIcon from '@mui/icons-material/Navigation';
import { lightBlue } from '@mui/material/colors';
import { appTheme } from "../themePalette.jsx";
import { useHistory } from 'react-router-dom';

const PREFIX = 'TokenExpired';

const classes = {
  paper: `${PREFIX}-paper`,
  logo: `${PREFIX}-logo`,
  extendedIcon: `${PREFIX}-extendedIcon`
};

const StyledTokenExpired = styled(TokenExpired)((
  {
    theme
  }
) => ({
  [`& .${classes.paper}`]: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: theme.spacing(12, 3, 3),
    "& .MuiGrid-item" : {
      textAlign: "center",
    },
  },

  [`& .${classes.logo}`]: {
    maxWidth: "240px",
  },

  [`& .${classes.extendedIcon}`]: {
    marginRight: theme.spacing(1),
  }
}));

export default function TokenExpired() {


  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={appTheme}>
        <Paper className={`${classes.paper}`} elevation={0}>
          <Grid
            container
            direction="column"
            spacing={7}
            alignItems="center"
            alignContent="center"
            className={classes.notFoundContainer}
          >
            <Grid item>
              <img src={document.querySelector('meta[name="logoLight"]').content} alt="" className={classes.logo}/>
            </Grid>
            <Grid item>
              <Typography variant="h1" color="primary" gutterBottom>
                This link is no longer valid, please close the page.
              </Typography>
            </Grid>
          </Grid>
        </Paper>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

ReactDOM.render(<StyledTokenExpired />, document.getElementById('token-expired-container'));
