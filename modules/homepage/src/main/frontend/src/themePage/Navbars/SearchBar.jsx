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
import classNames from "classnames";
import PropTypes from "prop-types";
import React, { useState } from "react";
import { Redirect, withRouter } from "react-router-dom";

import { ClickAwayListener, Grow, IconButton, Input, InputAdornment, ListItemText, MenuItem, MenuList, Paper, Popper, withStyles } from "@material-ui/core";
import Search from "@material-ui/icons/Search";
import HeaderStyle from "./headerStyle.jsx";

const QUERY_URL = "/query";
const MAX_RESULTS = 5;

function SearchBar(props) {
  const { classes, closeSidebar, invertColors } = props;
  const [ search, setSearch ] = useState("");
  const [ results, setResults ] = useState([]);
  const [ popperOpen, setPopperOpen ] = useState(false);
  const [ timer, setTimer ] = useState();
  const [ error, setError ] = useState();

  let input = React.useRef();

  let changeSearch = (query) => {
    // Reset the timer if it exists
    if (timer !== null) {
      clearTimeout(timer);
    }

    setSearch(query);
    setTimer(setTimeout(runQuery, 500, query));
  }

  let runQuery = (query) => {
    let new_url = new URL(QUERY_URL, window.location.origin);
    new_url.searchParams.set("quick", encodeURIComponent(query));
    fetch(new_url)
      .then(response => response.ok ? response.json() : Promise.reject(response))
      .then(displayResults)
      .catch(handleError)
  }

  let displayResults = (json) => {
    // Parse out the top 5 and display in popper?
    if (json.length >= MAX_RESULTS) {
      setResults(json.slice(0, MAX_RESULTS));
    } else {
      setResults(json);
    }
    setPopperOpen(true);
  }

  let handleError = (response) => {
    setError(response);
  }

  let getElementName = (element) => {
    // Attempt a few different methods of getting the name
    return element["name"] || element["title"] || element["jcr:uuid"];
  }

  return(
    <React.Fragment>
      <Input
        type="text"
        placeholder="Search"
        value={search}
        onChange={(event) => changeSearch(event.target.value)}
        endAdornment={
          <InputAdornment position="end">
            <IconButton className={invertColors ? classes.invertedColors : ""}>
              <Search />
            </IconButton>
          </InputAdornment>
        }
        className={classes.search + " " + (invertColors ? classes.invertedColors : "")}
        inputRef={input}
        />
      {/* Suggestions list using Popper */}
      <Popper
        open={popperOpen}
        anchorEl={input.current}
        transition
        className={
          classNames({ [classes.popperClose]: !open })
          + " " + classes.popperNav
          + " " + classes.aboveBackground
        }
        placement = "bottom-start"
        keepMounted
        container={document.querySelector('#main-panel')}
        >
        {({ TransitionProps }) => (
          <Grow
            {...TransitionProps}
            id="menu-list-grow"
            style={{
              transformOrigin: "left top"
            }}
          >
            <Paper>
              <ClickAwayListener onClickAway={() => {setPopperOpen(false)}}>
                <MenuList role="menu" className={classes.suggestions}>
                  {results.map( (element, index) => (
                    <MenuItem
                      className={classes.dropdownItem}
                      key={element["@path"]}
                      onClick={(e) => {
                        // Redirect
                        props.history.push("/content.html" + element["@path"]);
                        closeSidebar && closeSidebar();
                        setPopperOpen(false);
                        }}
                      >
                        <ListItemText
                          primary={getElementName(element)}
                          secondary={element["jcr:primaryType"]}
                          className={classes.dropdownItem}
                          />
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
  invertColors: PropTypes.bool
}

export default withStyles(HeaderStyle)(withRouter(SearchBar));