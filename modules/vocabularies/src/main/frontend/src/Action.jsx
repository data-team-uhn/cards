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
  Button,
  CircularProgress,
  makeStyles,
  Tooltip
} from "@material-ui/core";

const Phase = require("./phaseCodes.json");

const useStyles = makeStyles(theme => ({
  button: {
    margin: theme.spacing(1),
    textTransform: "none",
    color: "white"
  },
  buttonProgress: {
    top: "50%",
    left: "50%",
    position: "absolute",
    marginTop: -12,
    marginLeft: -12,
    textTransform: "none"
  },
  install: {
    background: "green",
    "&:hover": {
      background: "#075706"
    }
  },
  installingColor: {
    color: "green",
  },
  uninstallingColor: {
    color: "red",
  },
  update: {
    background: "orange",
    "&:hover": {
      background: "#a36c1f"
    }
  },
  wrapper: {
    position: "relative",
    display: "inline-block",
    marginLeft: -theme.spacing(3)
  }
}));

export default function Action(props) {
  const classes = useStyles();
  return(
    <React.Fragment>
    {(props.phase == Phase["Not Installed"]) && (
      <Tooltip title="Install this vocabulary">
        <Button onClick={props.install} variant="contained" className={classes.button + " " + classes.install}>Install</Button>
      </Tooltip>
    )}
    {(props.phase == Phase["Installing"]) && (
      <span className={classes.wrapper}>
        <Button disabled variant="contained" className={classes.button}>Installing</Button>
        <CircularProgress size={24} className={classes.buttonProgress + " " + classes.installingColor} />
      </span>
    )}
    {(props.phase == Phase["Update Available"]) && (
      <Tooltip title="Update this vocabulary">
        <Button onClick={props.install} variant="contained" className={classes.button + " " + classes.update}>Update</Button>
      </Tooltip>
    )}
    {(props.phase == Phase["Uninstalling"]) && (
      <span className={classes.wrapper}>
        <Button disabled variant="contained" className={classes.button}>Uninstalling</Button>
        <CircularProgress size={24} className={classes.buttonProgress + " " + classes.uninstallingColor} />
      </span>
    )}
    </React.Fragment>
  );
}
