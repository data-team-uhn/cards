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
      failedLogin: false,

      username: "",
      password: "",

      phase: "USERNAME_ENTRY"
    };
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
      this.setState({failedLogin: false});
      this.props.handleLogin && this.props.handleLogin(true);
      if (this.props.redirectOnLogin) {
        window.location = this.loginRedirectPath();
      }
    })
    .catch((error) => {
      this.setState({failedLogin: true});
      this.props.handleLogin && this.props.handleLogin(false);
    });
  }

  render() {
    const { classes } = this.props;
    const { passwordIsMasked } = this.state;

    return (
        <div className={classes.main}>
            {this.state.failedLogin && <Typography component="h2" className={classes.errorMessage}>Invalid username or password</Typography>}

            <form className={classes.form} onSubmit={(event)=>{event.preventDefault(); this.submitLogin();}} >

              { (this.state.phase == "USERNAME_ENTRY") &&
                <React.Fragment>
                  <FormControl margin="normal" required fullWidth>
                    <InputLabel htmlFor="j_username">Username</InputLabel>
                    <Input id="j_username" name="j_username" autoComplete="email" autoFocus onChange={(event) => {this.setState({username: event.target.value});}}/>
                  </FormControl>
                  <Button
                    fullWidth
                    variant="contained"
                    color="primary"
                    className={classes.submit}
                    onClick={() => {
                      if (this.state.username.split("@").length - 1 == 0) {
                        this.setState({phase: "PASSWORD_ENTRY"});
                      } else if (this.state.username.split("@").length - 1 == 1) {
                        let remoteUser = this.state.username.split("@")[0];
                        let remoteDomain = this.state.username.split("@")[1];
                        // Do a fetch() to see if we have a SAML configuration for this domain
                        fetch(window.location.origin + "/apps/cards/SAMLDomains/" + remoteDomain + ".json")
                        .then((resp) => {
                          if (resp.ok) {
                            this.setState({failedLogin: false});
                            return resp.json();
                          } else {
                            this.setState({failedLogin: true});
                          }
                        })
                        .then((data) => {
                          data && window.open(data.value + "&username=" + this.state.username, "FederatedLoginPopupWindow", "width=600,height=600");
                        })
                        .catch((err) => this.setState({failedLogin: true}))
                      } else {
                        this.setState({failedLogin: true});
                      }
                    }}
                  >
                    Next
                  </Button>
                </React.Fragment>
              }

              { (this.state.phase == "PASSWORD_ENTRY") &&
                <React.Fragment>
                  <FormControl margin="normal" required fullWidth>
                    <InputLabel htmlFor="j_password">Password for {this.state.username}</InputLabel>
                    <Input name="j_password" type={this.state.passwordIsMasked ? 'text' : 'password'} id="j_password" autoComplete="current-password" autoFocus onChange={(event) => {this.setState({password: event.target.value});}}
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
                  <Grid container direction="row" justifyContent="center" alignItems="center">
                    <Grid item xs={3}>
                    </Grid>
                    <Grid item xs={3}>
                      <Button
                        fullWidth
                        variant="contained"
                        color="primary"
                        className={classes.submit}
                        onClick={() => {
                          this.setState({
                            username: "",
                            password: "",
                            phase: "USERNAME_ENTRY"
                          });
                        }}
                      >
                        Back
                      </Button>
                    </Grid>
                    <Grid item xs={3}>
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
                    <Grid item xs={3}>
                    </Grid>
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
