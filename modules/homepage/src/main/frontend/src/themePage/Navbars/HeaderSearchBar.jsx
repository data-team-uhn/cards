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
import { withRouter } from "react-router-dom";

import { Avatar, ListItemText, ListItemAvatar, Typography, withStyles }  from "@material-ui/core";
import DescriptionIcon from "@material-ui/icons/Description";
import HeaderStyle from "./headerStyle.jsx";
import SearchBar, { DEFAULT_QUERY_URL, DEFAULT_MAX_RESULTS } from "../../SearchBar.jsx"; // In the commons module

// Location of the quick search result metadata in a node, outlining what needs to be highlighted
const LFS_QUERY_MATCH_KEY = "lfs:queryMatch";
// Properties of the quick search result metadata
const LFS_QUERY_QUESTION_KEY = "question";
const LFS_QUERY_MATCH_BEFORE_KEY = "before";
const LFS_QUERY_MATCH_TEXT_KEY = "text";
const LFS_QUERY_MATCH_AFTER_KEY = "after";
const LFS_QUERY_MATCH_NOTES_KEY = "inNotes";

function HeaderSearchBar(props) {
  const { classes, doNotEscapeQuery, ...rest } = props;

  // Runs a fulltext request
  let createQuery = (query, requestID) => {
    let new_url = new URL(DEFAULT_QUERY_URL, window.location.origin);
    new_url.searchParams.set("quick", encodeURIComponent(query));
    doNotEscapeQuery && new_url.searchParams.set("doNotEscapeQuery", "true");
    new_url.searchParams.set("limit", DEFAULT_MAX_RESULTS);
    new_url.searchParams.set("req", requestID);
    return(new_url);
  }

  // Generate a human-readable info about the resource (form) matching the query:
  // * questionnaire title (if available) and result type, followed by
  // * the form's subject name (if available) or the resource's uuid
  function QuickSearchResultHeader(props) {
    const {resultData} = props;
    return resultData && (
      <div>
        <Typography variant="body2" color="textSecondary">
          {(resultData.questionnaire?.title?.concat(' ') || '') + (resultData["jcr:primaryType"]?.replace(/lfs:/,"") || '')}
        </Typography>
        {resultData.subject?.identifier || resultData["jcr:uuid"] || ''}
      </div>
    ) || null
  }

  // Display how the query matched the result
  function QuickSearchMatch(props) {
    const {matchData} = props;
    // Adjust the question text to reflect the notes, if the match was on the notes
    let questionText = matchData[LFS_QUERY_QUESTION_KEY] + (matchData[LFS_QUERY_MATCH_NOTES_KEY] ? " / Notes" : "");
    return matchData && (
      <React.Fragment>
        <span className={classes.queryMatchKey}>{questionText}</span>
        <span className={classes.queryMatchSeparator}>: </span>
        <span className={classes.queryMatchBefore}>{matchData[LFS_QUERY_MATCH_BEFORE_KEY]}</span>
        <span className={classes.highlightedText}>{matchData[LFS_QUERY_MATCH_TEXT_KEY]}</span>
        <span className={classes.queryMatchAfter}>{matchData[LFS_QUERY_MATCH_AFTER_KEY]}</span>
      </React.Fragment>
    ) || null
  }

  // Display a quick search result
  // If it's a resource, show avatar, category, and title
  // Otherwise, if it's a generic entry, simply display the name
  function quickSearchResult(props) {
    let { resultData } = props;
    return <React.Fragment>
      <ListItemAvatar><Avatar className={classes.searchResultAvatar}><DescriptionIcon /></Avatar></ListItemAvatar>
      <ListItemText
        primary={(<QuickSearchResultHeader resultData={resultData} />)}
        secondary={(<QuickSearchMatch matchData={resultData[LFS_QUERY_MATCH_KEY]} />)}
        className={classes.dropdownItem}
      />
    </React.Fragment>
  }

  let redirectAndClose = (event, row) => {
    // Redirect using React-router
    if (row["@path"]) {
      props.history.push("/content.html" + row["@path"]);
      closeSidebar && closeSidebar();
      setPopperOpen(false);
    }
  }

  return(
    <SearchBar
      queryConstructor={createQuery}
      resultConstructor={quickSearchResult}
      onSelect={redirectAndClose}
      {...rest}
      />
  );
}

HeaderSearchBar.propTypes = {
  invertColors: PropTypes.bool,
  doNotEscapeQuery: PropTypes.bool
}

export default withStyles(HeaderStyle)(withRouter(HeaderSearchBar));
