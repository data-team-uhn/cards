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

import dropdownStyle from "./dropdownStyle.jsx";

const browseStyle = theme => ({
    ...dropdownStyle(theme),
    dialog: {
      padding: theme.spacing(1),
    },
    headbar: {
      backgroundColor: "#000000",
      color: "#FFFFFF",
    },
    closeButton: {
      float: "right",
      height: "100%",
      padding: "0px",
      fontSize: "10px",
    },
    headbarText: {
      display: "inline",
      color: "#FFFFFF",
      paddingLeft: "10px",
      paddingTop: "5px",
    },
    browseitem: {
      margin: "0px",
      padding: "0px",
      textTransform: "none",
    },
    infoDataSource: {
      color: "#0089DC",
    },
    infoName: {
      color: "#000000",
    },
    infoButton: {
      width: "20px",
    },
    treeRoot: {
      display: "block",
      paddingTop: theme.spacing(1),
      paddingLeft: theme.spacing(1),
      paddingRight: theme.spacing(1),
      "& div::before": {
        content: '"┌"',
      },
      "& div:nth-child(n+2):before": {
        content: '"├"',
      }
    },
    treeNode: {
      paddingLeft: theme.spacing(1),
      paddingBottom: theme.spacing(1),
    },
    branch: {
      display: "block",
      paddingRight: theme.spacing(1),
      "&:not(:last-child)::before": {
        content: '"├─"',
      },
      "&::before": {
        content: '"└─"',
      },
    },
    childDiv: {
      marginLeft: "22px",
    },
    arrowDiv: {
      width: "10px",
    },
    hiddenDiv: {
      display: "none",
    },
    boldedName: {
      fontWeight: "bold",
    },
});

export default browseStyle;