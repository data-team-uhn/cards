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
import React, { useState } from 'react';

import { Breadcrumbs, Button, Grid, Paper, Tooltip, Typography } from '@mui/material';

import RegistrationForm from './RegistrationForm';
import LoginForm from './LoginForm';
import Logo from "../components/Logo";
import { withStyles } from 'tss-react/mui';
import styles from "../styling/styles";

function MainLoginContainer(props) {
  const { classes, selfContained, handleLogin, redirectOnLogin } = props;
  let [ signInShown, setSignInShow ] = useState(true);

  const isLongForm = !!window.location.pathname.startsWith("/login");
  const title = document.querySelector('meta[name="title"]').content;

  return (
    <Paper className={`${classes.paper}  ${selfContained ? classes.selfContained : ''}`} elevation={0}>
      <Grid container direction="column" spacing={3} alignItems="center" alignContent="center">
        <Logo maxWidth="200px" component={Grid}/>
        <Grid>
        { signInShown ?
          <LoginForm handleLogin={handleLogin} redirectOnLogin={redirectOnLogin}/>
          :
          <RegistrationForm loginOnSuccess={true} handleLogin={handleLogin} />
        }
        </Grid>
        { isLongForm && !signInShown &&
          <Grid>
            <Button
              variant="outlined"
              fullWidth
              className={classes.main}
              onClick={() => setSignInShow(!signInShown)}
             >
              { signInShown ?  "Sign up" : "Sign In" }
            </Button>
          </Grid>
        }
        { isLongForm &&
        <Grid>
          <Breadcrumbs separator="by" className={classes.appInfo}>
            <Typography variant="subtitle2">{title}</Typography>
            <Tooltip title="DATA Team @ UHN">
              <a href="https://uhndata.io/" target="_blank">
                <img src="/libs/cards/resources/media/default/data-logo_light_bg.png" width="80" alt="DATA" />
              </a>
            </Tooltip>
          </Breadcrumbs>
        </Grid>
        }
      </Grid>
    </Paper>
  );
}

export default withStyles(MainLoginContainer, styles);
