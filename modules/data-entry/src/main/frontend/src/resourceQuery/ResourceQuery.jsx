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
import React, { useRef, useEffect, useState, useContext } from "react";
import PropTypes from "prop-types";

import { ClickAwayListener, Grow, IconButton, Input, InputAdornment, InputLabel, FormControl, Typography } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import { Divider, LinearProgress, MenuItem, MenuList, Paper, Popper } from "@mui/material";

import Search from "@mui/icons-material/Search";
import Info from "@mui/icons-material/Info";

import QueryStyle from "./queryStyle.jsx";
import { LABEL_POS, VALUE_POS } from "../questionnaire/Answer";
import QueryMatchingUtils from "./QueryMatchingUtils";
import FormattedText from "../components/FormattedText";

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

const NO_RESULTS_TEXT = "No results, use:";
const NONE_OF_ABOVE_TEXT = "None of the above, use:";
export const MAX_RESULTS = 10;

// Component that renders a search bar for resources.
//
// Required arguments:
//  clearOnClick: Whether selecting an option will clear the search bar (default: true)
//  onClick: Callback when the user clicks on this element
//  focusAfterSelecting: focus after selecting (default: true)
//  questionDefinition: Object describing the Resource Question for which this suggested input is displayed
//
// Optional arguments:
//  disabled: Boolean representing whether or not this element is disabled
//  variant: Adds the label to the search wrapper, values: default|labeled, defaulting on default
//  isNested: If true, restyles the element to remove most padding and apply a negative margin for better nesting
//  placeholder: String to display as the input element's placeholder
//  value: String to use as the input element value
//  questionDefinition: the metadata describing the resource question. Expected to include:
//    - maxAnswers: if maxAnswers is 1, the suggestion list is considered single select, otherwise multi select)
//    - primaryType: the type of resources queried
//    - labelProperty: what property of the resource is used as the label
//    - propertiesToSearch: when searching for resources based on texted entered by the user, what other properties
//      of the resource node, in addition to labelProperty, should be queried
//  onChange: Callback in term input change event
//  enableSelection: Boolean enabler for selection from a resource browser
//  initialSelection: Existing answers
//  onRemoveOption: Function to remove added answer
//  enableUserEntry: whether the user can choose to select a text that does not match any resources to store as the answer
//  fetchSuggestions: a query function with the signature (inputText, onSuccessCallback, onFailure Callback) that implements
//    a different way of obtaining suggestions for the given inputText.
//    onSuccessCallback accepts the suggestion data as a parameter and displays the suggestions
//    onFailureCallback takes no parameters and displays a generic failure message
//  formatSuggestionData: a function for transforming the raw suggestion data into data that can be displayed by `showSuggestions`
//  infoDisplayer: a component used to display further information about the resource
//
function ResourceQuery(props) {
  const { clearOnClick, onClick, focusAfterSelecting, disabled, variant, isNested, placeholder,
    value, questionDefinition, onChange, enableSelection, initialSelection, onRemoveOption, classes } = props;
  const { maxAnswers, primaryType, labelProperty, propertiesToSearch, enableUserEntry } = questionDefinition;
  const { fetchSuggestions, formatSuggestionData, infoDisplayer } = props;

  const [suggestions, setSuggestions] = useState([]);
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);
  const [suggestionsVisible, setSuggestionsVisible] = useState(false);
  const [lookupTimer, setLookupTimer] = useState(null);

  // Holds resource path on dropdown info button click
  const [resourcePath, setResourcePath] = useState("");

  // Checks whether path is listed in the default answer options in the question definition
  let isDefaultOption = (path) => {
    return Object.values(props.questionDefinition)
          .find(value => value['jcr:primaryType'] == 'cards:AnswerOption' && value.value === path)
  }

  const [inputValue, setInputValue] = useState(value);

  // Holds dropdown info buttons refs to be used as anchor elements by term infoBoxes
  const [buttonRefs, setButtonRefs] = useState({});

  let menuPopperRef = useRef();
  let anchorEl = useRef();
  let searchButtonRef = useRef();
  let menuRef = useRef();

  // If info buttons are enabled, assign refs for the info box and a possible resource browser
  // (that may be used for resources organized in a tree) to control clickaway events
  let infoboxRef = useRef();
  let browserRef = useRef();

  const inputEl = (
    <Input
      disabled={disabled}
      variant='outlined'
      inputProps={{
        "aria-label": "Search"
      }}
      onChange={(event) => {
        delayLookup(event.target.value);
        setInputValue(event.target.value);
        (maxAnswers != 1 || event.target.value == "") && onChange && onChange(event);
      }}
      inputRef={anchorEl}
      onKeyDown={(event) => {
        if (event.key == 'Enter') {
          onChange && onChange(event);
          closeAutocomplete(event);
          event.preventDefault();
        } else if (event.key == 'ArrowDown' || event.key == 'ArrowUp') {
          // Move the focus to the 1st or last item of suggestions list
          if (menuRef?.current?.children?.length > 0) {
            let index = (event.key == 'ArrowDown') ? 0 : menuRef.current.children.length -1;
            menuRef.current.children[index].focus();
          }
          event.preventDefault();
        } else if (event.key == 'Tab' || event.key == "Escape") {
          maxAnswers != 1 && setInputValue("");
          closeAutocomplete(event);
        }
      }}
      onFocus={(status) => {
        anchorEl.current.select();
        setSuggestionsVisible(false);
        setResourcePath("");
      }}
      className={variant == "labeled" ? classes.searchInput : ""}
      multiline={true}
      endAdornment={(
        <InputAdornment position="end" ref={searchButtonRef} onClick={() => {
              queryInput(anchorEl.current.value);
            }
          }
          className = {classes.searchButton}>
          <Search />
        </InputAdornment>
      )}
      placeholder={placeholder}
      value={inputValue}
    />
  );

  const globalContext = useContext(GlobalLoginContext);

  const otherProperties = propertiesToSearch?.trim() ? propertiesToSearch.trim().split(/\s*,\s*/) : [];

  const generateSearchURL = (input) => {
    let url = `/query?query=select distinct r.* from [${primaryType}] as r where`;
    let propNames = [labelProperty || '@name', ...otherProperties];
    propNames.forEach((propName, index) => {
      if (index != 0) {
        url += " OR"
      }
      url += ` contains(r.${propName},'*${input.replace(/[^\w\s]/g, ' ')}*')`;
    });
    url += `&limit=${MAX_RESULTS}`;
    return url;
  }

  // Lookup the search input after a short interval
  // This will reset the interval if called before the interval hangs up
  let delayLookup = (value) => {
    if (lookupTimer !== null) {
      clearTimeout(lookupTimer);
    }

    setLookupTimer(setTimeout(queryInput, 500, value));
    setSuggestionsVisible(true);
    setSuggestions([]);
  }

  // Grab suggestions for the given input
  let queryInput = (input) => {
    // Stop the timer
    setLookupTimer(null);

    // Empty/blank input? Do not query
    if (input.trim() === "") {
      return;
    }

    // Grab suggestions
    setSuggestionsLoading(true);
    // Query for resources matching the input
    getSuggestions(
      input,
      showSuggestions,
      () => showSuggestions({rows: [{
        error: true,
        message: "Answer suggestions cannot be loaded for this question."
      }]})
    );
  }

  let getSuggestions = fetchSuggestions || ((input, onSuccess, onFailure) => {
    fetchWithReLogin(globalContext, generateSearchURL(input))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(onSuccess)
      .catch(onFailure);
  });

  let formatSuggestion = (rawData, query) => {
    if (rawData.error) return rawData;

    if (formatSuggestionData) {
      return formatSuggestionData(rawData, query);
    }

    let suggestion = {
      isPerfectMatch: false,
      "@path" : rawData["@path"],
      label : rawData[labelProperty || '@name'],
      matchedFields : []
    };
    if (!QueryMatchingUtils.matches(query, suggestion.label)) {
      Object.entries(rawData)
        .filter(([propName, _]) => otherProperties.includes(propName))
        .forEach(([_, value]) => {
          if (Array.isArray(value)) {
            suggestion.matchedFields.push(QueryMatchingUtils.getMatchingSubset(query, value));
          } else {
            suggestion.matchedFields.push(QueryMatchingUtils.getMatchingExcerpt(query, value));
          }
        });
    }
    suggestion.isPerfectMatch = (suggestion.label.toLowerCase() == query.toLowerCase());
    return suggestion;
  }

  // Callback for queryInput to populate the suggestions bar
  let showSuggestions = (data) => {
    setSuggestionsLoading(false);

    // Populate suggestions
    var suggestions = [];
    var query = anchorEl.current.value;
    var showUserEntry = enableUserEntry;

    if (data["rows"]?.length > 0) {
      data["rows"].forEach((element) => {
        let suggestion = formatSuggestion(element, query);
        showUserEntry = showUserEntry && !suggestion.isPerfectMatch;

        suggestions.push(
          suggestion.error ?
          <MenuItem
            className={classes.dropdownMessage}
            key={suggestion.message}
            disabled={true}
          >
            <Typography
              component="p"
              variant="caption"
              color="error"
            >
            {suggestion.message}
            </Typography>
          </MenuItem>
          :
          <MenuItem
            className={classes.dropdownItem}
            key={suggestion["@path"]}
            onClick={(e) => {
              onClick(suggestion["@path"], suggestion.label);
              setInputValue(clearOnClick || isDefaultOption(suggestion["@path"]) ? "" : suggestion.label);
              closeSuggestions();
            }}
          >
            <div>
            { suggestion.label }
            { infoDisplayer &&
              <IconButton
                size="small"
                ref={node => {
                  registerInfoButton(suggestion["@path"], node);
                }}
                color="primary"
                onClick={(e) => {setResourcePath(element["@path"]); e.preventDefault(); e.stopPropagation();}}
                className={classes.infoButton}
            >
              <Info color="primary" />
            </IconButton>
            }
            { suggestion.matchedFields?.map(f =>
              <FormattedText
                key={f}
                component="div"
                variant="caption"
                color="textSecondary"
              >
                {f}
              </FormattedText>
            )}
            </div>
          </MenuItem>
        );
      });
    }

    if (showUserEntry) {
      suggestions.length > 0 && suggestions.push(<Divider key="divider"/>);
      suggestions.push(
        <MenuItem
          className={classes.dropdownItem}
          key={NO_RESULTS_TEXT}
          disabled={true}
        >
          <Typography
            component="p"
            className={classes.noResults}
            variant="caption"
          >
            {data["rows"].filter(r => !r.error).length > 0 ? NONE_OF_ABOVE_TEXT : NO_RESULTS_TEXT}
          </Typography>
        </MenuItem>
      );

      suggestions.push(
        <MenuItem
          className={classes.dropdownItem}
          key={anchorEl.current.value}
          onClick={(e) => {
              if (e.target.localName === "li") {
                onClick(anchorEl.current.value, anchorEl.current.value);
                clearOnClick && setInputValue("");
                closeSuggestions();
              }}
            }
        >
          {anchorEl.current.value}
        </MenuItem>
      );
    }

    setSuggestions(suggestions);
    setSuggestionsVisible(true);
  }

  // Event handler for clicking away from the autocomplete while it is open
  let closeAutocomplete = event => {
    if ( browserRef?.current
      || menuPopperRef?.current?.contains(event.target)
      || infoboxRef?.current?.contains(event.target)) {
      return;
    }

    !anchorEl?.current?.contains(event.target)
      && !searchButtonRef?.current?.contains(event.target)
      && maxAnswers !== 1
      && clearOnClick
      && setInputValue("");
    setSuggestionsVisible(false);
    setResourcePath("");
  };

  let InfoDisplayer = infoDisplayer;

    // Register a button reference that the info box can use to align itself to
  let registerInfoButton = (id, node) => {
    // List items getting deleted will overwrite new browser button refs, so
    // we must ignore deregistration events
    if (node) {
      setButtonRefs(oldRefs => {
        let newRefs = Object.assign({}, oldRefs);
        newRefs[id] = node;
        return newRefs;
      });
    }
  }

  let closeSuggestions = () => {
    if (clearOnClick && anchorEl?.current) {
      anchorEl.current.value = "";
    }
    if (focusAfterSelecting) {
      anchorEl?.current?.select();
    }
    setSuggestionsVisible(false);
  }

  let updateSelection = (selectedEntries, removedEntries) => {
    selectedEntries && selectedEntries.map(item => onClick(item[VALUE_POS], item[LABEL_POS]));
    removedEntries && removedEntries.map(item => onRemoveOption(item[VALUE_POS], item[LABEL_POS]));

    // Set input value to selected term label or initial selection label if single answer question
    if (maxAnswers === 1) {
      if (selectedEntries?.length > 0) {
        // Search in default answer options
        !isDefaultOption(selectedEntries[0][VALUE_POS]) && setInputValue(selectedEntries[0][LABEL_POS]);
      }
    }
    if (selectedEntries || removedEntries) {
      setSuggestionsVisible(false);
      setResourcePath("");
      maxAnswers != 1 && setInputValue("");
    } else {
      anchorEl.current.focus();
    }
  }

  if (disabled && anchorEl?.current) {
    // Alter our text to either the override ("Please select at most X options")
    // or empty it
    anchorEl.current.value = "";
    anchorEl.current.blur();
  }

  return (
      <div>
        {props.children}

        <div className={variant == "labeled" ? classes.searchWrapper : ""}>
          {variant == "labeled" ?
          <FormControl variant="standard" className={isNested ? classes.nestedSearchInput : classes.search}>
            <InputLabel
              classes={{
                root: classes.searchLabel,
                shrink: classes.searchShrink,
              }}
            >
              Search
            </InputLabel>
            {inputEl}
          </FormControl>
          :
          inputEl}
          <LinearProgress className={classes.progressIndicator + " " + (suggestionsLoading ? "" : classes.inactiveProgress)}/>
        </div>
        {/* Suggestions list using Popper */}
        <Popper
          open={suggestionsVisible}
          anchorEl={anchorEl.current}
          transition
          className={classNames(
            {[classes.popperClose]: !open},
            classes.popperNav,
            classes.popperListOnTop
          )}
          placement = "bottom-start"
          keepMounted
          modifiers={[
            {
              name: 'flip',
              enabled: true
            },
            {
              name: 'preventOverflow',
              enabled: true,
              options: {
                altAxis: true,
                altBoundary: true,
                tether: true,
                rootBoundary: 'window',
              }
            },
            {
              name: 'hide',
              enabled: true
            }
          ]}
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
        { infoDisplayer &&
            <InfoDisplayer
              infoPath={resourcePath}
              infoButtonRefs={buttonRefs}
              infoboxRef={infoboxRef}
              enableSelection={enableSelection}
              initialSelection={initialSelection}
              browserRef={browserRef}
              onCloseBrowser={updateSelection}
              onCloseInfo={() => setResourcePath("")}
              questionDefinition={questionDefinition}
           />
        }
      </div>
    );
}

ResourceQuery.propTypes = {
    classes: PropTypes.object.isRequired,
    clearOnClick: PropTypes.bool.isRequired,
    onClick: PropTypes.func.isRequired,
    focusAfterSelecting: PropTypes.bool.isRequired,
    disabled: PropTypes.bool,
    variant: PropTypes.string,
    isNested: PropTypes.bool,
    placeholder: PropTypes.string,
    value: PropTypes.string,
    questionDefinition: PropTypes.shape({
      text: PropTypes.string,
      maxAnswers: PropTypes.number,
      primaryType: PropTypes.string,
      labelProperty: PropTypes.string,
      propertiesToSearch: PropTypes.string,
      enableUserEntry: PropTypes.bool,
    }).isRequired,
    onChange: PropTypes.func,
    enableSelection: PropTypes.bool,
    initialSelection: PropTypes.array,
    onRemoveOption: PropTypes.func,
    infoDisplayer: PropTypes.object,
    fetchSuggestions: PropTypes.func,
    formatSuggestionData: PropTypes.func,
};

ResourceQuery.defaultProps = {
  clearOnClick: true,
  focusAfterSelecting: true,
  variant: 'default'
};

export default withStyles(QueryStyle)(ResourceQuery);
