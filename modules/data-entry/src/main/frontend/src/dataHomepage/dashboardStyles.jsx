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

import tableDialogStyles from "./tableDialogStyles.jsx";

const dashboardStyles = theme => ({
  dashboardContainer: {
    marginTop: theme.spacing(2),
    marginBottom: theme.spacing(6),
  },
  dashboardEntry: {
    "& > *": {
      height: "100%",
    },
    "& .MuiCardHeader-root" : {
      paddingBottom: 0,
    },
    "& .MuiCardHeader-avatar" : {
      zoom: "75%",
      marginTop: theme.spacing(-1.5),
    },
    "& .MuiTab-root": {
      width: "auto",
      minWidth: theme.spacing(10),
      paddingBottom: theme.spacing(1.5),
      paddingLeft: theme.spacing(2),
      paddingRight: theme.spacing(2),
      textTransform: "none",
      fontSize: "150%",
      letterSpacing: "unset",
    },
    "& .MuiTabs-indicator" : {
      height: theme.spacing(.5),
    },
    "& .MuiCardContent-root": {
      padding: theme.spacing(3, 0, 1, 0),
    },
    "& .MuiTableCell-body": {
      padding: theme.spacing(0, 2),
    },
    "& .MuiTableCell-body:last-child": {
      paddingRight: theme.spacing(.5),
    },
  },
  ...tableDialogStyles(theme),
});

export default dashboardStyles;
