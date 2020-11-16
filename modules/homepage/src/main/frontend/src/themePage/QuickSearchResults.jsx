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
import React from "react";
import LiveTable from "../dataHomepage/LiveTable.jsx";
import HeaderStyle from "../headerStyle.jsx";
import { getEntityIdentifier } from "./EntityIdentifier.jsx";

import { Button, Card, CardContent, CardHeader, withStyles } from "@material-ui/core";
import { Link } from "react-router-dom";

// Location of the quick search result metadata in a node, outlining what needs to be highlighted
const LFS_QUERY_MATCH_KEY = "lfs:queryMatch";
const LFS_QUERY_MATCH_PATH_KEY = "@path";
// Properties of the quick search result metadata
const LFS_QUERY_QUESTION_KEY = "question";
const LFS_QUERY_MATCH_BEFORE_KEY = "before";
const LFS_QUERY_MATCH_TEXT_KEY = "text";
const LFS_QUERY_MATCH_AFTER_KEY = "after";
const LFS_QUERY_MATCH_NOTES_KEY = "inNotes";

function QuickSearchResults(props) {

  const { classes } = props;

  const url = new URL(window.location);

  const anchor = url.searchParams.get('query');
  const allowedResourceTypes = url.searchParams.getAll('allowedResourceTypes');

  // Display how the query matched the result
  function QuickSearchMatch(resultData) {
    const matchData = resultData[LFS_QUERY_MATCH_KEY];
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

  // Display the result identifier with link to result section
  function QuickSearchIdentifier(resultData) {
    let anchorPath = resultData[LFS_QUERY_MATCH_KEY]?[LFS_QUERY_MATCH_PATH_KEY] : '';
    let fullPath = `/content.html${resultData["@path"]}#${anchorPath}`;
    if (resultData["jcr:primaryType"] == "lfs:Questionnaire") {
      fullPath = `/content.html/admin${resultData["@path"]}#${anchorPath}`;
    }
    return (<Link to={fullPath}>{getEntityIdentifier(resultData)}</Link>);
  }

  const columns = [
    {
      "key": "",
      "label": "Identifier",
      "format": QuickSearchIdentifier,
    },
    {
      "key": "jcr:created",
      "label": "Created on",
      "format": "date:YYYY-MM-DD HH:mm",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
    {
      "key": "",
      "label": "Match",
      "format": QuickSearchMatch,
    },
  ]

  // import the function

  return (
    <div>
      <Card>
        <CardHeader
          title={
            <Button className={classes.quickSearchResultsTitle}>
              Quick Search Results for <span className={classes.highlightedText}>{anchor}</span>
            </Button>
          }
        />
        <CardContent>
          <LiveTable
            columns={columns}
            customUrl={'/query?quick='+ encodeURIComponent(anchor) + allowedResourceTypes.map(i => `&allowedResourceTypes=${encodeURIComponent(i)}`).join('')}
            defaultLimit={10}
          />
        </CardContent>
      </Card>
    </div>
  );
}

export default withStyles(HeaderStyle)(QuickSearchResults);

