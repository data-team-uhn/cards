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
import React, { useState } from "react";
import LiveTable from "./LiveTable.jsx";
import HeaderStyle from "../headerStyle.jsx";

import { Button, Card, CardContent, CardHeader, Grid, withStyles, ListItemText, Tooltip, Fab } from "@material-ui/core";
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
  // fix issue with classes

  const anchor = location.hash.substr(1);

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

  let defaultRedirect = (row, props) => {
    // Redirect using React-router
    const anchorPath = row[LFS_QUERY_MATCH_KEY][LFS_QUERY_MATCH_PATH_KEY];
    let path = (row["jcr:primaryType"] == "lfs:Questionnaire") ? "/content.html/admin" : "/content.html";
    if (row["@path"]) {
        path = `${path}${row["@path"]}#${anchorPath}`;
    }
    return path;
  }

  const columns = [
    {
      "key": "",
      "label": "Identifier",
      "format": (resultData) => (<Link to={defaultRedirect(resultData, props)}>{resultData["@name"] || anchor}</Link>),
    },
    {
      "key": "",
      "label": "Questionnaire",
      "format": (resultData) => ((resultData.questionnaire?.title?.concat(' ') || '') + (resultData["jcr:primaryType"]?.replace(/lfs:/,"") || '')),
    },
    {
      "key": "",
      "label": "Subject",
      "format": (resultData) => ((resultData["jcr:primaryType"] == "lfs:Form" && resultData.subject)
        ? <Link to={"/content.html/Subjects/"+resultData.subject["@name"]}>{resultData.subject?.identifier}</Link>
        : null),
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
    {
      "key": "jcr:created",
      "label": "Created on",
      "format": "date:YYYY-MM-DD HH:mm",
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
            <Button>
              Quick Search Results for <span className={classes.highlightedText}>{anchor}</span>
            </Button>
          }
        />
        <CardContent>
          <LiveTable
            columns={columns}
            customUrl={'/query?quick='+ encodeURIComponent(anchor)}
            defaultLimit={10}
          />
        </CardContent>
      </Card>
    </div>
  );
}

export default withStyles(HeaderStyle)(QuickSearchResults);

