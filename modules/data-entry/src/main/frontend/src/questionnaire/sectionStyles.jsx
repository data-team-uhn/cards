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

import { grey } from '@mui/material/colors';

import { GRID_SPACE_UNIT } from "./questionnaireConstants.jsx";

// Shared factory for sticky header/footer questionnaire sections
const stickySectionStyles = (theme, position = "top") => ({
  "&.cards-edit-section" : {
    position: "sticky",
    ...(position === "top"
      ? { top: 0, zIndex: 2, paddingTop: 0 }
      : { bottom: 0, paddingBottom: "0 !important" }),
  },
  "& > .MuiCollapse-wrapper" : {
    border: "1px solid " + theme.palette.primary.light,
  },
  "& .MuiGrid-root:not(:first-of-type)": {
    paddingTop: 0,
  },
  "& .MuiGrid-root:not(:last-child)": {
    paddingBottom: 0,
  },
  "& .MuiGrid-root:not(.MuiCollapse-container) > *": {
    background: grey[100],
  },
  "& .MuiCard-root" : {
    borderColor: "transparent",
  },
});

const sectionStyles = theme => ({
  sectionHeader: {
    paddingBottom: "0 !important",
    "& > h5" : {
      padding: theme.spacing(1, GRID_SPACE_UNIT),
      background: theme.palette.action.hover,
      borderBottom: "1px solid transparent",
    },
    "& > .MuiTypography-caption" : {
      padding: theme.spacing(0, GRID_SPACE_UNIT),
    },
  },
  horizontalSection: {
    [theme.breakpoints.up('md')]: {
      flexFlow: "row",
      flexWrap: "wrap",
    },
    "& > .MuiGrid-root > .MuiCard-root" : {
      [theme.breakpoints.up('md')]: {
        height: "100%",
      },
    },
    "& .MuiTextField-root, .MuiInputBase-root" : {
      [theme.breakpoints.between('md','xl')]: {
        minWidth: "200px !important",
      },
    },
    "& .MuiListItem-root .MuiTextField-root,  .MuiListItem-root .MuiInputBase-root" : {
      [theme.breakpoints.between('md','xl')]: {
        minWidth: "160px !important",
      },
    },
  },
  collapsedSection: {
    padding: "0 !important",
  },
  hiddenSection: {
    display: "none",
  },
  entryActionIcon: {
    float: "right",
    marginRight: theme.spacing(1),
  },
  recurrentSectionInstance: {
    marginBottom: theme.spacing(2*GRID_SPACE_UNIT),
  },
  headerSection : stickySectionStyles(theme, "top"),
  footerSection : stickySectionStyles(theme, "bottom"),
  highlightedSection: {
    "& .MuiGrid-root > .MuiCard-root, .MuiGrid-root > .MuiTypography-h5": {
      borderColor: theme.palette.warning.main,
      boxShadow: `1px 1px 2px ${theme.palette.warning.main}`,
    },
  },
});

export default sectionStyles;
