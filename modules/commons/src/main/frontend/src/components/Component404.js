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

import { Button, Grid, Paper, Typography, withStyles } from '@material-ui/core';
import { MuiThemeProvider } from '@material-ui/core/styles';
import { appTheme } from "../themePalette.jsx";
import { useHistory } from 'react-router-dom';

import styles from "../styling/styles";

class Container404 extends React.Component {
  constructor(props, selfContained) {
    super(props);
  }
  
  handleRedirect = () => {
	let history = useHistory();
    history.push({
      pathname: "/content.html/Questionnaires/User"
    });
  };

  render () {
	const { classes } = this.props;

    return (
      <MuiThemeProvider theme={appTheme}>
      <Paper className={`${classes.paper}  ${selfContained ? classes.selfContained : ''}`} elevation={0}>
        <Grid container direction="column" spacing={3} alignItems="center" alignContent="center">
          <Grid item>
            <img src="/libs/lfs/resources/logo_light_bg.png" alt="this.state.title" className={classes.logo}/>
          </Grid>
          <Grid item>
            <Typography variant="h1" color="primary" className={classes.dialogTitle}>404</Typography>
          </Grid>
          <Grid item>
          <Typography variant="h1" color="primary" className={classes.dialogTitle}>Not found</Typography>
          </Grid>
          <Grid item>
            <Typography variant="subtitle2">The page you are trying to reach does not exist</Typography>
          </Grid>
            <Grid item>
              <Button
                fullWidth
                color="default"
                className={classes.main}
                onClick={this.handleRedirect}
               >
                Go to the dashboard
              </Button>
            </Grid>
        </Grid>
      </Paper>
      </MuiThemeProvider>
    );
  }
}

const Component404 = withStyles(styles)(Container404);

export default Component404;
