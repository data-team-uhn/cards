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

import queryStyle from "../resourceQuery/queryStyle.jsx";

const browseStyle = theme => ({
    ...queryStyle(theme),
    infoPaper: {
      padding: theme.spacing(2),
    },
    infoCard: {
      maxWidth: "500px",
      fontFamily: "'Roboto', 'Helvetica', 'Arial', sans-serif",
      position: "static",
      zIndex: "4 !important",
    },
    vocabularyAvatar: {
      height: 64,
      minWidth: 64,
      width: "auto",
      padding: theme.spacing(0.5),
      backgroundColor: theme.palette.info.main,
      color: theme.palette.getContrastText(theme.palette.info.main)
    },
    infoSection: {
      padding: theme.spacing(1, 0),
    },
    // The following ensures poppers are placed below the presentation (zIndex 1300)
    // but above everything else
    popperInfoOnTop: {
      zIndex: "1302 !important",
    },
    infoAboveBackdrop: {
      // When the info box is spawned from the browse menu,
      // it should no longer be greyed out
      zIndex: "1352 !important",
    },
    infoDialog: {
      zIndex: "1350 !important",
    },
    dialogPaper: {
      top: "0px",
      position: "absolute",
    },
    infoName: {
      whiteSpace: "normal", // Enable line wrapping
      color: theme.palette.text.primary,
      display: 'inline',
      cursor: 'pointer'
    },
    infoIcon: {
      whiteSpace: "nowrap"
    },
    treeContainer: {
      padding: theme.spacing(2,2,2,6),
    },
    // Tree components
    treeRoot: {
      display: "block",
    },
    treeNode: {
      // Nothing in here for now, but this is here in case we
      // want to apply themes in the future
      marginLeft: theme.spacing(2.75),
    },
    branch: {
      display: "block",
    },
    childDiv: {
      marginLeft: theme.spacing(2.75),
    },
    expandAction: {
      display: "inline-block",
      marginLeft: theme.spacing(-4),
    },
    loadingBranch: {
      "& .MuiSvgIcon-root": {
        visibility: "hidden",
      },
      "& .MuiCircularProgress-root": {
        position: "absolute",
      },
    },
    hiddenDiv: {
      display: "none",
    },
    focusedTermName: {
      fontWeight: "bold",
    },
    browseAction: {
      margin: theme.spacing(1)
    },
    selectionContainer: {
      padding: theme.spacing(0, 3, 2),
    },
    browserAnswerInstrustions: {
      padding: theme.spacing(1, 3, 0),
      marginBottom: theme.spacing(-2),
    },
    selectionChips: {
      margin: theme.spacing(0.5, 0.5),
    },
    termSelector: {
      margin: theme.spacing(-.25, 0, 0, -1.5),
    },
});

export default browseStyle;
