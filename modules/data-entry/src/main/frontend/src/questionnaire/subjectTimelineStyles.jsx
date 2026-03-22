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

const subjectTimelineStyles = theme => ({
  timelineLoading: {
    display: "flex",
    margin: "auto",
  },
  timeline: {
    maxWidth: "1000px",
    margin: "auto",
    "& .MuiTimelineContent-root, .MuiTimelineOppositeContent-root": {
      margin: theme.spacing(0, 1),
    },
    "& .MuiTimelineSeparator-root": {
      minHeight: theme.spacing(12)
    },
    "& .MuiTimelineConnector-root": {
      backgroundColor: theme.palette.grey[200],
    },
  },
  timelineAncestor: {
    opacity: 0.3,
    "&:hover": {
      opacity: 1,
    },
  },
  timelineLabeledConnector: {
    display: "flex",
    alignItems: "center",
    flexDirection: "column",
    flexGrow: 1
  },
  timelineCircle: {
    background: theme.palette.grey[200],
    position: "absolute",
    top: "50%",
    transform: "translateY(calc(-50% + 14px))",
    width: "35px",
    height: "35px",
    borderRadius: "50%",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    "&:before": {
      content: "''",
      display: "block",
      borderBottom: "12px solid",
      borderBottomColor: theme.palette.grey[200],
      borderLeft: "12px solid transparent",
      borderRight: "12px solid transparent",
      position: "absolute",
      top: "-7px"
    },
    "&:after": {
      content: "''",
      display: "block",
      borderTop: "12px solid",
      borderTopColor: theme.palette.grey[200],
      borderLeft: "12px solid transparent",
      borderRight: "12px solid transparent",
      position: "absolute",
      bottom: "-7px"
    }
  },
});

export default subjectTimelineStyles;
