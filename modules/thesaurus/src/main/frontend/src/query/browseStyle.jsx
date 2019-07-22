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
import {
    successColor,
    whiteColor,
    grayColor,
    hexToRgb
  } from "../themeStyle.jsx";

import dropdownStyle from "./dropdownStyle.jsx";

const browseStyle = theme => ({
    ...dropdownStyle(theme),
    dialog: {
      padding: theme.spacing(1),
    },
    browseitem: {
      margin: theme.spacing(1),
      padding: "0px",
      textTransform: "none",
    },
    infoDataSource: {
      color: "#0089DC",
    },
    infoName: {
      color: "#000000",
    },
    treeRoot: {
      display: "inline",
      "& div::before": {
        content: '"┌"'
      }
    },
    treeNode: {
      "& div::before": {
        content: '"└─"'
      }
    },
    childrenNodes: {
      "& div::before": {
        content: '"└──"'
      }
    },
    branch: {
      display: "inline",
    },
});

export default browseStyle;