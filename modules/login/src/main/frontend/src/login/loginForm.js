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
import PropTypes from 'prop-types';
import { Avatar, Button, CssBaseline, FormControl, Input, InputLabel, Paper, Typography, withStyles, InputAdornment, IconButton, Tooltip, Icon, SvgIcon } from '@material-ui/core';
import axios from 'axios';
import styles from "../styling/styles";

class SignIn extends React.Component {
  constructor(props, selfContained) {
    super(props);

    this.state = {
      passwordIsMasked: false,
      failedLogin: false,

      username: "",
      password: ""
    };
  }

  loginRedirectPath() {
    return "/";
  };

  loginValidationPOSTPath() {
    return "/j_security_check";
  };

  togglePasswordMask = () => {
    this.setState(prevState => ({
      passwordIsMasked: !prevState.passwordIsMasked,
    }));
  }

  submitLogin() {
    axios({
      method: 'POST',
      url: '/j_security_check',
      headers: {
        'Content-Type':'application/x-www-form-urlencoded;charset=utf-8'
      },
      dataType: "text",
      params: {
        j_username: this.state.username,
        j_password: this.state.password,
        j_validate: true,
        resource: "/content.html"
      }
    })
    .then((response) => {
      window.location.reload();
      this.setState({failedLogin: false});
    })
    .catch((error)=>{
      this.setState({failedLogin: true});
    });
  }

  render() {
    const { classes, selfContained } = this.props;
    const { passwordIsMasked } = this.state;

    return (
      <main className={classes.main}>
        <CssBaseline />
        <Paper className={`${classes.paper} ${selfContained ? classes.selfContained : ''}`}>
          <Avatar className={classes.avatar}>
            <SvgIcon><path d="M10,17V14H3V10H10V7L15,12L10,17M10,2H19C20.1,2 21,2.9 21,4V20C21,21.1 20.1,22 19,22H10C8.9,22 8,21.1 8,20V18H10V20H19V4H10V6H8V4C8,2.9 8.9,2 10,2Z"/></SvgIcon>
          </Avatar>
          <Typography component="h1" variant="h5">
            Sign in
          </Typography>

          {this.state.failedLogin && <Typography component="h2">An invalid username or password has been entered.</Typography>}

          <form className={classes.form} onSubmit={(event)=>{event.preventDefault(); this.submitLogin();}} method="POST" action={this.loginValidationPOSTPath()}>
            <input type="hidden" name="resource" value={this.loginRedirectPath()} />
            <input type="hidden" name="j_validate" value={true}/>

            <FormControl margin="normal" required fullWidth>
              <InputLabel htmlFor="j_username">Username</InputLabel>
              <Input id="j_username" name="j_username" autoComplete="email" autoFocus onChange={(event) => {this.setState({username: event.target.value});}}/>
            </FormControl>

            <FormControl margin="normal" required fullWidth>
              <InputLabel htmlFor="j_password">Password</InputLabel>
              <Input name="j_password" type={this.state.passwordIsMasked ? 'text' : 'password'} id="j_password" autoComplete="current-password" onChange={(event) => {this.setState({password: event.target.value});}}
                endAdornment={
                <InputAdornment position="end">
                  <Tooltip title={this.state.passwordIsMasked ? "Mask Password" : "Show Password"}>
                    <IconButton
                      aria-label="Toggle password visibility"
                      onClick={this.togglePasswordMask}
                    >
                      {this.state.passwordIsMasked ? <Icon>visibility</Icon> : <Icon >visibility_off</Icon>}
                    </IconButton>
                  </Tooltip>
                </InputAdornment>
              }
             />
            </FormControl>
            <Button
              type="submit"
              fullWidth
              variant="contained"
              color="primary"
              className={classes.submit}
            >
              Sign in
            </Button>
          </form>
          <Typography>
            Don't have an account?
          </Typography>
          <Button
            fullWidth
            variant="contained"
            color="default"
            onClick={this.props.swapForm}
          >
            <Icon className={classes.buttonIcon}>person_add</Icon> Register
          </Button>
        </Paper>
      </main>
    );
  }
}

SignIn.propTypes = {
  classes: PropTypes.object.isRequired,
};

export default withStyles(styles)(SignIn);
