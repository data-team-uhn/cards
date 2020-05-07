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
import React, { useState } from "react";
import { withRouter } from "react-router-dom";

import { ClickAwayListener, Grow, IconButton, Input, InputAdornment, ListItemText, MenuItem, ListItemAvatar, Avatar}  from "@material-ui/core";
import { MenuList, Paper, Popper, Typography, withStyles } from "@material-ui/core";
import DescriptionIcon from "@material-ui/icons/Description";
import Search from "@material-ui/icons/Search";
import HeaderStyle from "./headerStyle.jsx";

const QUERY_URL = "/query";
const MAX_RESULTS = 5;

// Location of the quick search result metadata in a node, outlining what needs to be highlighted
const LFS_QUERY_MATCH_KEY = "lfs:queryMatch";
// Properties of the quick search result metadata
const LFS_QUERY_QUESTION_KEY = "question";
const LFS_QUERY_MATCH_BEFORE_KEY = "before";
const LFS_QUERY_MATCH_TEXT_KEY = "text";
const LFS_QUERY_MATCH_AFTER_KEY = "after";
const LFS_QUERY_MATCH_NOTES_KEY = "inNotes";

function SearchBar(props) {
  const { classes, className, closeSidebar, invertColors, doNotEscapeQuery } = props;
  const [ search, setSearch ] = useState("");
  const [ results, setResults ] = useState([]);
  const [ popperOpen, setPopperOpen ] = useState(false);
  const [ timer, setTimer ] = useState();
  const [ error, setError ] = useState();
  const [ requestID, setRequestID ] = useState(0);

  let input = React.useRef();
  let suggestionMenu = React.useRef();
  let searchBar = React.useRef();

  // Callback to update the value of the search bar. Sends off a delayed fulltext request
  let changeSearch = (query) => {
    // Reset the timer if it exists
    if (timer !== null) {
      clearTimeout(timer);
    }

    setSearch(query);
    setResults([{
      name: 'Searching...',
      '@path': '',
      'disabled': true
    }]);
    setError(false);

    // If there is a query, execute it
    if (query !== "") {
      setTimer(setTimeout(runQuery, 500, query));
      setPopperOpen(true);
    } else {
      setTimer(null);
      setPopperOpen(false);
    }
  }

  // Runs a fulltext request
  let runQuery = (query) => {
    let new_url = new URL(QUERY_URL, window.location.origin);
    new_url.searchParams.set("quick", encodeURIComponent(query));
    doNotEscapeQuery && new_url.searchParams.set("doNotEscapeQuery", "true");
    new_url.searchParams.set("limit", MAX_RESULTS);
    new_url.searchParams.set("req", requestID);
    // In the closure generated, postprocessResults will look for req_id, instead of requestID+1
    setRequestID(requestID+1);
    fetch(new_url)
      .then(response => response.ok ? response.json() : Promise.reject(response))
      .then(postprocessResults)
      .catch(handleError);
  }

  // Callback to store the results of the top results, or to display 'No results'
  let postprocessResults = (json) => {
    // Ignore this result if the request ID does not match our last request
    if (json["req"] != requestID) {
      return;
    }

    // Show the results, if any
    if (json["rows"].length > 0) {
      setResults(json["rows"]);
    } else {
      setResults([{
        name: 'No results',
        '@path': '',
        'disabled': true
      }]);
    }
  }

  // Error handling
  let handleError = (response) => {
    setError(response);
  }

  // Generate a human-readable info about the resource (form) matching the query:
  // * questionnaire title (if available) and result type, followed by
  // * the form's subject name (if available) or the resource's uuid
  function QuickSearchResultHeader(props) {
    const {resultData} = props;
    const entry = /Forms\/(.+)/.exec(resultData["@path"]);
    return resultData && (
      <div>
        <Typography variant="body2" color="textSecondary">
          {(resultData.questionnaire?.title?.concat(' ') || '') + (resultData["jcr:primaryType"]?.replace(/lfs:/,"") || '')}
        </Typography>
        {resultData.subject?.identifier || entry?.[1] || ''}
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
  function QuickSearchResult(props) {
    const {resultData} = props;
    return resultData["jcr:primaryType"] && (
      <React.Fragment>
        <ListItemAvatar><Avatar className={classes.searchResultAvatar}><DescriptionIcon /></Avatar></ListItemAvatar>
        <ListItemText
          primary={(<QuickSearchResultHeader resultData={resultData} />)}
          secondary={(<QuickSearchMatch matchData={resultData[LFS_QUERY_MATCH_KEY]} />)}
          className={classes.dropdownItem}
        />
      </React.Fragment>
    ) || (
       <ListItemText
          primary={resultData.name || ''}
          className={classes.dropdownItem}
        />
    ) || null
  }

  return(
    <React.Fragment>
      <Input
        type="text"
        placeholder="Search"
        value={search}
        ref={searchBar}
        onChange={(event) => changeSearch(event.target.value)}
        onFocus={(event) => {
          // Rerun the query
          changeSearch(search);
        }}
        onKeyDown={(event) => {
          if (event.key == 'ArrowDown' && suggestionMenu.current.children.length > 0) {
            // Move the focus to the suggestions list
            suggestionMenu.current.children[0].focus();
            event.preventDefault();
          }
        }}
        endAdornment={
          <InputAdornment position="end">
            <IconButton
              className={invertColors ? classes.invertedColors : ""}
              onClick={(event) => {
                input?.current?.focus();
              }}
            >
              <Search />
            </IconButton>
          </InputAdornment>
        }
        className={
          classes.search
          + " " + (invertColors ? classes.invertedColors : "")
          + " " + (className ? className : "")
        }
        inputRef={input}
        />
      {/* Suggestions list using Popper */}
      <Popper
        open={popperOpen}
        anchorEl={input.current}
        className={classes.aboveBackground}
        modifiers={{
          keepTogether: {enabled: true}
        }}
        placement = "bottom-start"
        transition
        keepMounted
        >
        {({ TransitionProps }) => (
          <Grow
            {...TransitionProps}
            style={{transformOrigin: "top"}}
          >
            <Paper square className={classes.suggestionContainer}>
              <ClickAwayListener onClickAway={(event) => {
                // Ignore clickaway events if they're just clicking on the input box or search button
                if (!searchBar.current.contains(event.target)) {
                  setPopperOpen(false)
                }}}>
                <MenuList role="menu" className={classes.suggestions} ref={suggestionMenu}>
                  {error ?
                    /* Error message in the popper, if appropriate */
                    <MenuItem
                      className={classes.dropdownItem}
                      disabled
                      >
                      { /* Handle either a fetch error (which uses error.message/error.name)
                           or an HTTP error (error.status/error.statusText) */}
                      <ListItemText
                        primary={"Error: " + (error.statusText ? error.statusText : error.message)}
                        secondary={(error.status ? error.status : error.name)}
                        primaryTypographyProps={{color: "error"}}
                        />
                    </MenuItem>
                  : results.map( (result, i) => (
                    /* Results if no errors occurred */
                    <MenuItem
                      className={classes.dropdownItem}
                      key={i}
                      disabled={result["disabled"]}
                      onClick={(e) => {
                        const anchor = result[LFS_QUERY_MATCH_KEY][LFS_QUERY_QUESTION_KEY].replace(/\s/g, '');
                        // Redirect using React-router
                        if (result["@path"]) {
                          props.history.push({
                            pathname: "/content.html" + result["@path"],
                            hash: anchor
                          });
                          closeSidebar && closeSidebar();
                          setPopperOpen(false);
                        }
                        }}
                      >
                      <QuickSearchResult resultData={result} />
                    </MenuItem>
                  ))}
                </MenuList>
              </ClickAwayListener>
            </Paper>
          </Grow>
        )}
      </Popper>
    </React.Fragment>
  );
}

SearchBar.propTypes = {
  invertColors: PropTypes.bool,
  doNotEscapeQuery: PropTypes.bool
}

export default withStyles(HeaderStyle)(withRouter(SearchBar));
