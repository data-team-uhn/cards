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
import SignUpForm from './signUpForm';
import SignIn from './loginForm';

import { Button, Grid, Paper, Tooltip, Typography, withStyles } from '@material-ui/core';

import styles from "../styling/styles";

class MainLoginContainer extends React.Component {
  constructor(props, selfContained) {
    super(props);

    this.state = {
      signInShown: true,
      signUpEnabled : false,
      title: document.querySelector('meta[name="title"]').content
    }
  }

  // Toggle between sign in and sign up
  handleSwap = () => {
    this.setState(prevState => ({
      signInShown: !prevState.signInShown,
    }));
  }

  render () {
    const { classes, selfContained } = this.props;

    return (
      <Paper className={`${classes.paper}  ${selfContained ? classes.selfContained : ''}`} elevation={0}>
        <Grid container direction="column" spacing={3} alignItems="center" alignContent="center">
          <Grid item>
            <img src="/libs/lfs/resources/logo_light_bg.png" alt="this.state.title" className={classes.logo}/>
          </Grid>
          <Grid item>
            <Grid container direction="column" spacing={0} alignItems="center" alignContent="center">
              <Grid item>
                <Typography variant="caption" component="div">{this.state.title}</Typography>
              </Grid>
              <Grid item>
                <Typography variant="caption" component="div">by</Typography>
              </Grid>
              <Grid item>
                <Tooltip title="DATA Team @ UHN">
                  <a href="https://uhndata.io/" target="_blank">
                    <img src="/libs/lfs/resources/data-logo_light_bg.png" width="80" alt="DATA" />
                  </a>
                </Tooltip>
              </Grid>
            </Grid>
          </Grid>
          <Grid item>
          { this.state.signInShown ? <SignIn handleLogin={this.props.handleLogin} redirectOnLogin={this.props.redirectOnLogin}/> : <SignUpForm loginOnSuccess={true} handleLogin={this.props.handleLogin} /> }
          </Grid>
          { (!this.state.signInShown || this.state.signUpEnabled) &&
            <Grid item>
              <Button
                fullWidth
                color="default"
                className={classes.main}
                onClick={this.handleSwap}
               >
                { this.state.signInShown ?  "Sign up" : "Sign In" }
              </Button>
            </Grid>
          }
        </Grid>
      </Paper>
    );
  }
}

const MainLoginComponent = withStyles(styles)(MainLoginContainer);

export default MainLoginComponent;
