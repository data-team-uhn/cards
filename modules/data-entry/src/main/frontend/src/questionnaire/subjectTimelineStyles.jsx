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
  timeline: {
    maxWidth: "1000px",
    margin: "auto"
  },
  timelineContainer: {
    alignItems: "center",
  },
  timelineContent: {
    padding: theme.spacing(1,3,3),
  },
  timelinePaper: {
    padding: theme.spacing(1, 2),
  },
  timelineAncestor: {
    opacity: 0.3,
    "&:hover": {
      opacity: 1,
    },
  },
  timelineDate: {
    lineHeight: "1em",
    marginLeft: theme.spacing(-2),
    marginRight: theme.spacing(-2),
    color: theme.palette.primary.main
  },
  timelineDateEntry: {
    paddingBottom: theme.spacing(2),
  },
  timelineDateEntryFinal: {
    paddingBottom: 0,
  },
  timelineConnectorGroup: {
    display: "flex",
    alignItems: "center",
    flexDirection: "column",
    flexGrow: 1
  },
  timelineConnectorLine: {
    backgroundColor: theme.palette.grey["200"]
  },
  timelineCircle: {
    background: theme.palette.grey["200"],
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
      borderBottomColor: theme.palette.grey["200"],
      borderLeft: "12px solid transparent",
      borderRight: "12px solid transparent",
      position: "absolute",
      top: "-7px"
    },
    "&:after": {
      content: "''",
      display: "block",
      borderTop: "12px solid",
      borderTopColor: theme.palette.grey["200"],
      borderLeft: "12px solid transparent",
      borderRight: "12px solid transparent",
      position: "absolute",
      bottom: "-7px"
    }
  },
  timelineSeparator: {
    minHeight: theme.spacing(12)
  },
});

export default subjectTimelineStyles;
