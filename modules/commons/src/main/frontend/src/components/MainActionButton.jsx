/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

import React from "react";
import { CircularProgress, Fab, Tooltip, withStyles } from "@material-ui/core";

import style from './style.jsx';

function MainActionButton(props) {
  const { icon, label, title, ariaLabel, onClick, inProgress, classes } = props;

  let extended = !!label;

  let button = (
    <div className={classes.mainActionButton}>
      <Fab
        variant={extended ? "extended" : "round"}
        color="primary"
        onClick={onClick}
        disabled={inProgress}
        aria-label={ariaLabel}
      >
        {icon}{label}
      </Fab>
      {inProgress && <CircularProgress size={extended ? 32 : 56} />}
    </div>
  );

  return (
    <>
    { title ?
      <Tooltip title={title}>
        {button}
      </Tooltip>
      :
      button
    }
    </>
  );
}

export default withStyles(style)(MainActionButton);
