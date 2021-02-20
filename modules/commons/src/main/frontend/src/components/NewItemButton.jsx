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
import AddIcon from "@material-ui/icons/Add";

import style from './style.jsx';

function NewItemButton(props) {
  const { title, onClick, loading, classes } = props;
  return (
    <div className={classes.mainPageAction}>
      <Tooltip title={title} aria-label="new">
        <Fab
          color="primary"
          aria-label="new"
          onClick={onClick}
          disabled={loading}
        >
          <AddIcon />
        </Fab>
      </Tooltip>
      {loading && <CircularProgress size={56} className={classes.newItemLoadingIndicator} />}
    </div>
  );
}

export default withStyles(style)(NewItemButton);
