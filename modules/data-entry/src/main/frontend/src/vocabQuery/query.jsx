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
import React, { useRef, useState } from "react";
import PropTypes from "prop-types";

import { withStyles, FormControl } from "@material-ui/core";
import { Avatar, Button, Card, CardActions, CardContent, CardHeader, ClickAwayListener, Grow, IconButton, Input, InputAdornment, InputLabel } from "@material-ui/core"
import { LinearProgress, Link, MenuItem, MenuList, Paper, Popper, Snackbar, SnackbarContent, Tooltip, Typography } from "@material-ui/core";
import CloseIcon from '@material-ui/icons/Close';

import Search from "@material-ui/icons/Search";
import Info from "@material-ui/icons/Info";

import VocabularyBrowser from "./browse.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";
import QueryStyle from "./queryStyle.jsx";
import InfoBox from "./infoBox.jsx";

const NO_RESULTS_TEXT = "No results";
const MAX_RESULTS = 10;

// Component that renders a search bar for vocabulary terms.
//
// Required arguments:
//  clearOnClick: Whether selecting an option will clear the search bar
//  onClick: Callback when the user clicks on this element
//  onInputFocus: Callback when the input is focused on
//  vocabularies: Array of Strings of vocabularies to use (e.g. ["hpo"])
//
// Optional arguments:
//  disabled: Boolean representing whether or not this element is disabled
//  label: Default text to display in search bar when nothing has been entered (default: 'Search')
//  vocabularyFilter: Array of required ancestor elements, of which any term must be a descendent of
//  overrideText: When not undefined, this will overwrite the contents of the search bar
//  defaultValue: Default chosen term ID, which will be converted to the real ID when the vocabulary loads
//  noMargin: Removes the margin from the search wrapper
function VocabularyQuery(props) {
  const [editingFilters, setEditingFilters] = useState([]);

  const { classes, defaultValue, disabled, inputRef, noMargin, isNested, onChange, onInputFocus, placeholder, label, value } = props;

  const [suggestions, setSuggestions] = useState([]);
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);
  const [suggestionsVisible, setSuggestionsVisible] = useState(false);
  const [termInfoVisible, setTermInfoVisible] = useState(false);
  const [lookupTimer, setLookupTimer] = useState(null);
  const [browserOpened, setBrowserOpened] = useState(false);
  const [browseID, setBrowseID] = useState("");
  const [browsePath, setBrowsePath] = useState("");
  const [inputValue, setInputValue] = useState(defaultValue);
  const [snackbarVisible, setSnackbarVisible] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  // Strings used by the info box
  const [infoID, setInfoID] = useState("");
  const [infoName, setInfoName] = useState("");
  const [infoPath, setInfoPath] = useState("");
  const [infoDefinition, setInfoDefinition] = useState("");
  const [infoAlsoKnownAs, setInfoAlsoKnownAs] = useState([]);
  const [infoTypeOf, setInfoTypeOf] = useState([]);
  const [infoAboveBackground, setInfoAboveBackground] = useState(false);
  const [infoAnchor, setInfoAnchor] = useState(null);

  // Information about the vocabulary
  const [infoVocabAcronym, setInfoVocabAcronym] = useState("");
  const [infoVocabURL, setInfoVocabURL] = useState("");
  const [infoVocabDescription, setInfoVocabDescription] = useState("");
  const [infoVocabObtained, setInfoVocabObtained] = useState("");
  const [infoVocabTobeObtained, setInfoVocabTobeObtained] = useState("");
  const [buttonRefs, setButtonRefs] = useState({});
  const [noResults, setNoResults] = useState(false);

  let infoRef = useRef();
  let menuPopperRef = useRef();
  let anchorEl = useRef();
  let menuRef = useRef();

  const inputEl = (
    <Input
      disabled={disabled}
      variant='outlined'
      inputProps={{
        "aria-label": "Search"
      }}
      onChange={(event) => {
        delayLookup(event);
        setInputValue(event.target.value)
        onChange && onChange(event);
      }}
      inputRef={anchorEl}
      onKeyDown={(event) => {
        if (event.key == 'Enter') {
          queryInput(anchorEl.current.value);
          event.preventDefault();
        } else if (event.key == 'ArrowDown') {
          // Move the focus to the suggestions list
          if (menuRef.children.length > 0) {
            menuRef.children[0].focus();
          }
          event.preventDefault();
        }
      }}
      onFocus={(status) => {
        if (onInputFocus !== undefined) {
          onInputFocus(status);
        }
        delayLookup(status);
        anchorEl.current.select();
      }}
      disabled={disabled}
      className={noMargin ? "" : classes.searchInput}
      multiline={true}
      endAdornment={(
        <InputAdornment position="end" onClick={()=>{anchorEl.current.select();}} className = {classes.searchButton}>
          <Search />
        </InputAdornment>
      )}
      placeholder={placeholder}
      value={value || inputValue}
  />);

  // Lookup the search term after a short interval
  // This will reset the interval if called before the interval hangs up
  let delayLookup = (status) => {
    if (lookupTimer !== null) {
      clearTimeout(lookupTimer);
    }

    setLookupTimer(setTimeout(queryInput, 500, status.target.value));
    setSuggestionsVisible(true);
    setSuggestions([]);
  }

  let makeMultiRequest = (queue, input, status, prevData) => {
    //Get an vocabulary to search through
    var selectedVocab = queue.pop();
    if (selectedVocab === undefined) {
      showSuggestions(status, {rows: prevData.slice(0, MAX_RESULTS)});
      return;
    }
    var url = new URL(`./${selectedVocab}.search.json`, REST_URL);
    url.searchParams.set("suggest", input.replace(/[^\w\s]/g, ' '));

    //Are there any filters that should be associated with this request?
    if (props?.questionDefinition?.vocabularyFilters?.[selectedVocab]) {
      var filter = props.questionDefinition.vocabularyFilters[selectedVocab].map((category) => {
        return (`term_category:${category}`);
      }).join(" OR ");
      url.searchParams.set("customFilter", `(${filter})`);
    }

    MakeRequest(url, (status, data) => {
      makeMultiRequest(queue, input, status, prevData.concat(!status && data && data['rows'] ? data['rows'] : []));
    });
  }

  // Grab suggestions for the given input
  let queryInput = (input) => {
    // Empty input? Do not query
    if (input === "") {
      setSuggestionsLoading(false);
      setTermInfoVisible(false);
      setLookupTimer(null);
      return;
    }

    // Grab suggestions
    //...Make a queue of vocabularies to search through
    var vocabQueue = props.questionDefinition.sourceVocabularies.slice();
    makeMultiRequest(vocabQueue, input, null, []);

    // Hide the infobox and stop the timer
    setSuggestionsLoading(true);
    setTermInfoVisible(false);
    setLookupTimer(null);
  }

  // Callback for queryInput to populate the suggestions bar
  let showSuggestions = (status, data) => {
    if (!status) {
        // Populate suggestions
        var suggestions = [];

        if (data["rows"].length > 0) {
          data["rows"].forEach((element) => {
            var name = element["label"] || element["name"] || element["identifier"];
            suggestions.push(
              <MenuItem
                className={classes.dropdownItem}
                key={element["@path"]}
                onClick={(e) => {
                  if (e.target.localName === "li") {
                    props.onClick(element["@path"], name);
                    setInputValue(props.clearOnClick ? "" : name);
                    closeDialog();
                  }}
                }
              >
                {name}
                <IconButton
                  size="small"
                  buttonRef={node => {
                    registerInfoButton(element["identifier"], node);
                  }}
                  color="primary"
                  aria-owns={termInfoVisible ? "menu-list-grow" : null}
                  aria-haspopup={true}
                  onClick={(e) => getInfo(element["@path"])}
                  className={classes.infoButton}
                >
                  <Info color="primary" />
                </IconButton>
              </MenuItem>
              );
          });
        } else {
          suggestions.push(
            <MenuItem
              className={classes.dropdownItem}
              key={NO_RESULTS_TEXT}
              onClick={props.onClick}
              disabled={true}
            >
              {NO_RESULTS_TEXT}
            </MenuItem>
          )
        }

        setSuggestions(suggestions);
        setSuggestionsVisible(true);
        setSuggestionsLoading(false);
    } else {
      logError("Failed to search vocabulary term");
    }
  }

  // Event handler for clicking away from the autocomplete while it is open
  let closeAutocomplete = event => {
    if ((menuPopperRef && menuPopperRef.current.contains(event.target))
      || (infoRef && infoRef.current && infoRef.current.contains(event.target))
      || browserOpened) {
      return;
    }

    setSuggestionsVisible(false);
    setTermInfoVisible(false);
  };

  // Register a button reference that the info box can use to align itself to
  let registerInfoButton = (id, node) => {
    // List items getting deleted will overwrite new browser button refs, so
    // we must ignore deregistration events
    if (node) {
      buttonRefs[id] = node;
    }
  }

  // Grab information about the given ID and populate the info box
  let getInfo = (path) => {
    // If we don't yet know anything about our vocabulary, fill it in
    var vocabPath = `${path.split("/").slice(0, -1).join("/")}.json`;
    if (infoVocabObtained != vocabPath) {
      var url = new URL(vocabPath, window.location.origin);
      setInfoVocabTobeObtained(vocabPath);
      MakeRequest(url, parseVocabInfo);
    }

    var url = new URL(path + ".info.json", window.location.origin);
    MakeRequest(url, showInfo);
  }

  let parseVocabInfo = (status, data) => {
    if (status === null) {
      var vocabPath = infoVocabTobeObtained;
      setInfoVocabAcronym(data["identifier"] || vocabPath.split("/")[2]?.split('.')[0] || "");
      setInfoVocabURL(data["website"] || "");
      setInfoVocabDescription(data["description"]);
      setInfoVocabObtained(vocabPath);
    } else {
      logError("Failed to search vocabulary details");
    }
  }

  // callback for getInfo to populate info box
  let showInfo = (status, data) => {
    if (status === null) {
      var typeOf = [];
      if ("parents" in data) {
        typeOf = data["parents"].map(element =>
          element["label"] || element["name"] || element["identifier"] || element["id"]
        ).filter(i => i);
      }

      setInfoID(data["identifier"]);
      setInfoPath(data["@path"]);
      setInfoName(data["label"]);
      setInfoDefinition(data["def"] || data["description"] || data["definition"]);
      setInfoAlsoKnownAs(data["synonyms"] || data["has_exact_synonym"] || []);
      setInfoTypeOf(typeOf);
      setInfoAnchor(buttonRefs[data["identifier"]]);
      setTermInfoVisible(true);
      setInfoAboveBackground(browserOpened);
    } else {
      logError("Failed to search vocabulary term");
    }
  }

  let clickAwayInfo = (event) => {
    if ((menuPopperRef && menuPopperRef.current.contains(event.target))
      || (infoRef && infoRef.current && infoRef.current.contains(event.target))) {
      return;
    }

    closeInfo();
  }

  // Event handler for clicking away from the info window while it is open
  let closeInfo = (event) => {
    setTermInfoVisible(false);
    setInfoAboveBackground(false);
  };

  let openDialog = () => {
    setBrowserOpened(true);
    setBrowseID(infoID);
    setBrowsePath(infoPath);
  }

  let closeDialog = () => {
    if (props.clearOnClick) {
      anchorEl.current.value = "";
    }
    if (props.focusAfterSelecting) {
      anchorEl.current.select();
    }
    setBrowserOpened(false);
    setSuggestionsVisible(false);
    setTermInfoVisible(false);
    setInfoAboveBackground(false);
  }

  let changeBrowseTerm = (id, path) => {
    setBrowseID(id);
    setBrowsePath(path);
  }

  let logError = (message) => {
    setSnackbarVisible(true);
    setSnackbarMessage(message);
    setSuggestionsLoading(false);
  }

  if (disabled) {
    // Alter our text to either the override ("Please select at most X options")
    // or empty it
    anchorEl.currentl.value = disabled ? props.overrideText : "";
  }

  return (
      <div>
        {props.children}

        <div className={noMargin ? "" : classes.searchWrapper}>
          {noMargin ?
          inputEl
          :
          <FormControl className={isNested ? classes.nestedSearchInput : classes.search}>
            <InputLabel
              classes={{
                root: classes.searchLabel,
                shrink: classes.searchShrink,
              }}
            >
              { /* Cover up a bug that causes the label to overlap the defaultValue:
                   if it has a displayed value and isn't focused, don't show the label
                 */ }
              { (document.activeElement === anchorEl.current || (!defaultValue && !(anchorEl.current?.value))) ? label : ''}
            </InputLabel>
            {inputEl}
          </FormControl>}
          <LinearProgress className={classes.progressIndicator + " " + (suggestionsLoading ? "" : classes.inactiveProgress)}/>
        </div>
        {/* Suggestions list using Popper */}
        <Popper
          open={suggestionsVisible}
          anchorEl={anchorEl.current}
          transition
          className={
            classNames({ [classes.popperClose]: !open })
            + " " + classes.popperNav
            + " " + classes.popperListOnTop
          }
          placement = "bottom-start"
          keepMounted
          modifiers={{
            flip: {
              enabled: true
            },
            preventOverflow: {
              enabled: true,
              boundariesElement: 'window',
              escapeWithReference: true,
            },
            hide: {
              enabled: true
            }
          }}
          ref={menuPopperRef}
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
                <ClickAwayListener onClickAway={closeAutocomplete}>
                  <MenuList role="menu" ref={menuRef}>
                    {suggestions}
                  </MenuList>
                </ClickAwayListener>
              </Paper>
            </Grow>
          )}
        </Popper>
        {/* Info box using Popper */}
        <InfoBox
          termInfoVisible={termInfoVisible}
          anchorEl={infoAnchor}
          infoAboveBackground={infoAboveBackground}
          infoRef={infoRef}
          menuPopperRef={menuPopperRef}
          infoVocabURL={infoVocabURL}
          infoVocabDescription={infoVocabDescription}
          infoVocabAcronym={infoVocabAcronym}
          closeInfo={closeInfo}
          infoName={infoName}
          infoID={infoID}
          infoDefinition={infoDefinition}
          infoAlsoKnownAs={infoAlsoKnownAs}
          infoTypeOf={infoTypeOf}
          openDialog={openDialog}
          browserOpened={browserOpened}
        />
        { /* Browse dialog box */}
        <VocabularyBrowser
          open={browserOpened}
          id={browseID}
          path={browsePath}
          changeTerm={changeBrowseTerm}
          onClose={closeDialog}
          onError={logError}
          registerInfo={registerInfoButton}
          getInfo={getInfo}
          />
        { /* Error snackbar */}
        <Snackbar
          open={snackbarVisible}
          onClose={() => {setSnackbarVisible(false);}}
          autoHideDuration={6000}
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'center',
          }}
          variant="error"
          >
            <SnackbarContent
              className={classes.errorSnack}
              role="alertdialog"
              message={snackbarMessage}
            />
          </Snackbar>
      </div>
    );
}

VocabularyQuery.propTypes = {
    classes: PropTypes.object.isRequired,
    overrideText: PropTypes.string,
    clearOnClick: PropTypes.bool,
    onInputFocus: PropTypes.func,
    defaultValue: PropTypes.string,
    noMargin: PropTypes.bool,
    focusAfterSelecting: PropTypes.bool
};

VocabularyQuery.defaultProps = {
  label: 'Search',
  overrideText: '',
  clearOnClick: true,
  focusAfterSelecting: true
};

export default withStyles(QueryStyle)(VocabularyQuery);
