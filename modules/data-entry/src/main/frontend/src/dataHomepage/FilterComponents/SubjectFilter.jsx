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

import React, { forwardRef, useState } from "react";
import { Avatar, ClickAwayListener, Grow, InputAdornment, ListItemAvatar, ListItemText, MenuList, MenuItem, Paper, Popper, TextField, Tooltip, Typography, withStyles } from "@material-ui/core";
import DescriptionIcon from "@material-ui/icons/Description";
import ErrorIcon from "@material-ui/icons/Error";
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS, VALUE_COMPARATORS } from "./FilterComparators.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS).concat(VALUE_COMPARATORS);

const SubjectFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, onChangeInput, questionDefinition, ...rest } = props;
  const [ search, setSearch ] = useState("");
  const [ results, setResults ] = useState([]);
  const [ popperOpen, setPopperOpen ] = useState(false);
  const [ timer, setTimer ] = useState();
  const [ error, setError ] = useState();
  const [ HTTPError, setHTTPError ] = useState();
  const [ requestID, setRequestID ] = useState(0);
  const [ hasSelectedValidSubject, setHasSelectedValidSubject ] = useState(false);

  let inputRef = React.useRef();
  let suggestionMenu = React.useRef();

  let startSubjectSearchTimeout = (query) => {
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
      closePopper();
    }

    // The user has input text, so the results are no longer valid
    setHasSelectedValidSubject(false);
  }

  let runQuery = (query) => {
    let url = new URL("/query", window.location.origin);
    let sqlquery = "SELECT s.* FROM [lfs:Subject] as s" + (query.search ? ` WHERE CONTAINS(s.'identifier', '*${query}*')` : "");
    url.searchParams.set("query", sqlquery);
    url.searchParams.set("limit", query.pageSize);
    url.searchParams.set("offset", query.page*query.pageSize);
    url.searchParams.set("req", requestID);
    // In the closure generated, postprocessResults will look for req_id, instead of requestID+1
    setRequestID(old => old+1);
    fetch(url)
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
    setHTTPError(response);
  }

  // Close the popper, and ensure that the currently entered field is correct
  let closePopper = () => {
    if (!hasSelectedValidSubject) {
      setError("Invalid subject selected");
    }
    setPopperOpen(false);
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

  return (
    <React.Fragment>
      <TextField
        className={classes.textField}
        InputProps={{
          className: (hasSelectedValidSubject ? "" : classes.invalidSubjectText),
          startAdornment:
            error && <InputAdornment position="end">
              <Tooltip title={error}>
                <ErrorIcon />
              </Tooltip>
            </InputAdornment>
        }}
        defaultValue={defaultValue}
        onChange={(event) => {
          // We won't register the onChangeInput here until the user has selected a subject from the list
          setSearch(event.target.value);
          startSubjectSearchTimeout(event.target.value);
        }}
        error={!!error /* Turn error into a boolean to avoid proptype warnings */}
        placeholder="empty"
        inputRef={ref}  // If necessary, the reference will be to the <input> element...
        ref={inputRef}     // But the reference that the popper will work off of is the entire <TextField>
        value={search}
        {...rest}
        />
      {/* Suggestions list using Popper */}
      <Popper
        open={popperOpen}
        anchorEl={inputRef.current}
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
                if (!inputRef.current.contains(event.target)) {
                  closePopper();
                }}}>
                <MenuList role="menu" className={classes.suggestions} ref={suggestionMenu}>
                  {HTTPError ?
                    /* Error message in the popper, if appropriate */
                    <MenuItem
                      className={classes.dropdownItem}
                      disabled
                      >
                      { /* Handle either a fetch error (which uses error.message/error.name)
                           or an HTTP error (error.status/error.statusText) */}
                      <ListItemText
                        primary={"Error: " + (HTTPError.statusText ? HTTPError.statusText : HTTPError.message)}
                        secondary={(HTTPError.status ? HTTPError.status : HTTPError.name)}
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
                        // Select this subject
                        onChangeInput(result["jcr:uuid"], result["identifier"]);
                        setHasSelectedValidSubject(true);
                        setPopperOpen(false);
                        setSearch(result["identifier"]);
                        setError(false);
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
  )
});

SubjectFilter.propTypes = {
  onChangeComparator: PropTypes.func,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    dateFormat: PropTypes.string
  })
}

const StyledSubjectFilter = withStyles(QuestionnaireStyle)(SubjectFilter)

export default StyledSubjectFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType == 'subject') {
    return [COMPARATORS, StyledSubjectFilter, 50];
  }
});
