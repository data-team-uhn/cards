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

import React from "react";

import {
  AppBar,
  Grid,
  Toolbar,
  Typography
} from '@mui/material';

import withStyles from '@mui/styles/withStyles';

import WarningIcon from '@mui/icons-material/Warning';

const appbarStyle = theme => ({
  root: {
    backgroundColor: theme.palette.warning.main,
    boxShadow: "none",
    [theme.breakpoints.down('md')]: {
      position: 'absolute',
    },
  }
});

export default function DemoBanner(props) {
  const StyledAppBar = withStyles(appbarStyle)(AppBar);

  return (
    <StyledAppBar position="fixed" style={props.style} ref={props.onRender}>
      <Toolbar>
      <Grid container spacing={1} direction="row" justifyContent="center" alignItems="center" wrap="nowrap">
        <Grid item><WarningIcon/></Grid>
        <Grid item>
        <Typography variant="subtitle2">
          This installation is for demo purposes only.
          Data entered here can be accessed by anyone and is
          periodically deleted. Do not enter any real
          data / patient identifiable information.
        </Typography>
        </Grid>
      </Grid>
      </Toolbar>
    </StyledAppBar>
  );
}
