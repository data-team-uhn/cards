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
      // The dialogue appears in the wrong location without the following
      padding: theme.spacing(1),
    },
    dialogPaper: {
      top: "0px",
      position: "absolute",
    },
    // Top part of the dialog
    headbar: {
      backgroundColor: "#000000",
      color: "#FFFFFF",
      padding: theme.spacing(0),
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
    // Info box components
    infoDataSource: {
      color: "#0089DC",
    },
    infoName: {
      color: "#000000",
      whiteSpace: "normal", // Enable line wrapping
    },
    infoButton: {
      width: "20px",
    },
    treeContainer: {
      padding: theme.spacing(2),
    },
    // Tree components
    treeRoot: {
      display: "block",
      "& >div::before": {
        content: '"├"',
      },
      "& >div:first-child:before": {
        content: '"┌"',
      }
    },
    treeNode: {
      // Nothing in here for now, but this is here in case we
      // want to apply themes in the future
    },
    branch: {
      display: "block",
      "&::before": {
        content: '"├─"',
      },
      "&:last-child::before": {
        content: '"└─"',
      },
    },
    // Components of the browser list items
    browseitem: {
      margin: "0px",
      padding: "0px",
      textTransform: "none",
    },
    childDiv: {
      marginLeft: "22px",
    },
    arrowDiv: {
      width: "0.8em",
      display: "inline-block",
      align: "center",
    },
    hiddenDiv: {
      display: "none",
    },
    boldedName: {
      fontWeight: "bold",
    },
});

export default browseStyle;