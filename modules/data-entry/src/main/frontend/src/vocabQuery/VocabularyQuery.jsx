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
import React, { useRef, useEffect, useState } from "react";
import PropTypes from "prop-types";

import { withStyles, ClickAwayListener, Grow, IconButton, Input, InputAdornment, InputLabel, FormControl, Typography } from "@material-ui/core"
import { LinearProgress, MenuItem, MenuList, Paper, Popper } from "@material-ui/core";

import Search from "@material-ui/icons/Search";
import Info from "@material-ui/icons/Info";

import VocabularyBrowser from "./VocabularyBrowser.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";
import QueryStyle from "./queryStyle.jsx";
import { LABEL_POS, VALUE_POS } from "../questionnaire/Answer";

const NO_RESULTS_TEXT = "No results";
const MAX_RESULTS = 10;

// Component that renders a search bar for vocabulary terms.
//
// Required arguments:
//  clearOnClick: Whether selecting an option will clear the search bar (default: true)
//  onClick: Callback when the user clicks on this element
//  focusAfterSelecting: focus after selecting (default: true)
//  questionDefinition: Object describing the Vocabulary Question for which this suggested input is displayed
//
// Optional arguments:
//  disabled: Boolean representing whether or not this element is disabled
//  variant: Adds the label to the search wrapper, values: default|labeled, defaulting on default
//  isNested: If true, restyles the element to remove most padding and apply a negative margin for better nesting
//  placeholder: String to display as the input element's placeholder
//  value: String to use as the input element value
//  onChange: Callback in term input change event
//  allowTermSelection: Boolean enabler for term selection from vocabulary tree browser
//  initialSelection: Existing answers
//  onRemoveOption: Function to remove added answer
//
function VocabularyQuery(props) {
  const { clearOnClick, onClick, focusAfterSelecting, disabled, variant, isNested, placeholder,
    value, questionDefinition, onChange, allowTermSelection, initialSelection, onRemoveOption, classes } = props;
  const [suggestions, setSuggestions] = useState([]);
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);
  const [suggestionsVisible, setSuggestionsVisible] = useState(false);
  const [lookupTimer, setLookupTimer] = useState(null);
  const maxAnswers = questionDefinition?.maxAnswers;

  // Holds term path on dropdown info button click
  const [termPath, setTermPath] = useState("");

  // Checks whether path is listed in the default answer options in the question definition
  let isDefaultOption = (path) => {
    return Object.values(props.questionDefinition)
          .find(value => value['jcr:primaryType'] == 'lfs:AnswerOption' && value.value === path)
  }

  const [inputValue, setInputValue] = useState(maxAnswers === 1 && initialSelection?.length > 0 && !isDefaultOption(initialSelection[0][VALUE_POS]) ? initialSelection[0][LABEL_POS] : value);
  const [error, setError] = useState("");

  // Holds dropdown info buttons refs to be used as anchor elements by term infoBoxes
  const [buttonRefs, setButtonRefs] = useState({});

  let menuPopperRef = useRef();
  let anchorEl = useRef();
  let searchButtonRef = useRef();
  let menuRef = useRef();

  let infoboxRef = useRef();
  let browserRef = useRef();

  // Update input field if maxAnswers=1
  useEffect(() => {
    if (maxAnswers === 1) {
      initialSelection.length == 0 || isDefaultOption(initialSelection[0][VALUE_POS]) ? setInputValue("") : setInputValue(initialSelection[0][LABEL_POS]);
    }
  }, [initialSelection])

  const inputEl = (
    <Input
      disabled={disabled}
      error={!!error}
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
          queryInput(anchorEl.current.value);
          event.preventDefault();
        } else if (event.key == 'ArrowDown') {
          // Move the focus to the suggestions list
          if (menuRef?.current?.children?.length > 0) {
            menuRef.current.children[0].focus();
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
        setTermPath("");
        setError("");
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

  // Lookup the search term after a short interval
  // This will reset the interval if called before the interval hangs up
  let delayLookup = (value) => {
    if (lookupTimer !== null) {
      clearTimeout(lookupTimer);
    }

    setLookupTimer(setTimeout(queryInput, 500, value));
    setSuggestionsVisible(true);
    setSuggestions([]);
  }

  let makeMultiRequest = (queue, input, status, prevData) => {
    // Get vocabulary to search through
    var selectedVocab = queue.pop();
    if (selectedVocab === undefined) {
      showSuggestions(status, {rows: prevData.slice(0, MAX_RESULTS)});
      return;
    }
    var url = new URL(`./${selectedVocab}.search.json`, REST_URL);
    url.searchParams.set("suggest", input.replace(/[^\w\s]/g, ' '));

    //Are there any filters that should be associated with this request?
    if (questionDefinition?.vocabularyFilters?.[selectedVocab]) {
      var filter = questionDefinition.vocabularyFilters[selectedVocab].map((category) => {
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
    // Stop the timer
    setLookupTimer(null);

    // Empty/blank input? Do not query
    if (input.trim() === "") {
      return;
    }
    setError("");

    // Grab suggestions
    //...Make a queue of vocabularies to search through
    setSuggestionsLoading(true);
    var vocabQueue = questionDefinition.sourceVocabularies.slice();
    makeMultiRequest(vocabQueue, input, null, []);
  }

  // Callback for queryInput to populate the suggestions bar
  let showSuggestions = (status, data) => {
    setSuggestionsLoading(false);

    if (status && data["rows"]?.length == 0) {
      setError("Cannot load answer suggestions for this question. Please inform your administrator.");
      return;
    }

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
                onClick(element["@path"], name);
                setInputValue(clearOnClick ? "" : name);
                closeSuggestions();
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
              aria-owns={"menu-list-grow"}
              aria-haspopup={true}
              onClick={(e) => setTermPath(element["@path"])}
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
          onClick={onClick}
          disabled={true}
        >
          {NO_RESULTS_TEXT}
        </MenuItem>
      )
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
      && setInputValue("");
    setSuggestionsVisible(false);
    setTermPath("");
    setError("");
  };

  // Register a button reference that the info box can use to align itself to
  let registerInfoButton = (id, node) => {
    // List items getting deleted will overwrite new browser button refs, so
    // we must ignore deregistration events
    if (node) {
      buttonRefs[id] = node;
    }
  }

  // Event handler for clicking away from the info window while it is open
  let closeInfo = (event) => {
    setTermPath("");
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

  let onCloseBrowser = (selectedTerms, removedTerms) => {
    selectedTerms && selectedTerms.map(item => onClick(item[VALUE_POS], item[LABEL_POS]));
    removedTerms && removedTerms.map(item => onRemoveOption(item[VALUE_POS], item[LABEL_POS]));

    // Set input value to selected term label or initial selection label if single answer question
    if (maxAnswers === 1) {
      if (selectedTerms?.length > 0) {
        // Search in default answer options
        !isDefaultOption(selectedTerms[0][VALUE_POS]) && setInputValue(selectedTerms[0][LABEL_POS]);
      }
    }
    if (selectedTerms || removedTerms) {
      setSuggestionsVisible(false);
      setTermPath("");
      maxAnswers != 1 && setInputValue("");
      setError("");
    } else {
      anchorEl.current.focus();
    }
  }

  if (disabled && anchorEl?.current) {
    // Alter our text to either the override ("Please select at most X options")
    // or empty it
    anchorEl.current.value = "";
  }

  return (
      <div>
        {props.children}

        <div className={variant == "labeled" ? classes.searchWrapper : ""}>
          {variant == "labeled" ?
          <FormControl className={isNested ? classes.nestedSearchInput : classes.search}>
            <InputLabel
              classes={{
                root: classes.searchLabel,
                shrink: classes.searchShrink,
              }}
            >
              { /* Cover up a bug that causes the label to overlap the value:
                   if it has a displayed value and isn't focused, don't show the label
                 */ }
              { (document.activeElement === anchorEl.current || (!value && !(anchorEl.current?.value))) ? 'Search' : ''}
            </InputLabel>
            {inputEl}
          </FormControl>
          :
          inputEl}
          <LinearProgress className={classes.progressIndicator + " " + (suggestionsLoading ? "" : classes.inactiveProgress)}/>
          { error && <Typography component="div" color="error" variant="caption">{error}</Typography> }
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
        <VocabularyBrowser
          infoPath={termPath}
          onCloseInfo={closeInfo}
          infoButtonRefs={buttonRefs}
          browserRef={browserRef}
          infoboxRef={infoboxRef}
          questionDefinition={questionDefinition}
          allowTermSelection={allowTermSelection}
          initialSelection={initialSelection}
          onCloseBrowser={onCloseBrowser}
        />
      </div>
    );
}

VocabularyQuery.propTypes = {
    classes: PropTypes.object.isRequired,
    clearOnClick: PropTypes.bool.isRequired,
    onClick: PropTypes.func.isRequired,
    focusAfterSelecting: PropTypes.bool.isRequired,
    disabled: PropTypes.bool,
    variant: PropTypes.string,
    isNested: PropTypes.bool,
    placeholder: PropTypes.string,
    value: PropTypes.string,
    questionDefinition: PropTypes.object.isRequired,
    onChange: PropTypes.func,
    allowTermSelection: PropTypes.bool,
    initialSelection: PropTypes.array,
    onRemoveOption: PropTypes.func
};

VocabularyQuery.defaultProps = {
  clearOnClick: true,
  focusAfterSelecting: true,
  variant: 'default'
};

export default withStyles(QueryStyle)(VocabularyQuery);
