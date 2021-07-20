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
import PropTypes from "prop-types";
import React from "react";

import { blue, green, orange } from '@material-ui/core/colors';
import { Avatar, ListItem, ListItemText, ListItemAvatar }  from "@material-ui/core";
import DescriptionIcon from "@material-ui/icons/Description";
import AssignmentIndIcon from "@material-ui/icons/AssignmentInd";
import AssignmentIcon from '@material-ui/icons/Assignment';
import { Link } from "react-router-dom";
import { getEntityIdentifier } from "../EntityIdentifier.jsx";

// Location of the quick search result metadata in a node, outlining what needs to be highlighted
const CARDS_QUERY_MATCH_KEY = "cards:queryMatch";
const CARDS_QUERY_MATCH_PATH_KEY = "@path";

// Properties of the quick search result metadata
const CARDS_QUERY_QUESTION_KEY = "question";
const CARDS_QUERY_MATCH_BEFORE_KEY = "before";
const CARDS_QUERY_MATCH_TEXT_KEY = "text";
const CARDS_QUERY_MATCH_AFTER_KEY = "after";
const CARDS_QUERY_MATCH_NOTES_KEY = "inNotes";

// Display how the query matched the result
export function QuickSearchMatch(props) {
    const { matchData, classes } = props;
    if (!matchData) { return null; }
    // Adjust the question text to reflect the notes, if the match was on the notes
    let questionText = matchData[CARDS_QUERY_QUESTION_KEY] + (matchData[CARDS_QUERY_MATCH_NOTES_KEY] ? " / Notes" : "");
    return (
      <React.Fragment>
        <span className={classes.queryMatchKey}>{questionText}</span>
        <span className={classes.queryMatchSeparator}>: </span>
        <span className={classes.queryMatchBefore}>{matchData[CARDS_QUERY_MATCH_BEFORE_KEY]}</span>
        <span className={classes.highlightedText}>{matchData[CARDS_QUERY_MATCH_TEXT_KEY]}</span>
        <span className={classes.queryMatchAfter}>{matchData[CARDS_QUERY_MATCH_AFTER_KEY]}</span>
      </React.Fragment>
    )
}

function MatchAvatar(props) {
    const { matchData, classes } = props;
    let icon = <DescriptionIcon />;
    let style = '';
    switch (matchData["jcr:primaryType"]) {
      case "cards:Subject":
        icon = <AssignmentIndIcon />;
        style = { backgroundColor: green[500] };
        break;
      case "cards:Questionnaire":
        icon = <AssignmentIcon  />;
        style = { backgroundColor: orange[500] };
        break;
      // default covers other cases
      default:
        icon = <DescriptionIcon />;
        style = { backgroundColor: blue[500] };
        break;
    }
    return <Avatar className={classes.searchResultAvatar} style={style}>{icon}</Avatar>;
}

function ListItemLink(props) {
  return <ListItem alignItems="center" button component="a" {...props} />;
}

  // Display a quick search result identifier with link to result section
export function QuickSearchIdentifier(props) {
    let { resultData, hideMatchInfo, disableLink, classes } = props;
    let anchorPath = resultData[CARDS_QUERY_MATCH_KEY] ? resultData[CARDS_QUERY_MATCH_KEY][CARDS_QUERY_MATCH_PATH_KEY] : '';
    let fullPath = `/content.html${resultData["@path"]}#${anchorPath}`;
    if (resultData["jcr:primaryType"] == "cards:Questionnaire") {
      fullPath = `/content.html/admin${resultData["@path"]}#${anchorPath}`;
    }
    let showMatchInfo = !hideMatchInfo && resultData[CARDS_QUERY_MATCH_KEY];
    return (<ListItemLink href={disableLink ? '#' : fullPath}>
              <ListItemAvatar>
                <MatchAvatar matchData={resultData} classes={classes}></MatchAvatar>
              </ListItemAvatar>
              <ListItemText
                primary={resultData.entityIdentifier || getEntityIdentifier(resultData)}
                secondary={showMatchInfo && (<QuickSearchMatch matchData={resultData[CARDS_QUERY_MATCH_KEY]} classes={classes}></QuickSearchMatch>)}
                className={classes.dropdownItem}
              />
           </ListItemLink>)
}
