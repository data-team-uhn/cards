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
import {
    Button,
    FormControl,
    Grid,
    IconButton,
    Input,
    InputAdornment,
    InputLabel,
    Tooltip,
    Typography,
    withStyles
} from '@material-ui/core';
import VisibilityIcon from '@material-ui/icons/Visibility';
import VisibilityOffIcon from '@material-ui/icons/VisibilityOff';
import styles from "../styling/styles";

class SignIn extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      passwordIsMasked: false,
      failedLogin: undefined,

      username: "",
      password: "",

      phase: "USERNAME_ENTRY",

      singleStepEntry: undefined
    };

    // Check to see if 1 or 2 step login should be used
    fetch(window.location.origin + "/apps/cards/SAMLDomains.json")
    .then((resp) => {
      if (resp.ok) {
        this.setState({singleStepEntry: false});
      } else {
        this.setState({singleStepEntry: true});
      }
    });
  }

  loginRedirectPath() {
    const currentPath = window.location.pathname.startsWith("/login") ? "/" : window.location.pathname;
    return new URLSearchParams(window.location.search).get("resource") || currentPath;
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
    fetch('/j_security_check',
      {
        method: 'POST',
        body: new URLSearchParams({
          "j_username": this.state.username,
          "j_password": this.state.password,
          "j_validate": true
        }),
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      }
    )
    .then((response) => {
      if (!response.ok) {
        throw Error(response.statusText);
        this.props.handleLogin && this.props.handleLogin(false);
      }
      this.setState({failedLogin: undefined});
      this.props.handleLogin && this.props.handleLogin(true);
      if (this.props.redirectOnLogin) {
        window.location = this.loginRedirectPath();
      }
    })
    .catch((error) => {
      this.setState({failedLogin: "Invalid username or password"});
      this.props.handleLogin && this.props.handleLogin(false);
    });
  }

  render() {
    const { classes } = this.props;
    const { passwordIsMasked } = this.state;

    const nextButtonCallback = () => {
      if (this.state.username.split("@").length - 1 == 0) {
        this.setState({phase: "PASSWORD_ENTRY"});
      } else if (this.state.username.split("@").length - 1 == 1) {
        let remoteUser = this.state.username.split("@")[0];
        let remoteDomain = this.state.username.split("@")[1];
        // Do a fetch() to see if we have a SAML configuration for this domain
        fetch(window.location.origin + "/apps/cards/SAMLDomains/" + remoteDomain + ".json")
        .then((resp) => {
          if (resp.ok) {
            this.setState({failedLogin: undefined});
            return resp.json();
          } else {
            this.setState({failedLogin: "Unrecognized email domain"});
          }
        })
        .then((data) => {
          if (window.location.pathname === "/login" || window.location.pathname === "/login/") {
            // We are logging in at a main login screen
            window.location = data.value + window.location.search;
          } else {
            // We are logging in from a fetchWithReLogin() window
            let popupWidth = 600;
            let popupHeight = 600;
            let screenLeft = window.screenLeft !== undefined ? window.screenLeft : window.screenX;
            let screenTop = window.screenTop !== undefined ? window.screenTop : window.screenY;
            let screenWidth = window.innerWidth;
            let screenHeight = window.innerHeight;
            let systemZoom = screenWidth / window.screen.availWidth;
            let left = (screenWidth - popupWidth) / 2 / systemZoom + screenLeft;
            let top = (screenHeight - popupHeight) / 2 / systemZoom + screenTop;
            let loginPopup = data && window.open(window.location.origin + "/fetch_requires_saml_login.html", "FederatedLoginPopupWindow", "width=" + (popupWidth / systemZoom) + ",height=" + (popupHeight / systemZoom) + ",top=" + top + ",left=" + left);
            let checkLoginTimer = setInterval(() => {
              if (loginPopup.closed === true) {
                clearInterval(checkLoginTimer);
                this.props.handleLogin && this.props.handleLogin(true);
              }
            }, 1000);
          }
        })
        .catch((err) => this.setState({failedLogin: "Error occurred while handling third-party identity provider"}))
      } else {
        this.setState({failedLogin: "Invalid email address"});
      }
    }

    if (this.state.singleStepEntry === undefined) {
      return null;
    }

    return (
        <div className={classes.main}>
            {this.state.failedLogin && <Typography component="h2" className={classes.errorMessage}>{this.state.failedLogin}</Typography>}

            <form
              className={classes.form}
              onSubmit={(event)=> {
                event.preventDefault();
                if (this.state.phase == "PASSWORD_ENTRY" || this.state.singleStepEntry === true) {
                  this.submitLogin();
                } else if (this.state.phase == "USERNAME_ENTRY" && this.state.singleStepEntry === false) {
                  nextButtonCallback();
                }
              }}
            >
              { (this.state.phase == "USERNAME_ENTRY" || this.state.singleStepEntry) &&
                <React.Fragment>
                  <FormControl margin="normal" required fullWidth>
                    <InputLabel htmlFor="j_username">Username{this.state.singleStepEntry ? "" : " or email address"}</InputLabel>
                    <Input id="j_username" name="j_username" autoComplete="email" autoFocus onChange={(event) => {this.setState({username: event.target.value});}}/>
                  </FormControl>
                  {  (!this.state.singleStepEntry) &&
                    <Button
                      fullWidth
                      variant="contained"
                      color="primary"
                      className={classes.submit}
                      onClick={nextButtonCallback}
                    >
                      Next
                    </Button>
                  }
                </React.Fragment>
              }

              { (this.state.phase == "PASSWORD_ENTRY" || this.state.singleStepEntry) &&
                <React.Fragment>
                  <FormControl margin="normal" required fullWidth>
                    <InputLabel htmlFor="j_password">Password{this.state.singleStepEntry ? "" : (" for " + this.state.username)}</InputLabel>
                    <Input name="j_password" type={this.state.passwordIsMasked ? 'text' : 'password'} id="j_password" autoComplete="current-password" autoFocus={this.state.phase === "PASSWORD_ENTRY"} onChange={(event) => {this.setState({password: event.target.value});}}
                      endAdornment={
                        <InputAdornment position="end">
                          <Tooltip title={this.state.passwordIsMasked ? "Mask Password" : "Show Password"}>
                            <IconButton
                              aria-label="Toggle password visibility"
                              onClick={this.togglePasswordMask}
                            >
                              {this.state.passwordIsMasked ? <VisibilityIcon/> : <VisibilityOffIcon/>}
                            </IconButton>
                          </Tooltip>
                        </InputAdornment>
                      }
                    />
                  </FormControl>
                  <Grid container direction="row" justify="center" alignItems="center">
                    {  (!this.state.singleStepEntry) &&
                      <Grid item xs={3}>
                      </Grid>
                    }
                    {  (!this.state.singleStepEntry) &&
                      <Grid item xs={3}>
                        <Button
                          fullWidth
                          variant="contained"
                          color="primary"
                          className={classes.submit}
                          onClick={() => {
                            this.setState({
                              failedLogin: undefined,
                              username: "",
                              password: "",
                              phase: "USERNAME_ENTRY"
                            });
                          }}
                        >
                          Back
                        </Button>
                      </Grid>
                    }
                    <Grid item xs={this.state.singleStepEntry ? 12: 3}>
                      <Button
                        type="submit"
                        fullWidth
                        variant="contained"
                        color="primary"
                        className={classes.submit}
                      >
                        Sign in
                      </Button>
                    </Grid>
                    {  (!this.state.singleStepEntry) &&
                      <Grid item xs={3}>
                      </Grid>
                    }
                  </Grid>
                </React.Fragment>
              }
            </form>
        </div>
    );
  }
}

SignIn.propTypes = {
  classes: PropTypes.object.isRequired,
};

export default withStyles(styles)(SignIn);
