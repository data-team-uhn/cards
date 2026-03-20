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

import statusFlagStyles from "./statusFlagStyles.jsx";

const subjectStyles = theme => ({
  ...statusFlagStyles(theme),
  subjectAvatar: {
    backgroundColor: theme.palette.secondary.main,
    marginLeft: theme.spacing(-1),
    marginRight: theme.spacing(1.5),
    zoom: .75,
  },
  subjectFormAvatar: {
    backgroundColor: theme.palette.primary.main,
    marginTop: theme.spacing(-.5),
    marginRight: theme.spacing(1.5),
    zoom: .75,
  },
  childSubjectHeader: {
    marginLeft: theme.spacing(-5),
    "& > *" : {
      alignItems: "center",
    },
  },
  subjectContainer: {
    flexWrap: "nowrap",
    marginBottom: theme.spacing(4),
  },
  subjectNestedContainer: {
    paddingLeft: theme.spacing(5),
    "& .MuiGrid-container:last-child" : {
      marginBottom: "0 !important",
    },
  },
  subjectTabs: {
    "& .MuiTab-root" : {
      minWidth: "auto",
      textTransform: "initial",
    },
    "& .MuiTabs-indicator": {
      background: theme.palette.secondary.main,
    },
  },
  childSubjectHeaderButton: {
    left: theme.spacing(1),
  },
  childSubjectActions: {
    marginRight: theme.spacing(1),
  },
  formFlagBox: {
    display: "flex",
    flexWrap: "wrap",
    width: "100%",
  },
  childFormFlag: {
    marginBottom: theme.spacing(1),
    marginRight: theme.spacing(1),
  },
  childSubjectFlag: {
    marginLeft: theme.spacing(1),
    textTransform: "none",
  },
  formPreview: {
    padding: theme.spacing(1),
    background: theme.palette.action.hover,
  },
});

export default subjectStyles;
