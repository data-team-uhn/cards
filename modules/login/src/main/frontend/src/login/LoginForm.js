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
import { Fragment, useState, useEffect } from 'react';

import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import {
  Alert,
  Button,
  FormControl,
  Grid,
  IconButton,
  Input,
  InputAdornment,
  InputLabel,
  Tooltip,
} from '@mui/material';
import PropTypes from 'prop-types';
import { withStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";
import styles from "../styling/styles";

function LoginForm(props) {
  checkPropTypes(LoginForm, props);
  const { classes, handleLogin, redirectOnLogin } = props;

  const [ username, setUsername ] = useState("");
  const [ password, setPassword ] = useState("");
  const [ failedLogin, setFailedLogin ] = useState(false);
  const [ passwordIsMasked, setPasswordIsMasked ] = useState(false);
  const [ phase, setPhase ] = useState("USERNAME_ENTRY");
  const [ singleStepEntry, setSingleStepEntry ] = useState(undefined);

  useEffect(() => {
    // Check to see if 1 or 2 step login should be used
    fetch(window.location.origin + "/apps/cards/SAMLDomains.json")
      .then((resp) => setSingleStepEntry(!resp.ok));
  }, []);

  let loginRedirectPath = () => {
    const currentPath = window.location.pathname.startsWith("/login") ? "/" : window.location.pathname;
    const resource = new URLSearchParams(window.location.search).get("resource") || "";

    // Only allow relative, same-origin paths starting with a single "/"
    const isValidRelativePath =
      resource.startsWith("/") &&
      !resource.startsWith("//") &&
      !resource.includes("://");

    return isValidRelativePath ? resource : currentPath;
  };

  let submitLogin = () => {
    fetch('/j_security_check',
      {
        method: 'POST',
        body: new URLSearchParams({
          "j_username": username,
          "j_password": password,
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
        }
        setFailedLogin(undefined);
        handleLogin?.(true);
        if (redirectOnLogin) {
          window.location = loginRedirectPath();
        }
      })
      .catch((error) => {
        setFailedLogin("Invalid username or password");
        handleLogin?.(false);
      });
  }

  let nextButtonCallback = () => {
    if (username.split("@").length - 1 == 0) {
      setPhase("PASSWORD_ENTRY");
    } else if (username.split("@").length - 1 == 1) {
      let remoteDomain = username.split("@")[1];
      // Do a fetch() to see if we have a SAML configuration for this domain
      fetch(window.location.origin + "/apps/cards/SAMLDomains/" + remoteDomain + ".json")
        .then((resp) => {
          if (resp.ok) {
            setFailedLogin(undefined);
            return resp.json();
          } else {
            setFailedLogin("Unrecognized email domain");
          }
        })
        .then((data) => {
          if (!data) {
            return;
          }
          if (window.location.pathname === "/login" || window.location.pathname === "/login/") {
            // We are logging in at a main login screen
            window.location = window.location.origin + "/goto_saml_login";
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
                handleLogin?.(true);
              }
            }, 1000);
          }
        })
        .catch((err) => setFailedLogin("An error occurred while handling the third-party identity provider."));
    } else {
      setFailedLogin("Invalid email address");
    }
  }

  if (singleStepEntry === undefined) {
    return null;
  }

  return (
    <div className={classes.main}>
      {failedLogin && <Alert severity="error">{failedLogin}</Alert>}

      <form
        method="post"
        className={classes.form}
        onSubmit={(event)=> {
          event.preventDefault();
          if (phase == "PASSWORD_ENTRY" || singleStepEntry === true) {
            submitLogin();
          } else if (phase == "USERNAME_ENTRY" && singleStepEntry === false) {
            nextButtonCallback();
          }
        }}
      >
        { (phase == "USERNAME_ENTRY" || singleStepEntry) &&
            <Fragment>
              <FormControl variant="standard" margin="normal" required fullWidth>
                <InputLabel htmlFor="j_username">Username{singleStepEntry ? "" : " or email address"}</InputLabel>
                <Input
                  id="j_username"
                  name="j_username"
                  autoComplete="email"
                  autoFocus
                  onChange={(event) => setUsername(event.target.value)}
                />
              </FormControl>
              {  (!singleStepEntry) &&
                <Button
                  fullWidth
                  variant="contained"
                  className={`${classes.actions} ${classes.submit}`}
                  onClick={nextButtonCallback}
                  disabled={username.length == 0}
                >
                  Next
                </Button>
              }
            </Fragment>
        }

        { (phase == "PASSWORD_ENTRY" || singleStepEntry) &&
            <Fragment>
              <FormControl variant="standard" margin="normal" required fullWidth>
                <InputLabel htmlFor="j_password">Password{singleStepEntry ? "" : (" for " + username)}</InputLabel>
                <Input
                  name="j_password"
                  type={passwordIsMasked ? 'text' : 'password'}
                  id="j_password"
                  autoComplete="current-password"
                  autoFocus={phase === "PASSWORD_ENTRY"}
                  onChange={(event) => setPassword(event.target.value)}
                  endAdornment={
                    <InputAdornment position="end">
                      <Tooltip title={passwordIsMasked ? "Mask Password" : "Show Password"}>
                        <IconButton
                          size="large"
                          aria-label="Toggle password visibility"
                          onClick={() => setPasswordIsMasked(!passwordIsMasked)}
                        >
                          {passwordIsMasked ? <VisibilityIcon/> : <VisibilityOffIcon/>}
                        </IconButton>
                      </Tooltip>
                    </InputAdornment>
                  }
                />
              </FormControl>
              <Grid container spacing={2} className={classes.actions} sx={{ justifyContent: 'center', alignItems: 'center' }}>
                {  (!singleStepEntry) &&
                  <Grid>
                    <Button
                      fullWidth
                      variant="outlined"
                      className={classes.submit}
                      onClick={() => {
                        setFailedLogin(undefined),
                        setUsername(""),
                        setPassword("");
                        setPhase("USERNAME_ENTRY");
                      }
                      }
                    >
                      Back
                    </Button>
                  </Grid>
                }
                <Grid>
                  <Button
                    type="submit"
                    fullWidth
                    variant="contained"
                    className={classes.submit}
                  >
                    Sign in
                  </Button>
                </Grid>
              </Grid>
            </Fragment>
        }
      </form>
    </div>
  );
}

LoginForm.propTypes = {
  handleLogin: PropTypes.func,
  redirectOnLogin: PropTypes.bool
};

export default withStyles(LoginForm, styles);
