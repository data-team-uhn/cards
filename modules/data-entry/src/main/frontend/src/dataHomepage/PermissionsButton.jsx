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
import { withRouter } from "react-router-dom";

import { IconButton, Tooltip } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import { Lock } from "@mui/icons-material"

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

/**
 * A placeholder component that renders a lock icon.
 */
function PermissionsButton(props) {
  const { classes, size } = props;

  return (
    <React.Fragment>
      <Tooltip title={"Set Permissions"}>
        <IconButton component="span" className={classes.titleButton} size="large">
          <Lock fontSize={size ? size : "default"}/>
        </IconButton>
      </Tooltip>
    </React.Fragment>
  );
}

export default withStyles(QuestionnaireStyle)(withRouter(PermissionsButton));
