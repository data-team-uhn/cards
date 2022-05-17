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

import {
  ClickAwayListener,
  Grow,
  IconButton,
  Input,
  InputAdornment,
  InputLabel,
  FormControl,
  Typography,
} from "@material-ui/core";
import withStyles from '@material-ui/styles/withStyles';
import { Divider, LinearProgress, MenuItem, MenuList, Paper, Popper } from "@material-ui/core";

import Search from "@material-ui/icons/Search";
import Info from "@material-ui/icons/Info";

import VocabularyBrowser from "./VocabularyBrowser.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";
import QueryStyle from "./queryStyle.jsx";
import { LABEL_POS, VALUE_POS } from "../questionnaire/Answer";
import QueryMatchingUtils from "./QueryMatchingUtils";

const NO_RESULTS_TEXT = "No results, use:";
const NONE_OF_ABOVE_TEXT = "None of the above, use:";
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
          .find(value => value['jcr:primaryType'] == 'cards:AnswerOption' && value.value === path)
  }

  const [inputValue, setInputValue] = useState(value);
  const [error, setError] = useState("");

  // Holds dropdown info buttons refs to be used as anchor elements by term infoBoxes
  const [buttonRefs, setButtonRefs] = useState({});

  let menuPopperRef = useRef();
  let anchorEl = useRef();
  let searchButtonRef = useRef();
  let menuRef = useRef();

  let infoboxRef = useRef();
  let browserRef = useRef();

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

  let makeMultiRequest = (queue, input, statuses, prevData) => {
    // Get vocabulary to search through
    var selectedVocab = queue.pop();
    if (selectedVocab === undefined) {
      showSuggestions(statuses, {rows: prevData.slice(0, MAX_RESULTS)});
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
      statuses[selectedVocab] = status;
      makeMultiRequest(queue, input, statuses, prevData.concat(!status && data && data['rows'] ? data['rows'] : []));
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
    makeMultiRequest(vocabQueue, input, {}, []);
  }

  // Callback for queryInput to populate the suggestions bar
  let showSuggestions = (statuses, data) => {
    setSuggestionsLoading(false);

    // Populate suggestions
    var suggestions = [];
    var query = anchorEl.current.value;
    var showUserEntry = true;

    if (data["rows"]?.length > 0) {
      data["rows"].forEach((element) => {
        var name = element["label"] || element["name"] || element["identifier"];
        var synonyms = element["synonym"] || element["has_exact_synonym"] || [];
        var definition = Array.from(element["def"] || element["description"] || element["definition"] || [])[0] || "";
        if (name.toLowerCase() == query.toLowerCase()
            || synonyms.find(s => s.toLowerCase() == query.toLowerCase())) {
          showUserEntry = false;
        }

        // Display an existing synonym or definition if the user's search query doesn't
        // match the term's label but matches that synonym/definition
        // TODO: this logic will have to be revisited once vocabulary indexing is improved to
        // acount for typos
        var matchedFields = [];
        if (!QueryMatchingUtils.matches(query, name)) {
          matchedFields = QueryMatchingUtils.getMatchingSubset(query, synonyms);
          if (!matchedFields.length && QueryMatchingUtils.matches(query, definition)) {
             matchedFields.push(QueryMatchingUtils.getMatchingExcerpt(query, definition));
          }
        }

        suggestions.push(
          <MenuItem
            className={classes.dropdownItem}
            key={element["@path"]}
            onClick={(e) => {
              onClick(element["@path"], name);
              setInputValue(clearOnClick || isDefaultOption(element["@path"]) ? "" : name);
              closeSuggestions();
            }}
          >
          <div>
            {name}
            <IconButton
              size="small"
              buttonRef={node => {
                registerInfoButton(element["identifier"], node);
              }}
              color="primary"
              aria-owns={"menu-list-grow"}
              aria-haspopup={true}
              onClick={(e) => {setTermPath(element["@path"]); e.preventDefault(); e.stopPropagation();}}
              className={classes.infoButton}
            >
              <Info color="primary" />
            </IconButton>
            { matchedFields?.map(f =>
              <Typography
                key={f}
                component="div"
                variant="caption"
                color="textSecondary"
              >
                {f}
              </Typography>
            )}
          </div>
          </MenuItem>
          );
      });
    }

    var allRequestsFailed = Object.keys(statuses).filter(vocab => !statuses[vocab]).length == 0;
    var allRequestsSucceded = Object.keys(statuses).filter(vocab => statuses[vocab]).length == 0;

    if (!allRequestsSucceded) {
     suggestions.length > 0 && suggestions.push(<Divider key="error-divider"/>);
      suggestions.push(
        <MenuItem
          className={classes.dropdownMessage}
          key="error-message"
          disabled={true}
        >
          <Typography
            component="p"
            variant="caption"
            color="error"
          >
            { allRequestsFailed && "Answer suggestions cannot be loaded for this question. Please inform your administrator." }
            { !allRequestsFailed && !allRequestsSucceded && "Some answer suggestions for this question could not be loaded. Please inform your administrator." }
          </Typography>
        </MenuItem>
      );
      Object.keys(statuses).filter(vocab => statuses[vocab]).map( vocab => {
          console.error("Cannot load answer suggestions from " + vocab);
        }
      );
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
            {data["rows"].length > 0 ? NONE_OF_ABOVE_TEXT : NO_RESULTS_TEXT}
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
    anchorEl.current.blur();
  }

  return (
    <div>
      {props.children}

      <div className={variant == "labeled" ? classes.searchWrapper : ""}>
        {variant == "labeled" ?
        <FormControl
          variant="standard"
          className={isNested ? classes.nestedSearchInput : classes.search}>
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
