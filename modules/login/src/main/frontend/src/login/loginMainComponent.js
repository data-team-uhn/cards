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

import { Avatar, Button, Paper, Typography, withStyles } from '@material-ui/core';
import ExitToAppIcon from '@material-ui/icons/ExitToApp';
import PersonAddIcon from '@material-ui/icons/PersonAdd';

import styles from "../styling/styles";

class MainLoginContainer extends React.Component {
  constructor(props, selfContained) {
    super(props);

    this.state = {
      signInShown: true,
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
      <div>
        <Paper className={`${classes.paper}  ${selfContained ? classes.selfContained : ''}`}>
          <Typography component="h1" variant="overline">
            {this.state.title}
          </Typography>
          <Typography component="h2" variant="h5">
            {this.state.signInShown ? "Sign In" : "Sign Up" }
          </Typography>
          <Avatar className={classes.avatar}>
            { this.state.signInShown ? <ExitToAppIcon/> : <PersonAddIcon/> }
          </Avatar>
          { this.state.signInShown ? <SignIn handleLogin={this.props.handleLogin} redirectOnLogin={this.props.redirectOnLogin}/> : <SignUpForm loginOnSuccess={true} handleLogin={this.props.handleLogin} /> }
          <Typography>
            { this.state.signInShown ? "Don't have an account?" : "Already have an account?" }
          </Typography>
          <Button
            fullWidth
            variant="contained"
            color="default"
            className={classes.main}
            onClick={this.handleSwap}
          >
          { this.state.signInShown ? <span><PersonAddIcon className={classes.buttonIcon}/> Request an account</span> : <span><ExitToAppIcon className={classes.buttonIcon}/> Sign In</span> }
          </Button>
        </Paper>
      </div>
    );
  }
}

const MainLoginComponent = withStyles(styles)(MainLoginContainer);

export default MainLoginComponent;
