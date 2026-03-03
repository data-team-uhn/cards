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

import multipleChoiceStyles from "./multipleChoiceStyles.jsx";

const questionMatrixStyles = theme => ({
  ...multipleChoiceStyles(theme),
  questionMatrixView: {
    width: "auto",
    "& th, td" : {
      border: "none",
      padding: theme.spacing(1, 1, 1, 0),
      verticalAlign: "baseline",
    },
    "& td" : {
      fontSize: "1rem",
      fontWeight: "300 !important",
      padding: theme.spacing(1),
    },
    "& .wmde-markdown::before" : {
      display: "none",
    },
  },
  questionMatrixControls: {
    "& .MuiTableHead-root th": {
      fontWeight: "bold",
      padding: theme.spacing(.5, 1, 1, .5),
      verticalAlign: "top",
    },
    "& .MuiTableBody-root th": {
      paddingLeft: 0,
    },
    "& .MuiTableBody-root tr:last-child .MuiTableCell-root": {
      border: "0 none",
    },
  },
  questionMatrixHorizontal: {
    position: "relative",
    "& .MuiTableHead-root" : {
      position: "sticky",
      boxShadow: "0 1px 0 " + theme.palette.divider,
      zIndex: 1,
      "& th" : {
        backgroundColor: theme.palette.background.paper,
      },
    },
    "& .MuiFormControlLabel-root" : {
      margin: "0 !important",
    },
    "& .MuiFormControlLabel-label, .MuiFormControlLabel-root + .MuiTypography-root": {
      display: "none",
    },
  },
  questionMatrixVertical: {
    display: "block",
    "& .MuiTableBody-root, .MuiTableRow-root, .MuiTableCell-root" : {
      display: "block",
      width: "100%",
    },
    "& td": {
      padding: theme.spacing(1, 0),
    },
  },
  questionMatrixStackedAnswer : {
    "&:not(:first-of-type) th" : {
      paddingTop: theme.spacing(3),
    },
    "& td" : {
      padding: 0,
    },
    "&:not(:last-child) td" : {
      paddingBottom: theme.spacing(3),
      borderBottom: "1px solid " + theme.palette.divider,
    },
  },
  questionMatrixFullEntry : {
    "& th" : {
      paddingBottom: theme.spacing(3),
    },
    "& th, td:not(:last-child)": {
      border: "0 none",
    },
    "&:not(:last-child) td:last-child" : {
      paddingBottom: theme.spacing(4),
    },
    "&:not(:first-of-type) th": {
      paddingTop: theme.spacing(4),
    },
  },
});

export default questionMatrixStyles;
