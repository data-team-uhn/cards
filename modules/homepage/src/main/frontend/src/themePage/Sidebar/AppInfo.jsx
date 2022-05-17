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
import { Tooltip, Typography } from "@mui/material";

function AppInfo (props) {
  const { classes, textVariant, showTeamInfo } = props;
  let variant = textVariant || "subtitle2";

  let platformName = document.querySelector('meta[name="platformName"]')?.content;
  let appName = document.querySelector('meta[name="title"]')?.content;
  let version = document.querySelector('meta[name="version"]')?.content;

  return (
    <>
      <Typography variant={variant} component="div">{appName ?  appName + " | " : ''} {platformName} {version ? "v" + version : ''}</Typography>
      {showTeamInfo &&
        <Typography variant={variant} component="div">
          by
          <Tooltip title="DATA Team @ UHN">
            <a href="https://uhndata.io/" target="_blank">
              <img src="/libs/cards/resources/media/default/data-logo.png" width="80" alt="DATA" />
            </a>
          </Tooltip>
        </Typography>
      }
    </>
    );
}

export default AppInfo;
