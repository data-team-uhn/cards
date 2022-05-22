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

const queryStyle = theme => ({
  // The following ensures poppers are placed below the presentation (zIndex 1300)
  // but above everything else
  popperListOnTop: {
    zIndex: "1301 !important",
  },
  popperNav: {
    // Old material-dashboard-react style, overridden because of issues with small screens
  },
  errorSnack: {
    backgroundColor: theme.palette.error.dark,
  },
  searchWrapper: {
    margin: theme.spacing(0),
    position: 'relative',
    display: 'inline-block',
    paddingBottom: theme.spacing(0),
    "& .MuiInputBase-root" : {
      minWidth: "250px",
    },
  },
  search: {
    paddingBottom: "0px",
    margin: "0px"
  },
  nestedSearchInput: {
    marginLeft: theme.spacing(-2.5),
    marginTop: theme.spacing(-1),
    "& .MuiInputBase-root" : {
      minWidth: "218px !important",
    },
    "& + .MuiLinearProgress-root": {
      marginLeft: theme.spacing(-2.5),
    }
  },
  searchInput: {
    marginTop: "6px !important",
  },
  searchLabel: {
    marginTop: theme.spacing(-1.5),
  },
  searchShrink: {
    transform: "translate(0, 12px) scale(0.7)",
  },
  searchButton: {
    cursor: "pointer"
  },
  dropdownItem: {
    whiteSpace: 'normal',
  },
  inactiveProgress: {
    visibility: "hidden"
  },
  progressIndicator: {
    marginBottom: theme.spacing(-.5),
    height: theme.spacing(.5)
  },
  infoButton: {
    marginLeft: theme.spacing(0.5),
    marginTop: theme.spacing(-0.25),
  },
  noResults: {
    marginBottom: theme.spacing(-1),
  },
  dropdownMessage: {
    opacity: "1 !important",
  },
});

export default queryStyle;
