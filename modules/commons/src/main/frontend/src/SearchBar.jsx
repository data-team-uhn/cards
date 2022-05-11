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
import React, { useState, useContext } from "react";
import { withRouter } from "react-router-dom";

import { ClickAwayListener, Grow, IconButton, Input, InputAdornment, ListItemText, MenuItem, ListItemAvatar, Avatar }  from "@material-ui/core";
import { MenuList, Paper, Popper } from "@material-ui/core";
import withStyles from '@material-ui/styles/withStyles';
import { Link } from "react-router-dom";
import { getEntityIdentifier } from "./themePage/EntityIdentifier.jsx";
import DescriptionIcon from "@material-ui/icons/Description";
import Search from "@material-ui/icons/Search";
import HeaderStyle from "./headerStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

export const DEFAULT_QUERY_URL = "/query";
export const DEFAULT_MAX_RESULTS = 5;

// Location of the quick search result metadata in a node, outlining what needs to be highlighted after redirect and where
const CARDS_QUERY_MATCH_KEY = "cards:queryMatch";
const CARDS_QUERY_MATCH_PATH_KEY = "@path";
const CARDS_QUERY_TEXT_KEY = "text";

/**
 * A component that renders a search bar, similar to autocomplete. It will fire off a query to /query, and parse the results as a selectable list.
 * 
 * @param {bool} invertColors If true, inverts the colours of various elements
 * @param {string} defaultValue The default string to place in the search bar
 * @param {func} onChange Function to call when the input has changed. Default: redirect the user to the found node.
 * @param {func} onPopperClose Function to call when the suggestions list is closed.
 * @param {func} onSelect Function that takes (event, row), called when an option from the autocomplete list is selected.
 * @param {func} onSelectFinish Function to call after onSelect
 * @param {func} queryConstructor Function that takes (query, requestID) and returns a URL to query for suggestions. Default: use /query?query
 * @param {func} resultConstructor Function that constructs a DOM element from a row of results.
 * @param {bool} showAllResultsLink If true, show the link “See all results” of the bottom of the results dropdown
 * @param {bool} disableDropdownItemLink If true, disable links for results dropdown items
 * @param {object} staticContext Unused, defined here to trap the inserted prop from being passed on with ...rest to the Input, where it is invalid
 * Other props will be forwarded to the Input element
 */
function SearchBar(props) {
  const { classes, className, defaultValue, invertColors, onChange, onPopperClose, onSelect, onSelectFinish, queryConstructor, resultConstructor, staticContext, showAllResultsLink, disableDropdownItemLink, ...rest } = props;
  const [ search, setSearch ] = useState(defaultValue);
  const [ results, setResults ] = useState([]);
  const [ moreResults, setMoreResults ] = useState(0);
  const [ popperOpen, setPopperOpen ] = useState(false);
  const [ timer, setTimer ] = useState();
  const [ error, setError ] = useState();
  const [ requestID, setRequestID ] = useState(0);

  const [ limit, setLimit ] = useState(5);
  const [ allowedResourceTypes, setAllowedResourceTypes ] = useState([]);
  const [ showTotalRows, setShowTotalRows ] = useState(true);
  const [ fetched, setFetched ] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let input = React.useRef();
  let suggestionMenu = React.useRef();
  let searchBar = React.useRef();

  // Fetch saved admin config settings
  let getQuickSearchSettings = () => {
    fetchWithReLogin(globalLoginDisplay, '/apps/cards/config/QuickSearch.json')
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setFetched(true);
        setLimit(json["limit"] || DEFAULT_MAX_RESULTS);
        setAllowedResourceTypes(json["allowedResourceTypes"]);
        setShowTotalRows(json["showTotalRows"]  == 'true');
      });
  }

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
    setMoreResults(0);
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
    let new_url = queryConstructor(query, requestID, showTotalRows, allowedResourceTypes, limit);
    // In the closure generated, postprocessResults will look for req_id, instead of requestID+1
    setRequestID(requestID+1);
    fetchWithReLogin(globalLoginDisplay, new_url)
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
      setResults(json["rows"].map( (item) => { item.entityIdentifier = getEntityIdentifier(item); return item; }) );
      if (json.totalrows > json.returnedrows) {
        let more = json.totalrows - json.returnedrows;
        setMoreResults(more);
      }
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

  // Display a quick search result
  // If it's a resource, show avatar, category, and title
  // Otherwise, if it's a generic entry, simply display the name
  function QuickSearchResult(props) {
    const { resultData, disableLink } = props;
    let ResultConstructor = resultConstructor;
    return resultData["jcr:primaryType"] && (
      <ResultConstructor resultData={resultData} disableLink={disableLink} classes={classes}/>
    ) || (
       <ListItemText
          primary={resultData.name || ''}
          className={classes.dropdownItem}
        />
    )
  }

  if (!fetched) {
    getQuickSearchSettings();
  }

  return(
    <React.Fragment>
      <Input
        type="text"
        placeholder="Search"
        value={search}
        ref={searchBar}
        onChange={(event) => {
          onChange && onChange(event);
          changeSearch(event.target.value);
        }}
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
              size="large"
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
        {...rest}
        />
      {/* Suggestions list using Popper */}
      <Popper
        open={popperOpen}
        anchorEl={input.current}
        className={popperOpen ? classes.aboveBackground : ""}
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
                  onPopperClose && onPopperClose();
                  setPopperOpen(false);
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
                        disableDropdownItemLink && setSearch(result.entityIdentifier);
                        onSelect(event, result, props);
                        onSelectFinish && onSelectFinish();
                        setPopperOpen(false);
                        }}
                      >
                      <QuickSearchResult resultData={result} disableLink={disableDropdownItemLink}/>
                    </MenuItem>
                  ))}
                  { !results[0]?.disabled && showAllResultsLink &&
                  <Link to={"/content.html/QuickSearchResults?query=" + encodeURIComponent(search)
                              + allowedResourceTypes.map(i => `&allowedResourceTypes=${encodeURIComponent(i)}`).join('')}
                          className={classes.root} underline="hover">
                    <MenuItem
                      className={classes.dropdownItem}
                      onClick={() => setPopperOpen(false)}
                      key="more"
                    >
                      { showTotalRows && moreResults > 0 && `${moreResults} more results` || "See all results" }
                    </MenuItem>
                  </Link> }
                </MenuList>
              </ClickAwayListener>
            </Paper>
          </Grow>
        )}
      </Popper>
    </React.Fragment>
  );
}

let defaultQueryConstructor = (query, requestID, showTotalRows, allowedResourceTypes, limit) => {
  let new_url = new URL(DEFAULT_QUERY_URL, window.location.origin);
  new_url.searchParams.set("quick", encodeURIComponent(query));
  new_url.searchParams.set("doNotEscapeQuery", "true");
  new_url.searchParams.set("limit", limit || DEFAULT_MAX_RESULTS);
  new_url.searchParams.set("req", requestID);
  new_url.searchParams.set("showTotalRows", showTotalRows);
  allowedResourceTypes.forEach(i => new_url.searchParams.append("allowedResourceTypes", i));
  return new_url;
}

let defaultResultConstructor = (props) => (
  <React.Fragment>
    <ListItemAvatar><Avatar className={classes.searchResultAvatar}><DescriptionIcon /></Avatar></ListItemAvatar>
    <ListItemText
      primary={(props.resultData["jcr:uuid"])}
      className={classes.dropdownItem}
    />
  </React.Fragment>
);

let defaultRedirect = (event, row, props) => {

  // Redirect using React-router
  const anchor = row[CARDS_QUERY_MATCH_KEY][CARDS_QUERY_MATCH_PATH_KEY];
  const path = (row["jcr:primaryType"] == "cards:Questionnaire") ? "/content.html/admin" : "/content.html";
  if (row["@path"]) {
    props.history.push({
      pathname: path + row["@path"],
      hash: anchor
    });
  }
}

SearchBar.propTypes = {
  invertColors: PropTypes.bool,
  defaultValue: PropTypes.string,
  onChange: PropTypes.func,
  onPopperClose: PropTypes.func,
  onSelect: PropTypes.func,
  queryConstructor: PropTypes.func,
  resultConstructor: PropTypes.func
}

SearchBar.defaultProps = {
  defaultValue: "",
  queryConstructor: defaultQueryConstructor,
  resultConstructor: defaultResultConstructor,
  onSelect: defaultRedirect
}

export default withStyles(HeaderStyle)(withRouter(SearchBar));
