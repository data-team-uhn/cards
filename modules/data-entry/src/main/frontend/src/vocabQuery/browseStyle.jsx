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

import queryStyle from "./queryStyle.jsx";

const browseStyle = theme => ({
    ...queryStyle(theme),
    dialog: {
      // The dialogue appears in the wrong location without the following
      padding: theme.spacing(1),
    },
    dialogPaper: {
      top: "0px",
      position: "absolute",
    },
     closeButton: {
      position: 'absolute',
      right: theme.spacing(1),
      top: theme.spacing(1),
    },
    infoName: {
      whiteSpace: "normal", // Enable line wrapping
    },
    treeContainer: {
      padding: theme.spacing(2),
    },
    // Tree components
    treeRoot: {
      display: "block",
    },
    treeNode: {
      // Nothing in here for now, but this is here in case we
      // want to apply themes in the future
      marginLeft: "22px",
    },
    branch: {
      display: "block",
    },
    // Components of the browser list items
    browseitem: {
      margin: "0px",
      padding: "0px",
      textTransform: "none",
      color: theme.palette.primary.main,
      backgroundColor: 'transparent',
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
    arrowButton: {
      minWidth: "0px",
      backgroundColor: 'transparent',
      right: theme.spacing(0.5)
    },
});

export default browseStyle;
