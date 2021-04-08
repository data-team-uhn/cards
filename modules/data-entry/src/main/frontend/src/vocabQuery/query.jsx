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
import React from "react";
import PropTypes from "prop-types";
// @material-ui/core
import { withStyles, FormControl } from "@material-ui/core";
import { Avatar, Button, Card, CardActions, CardContent, CardHeader, ClickAwayListener, Grow, IconButton, Input, InputAdornment, InputLabel } from "@material-ui/core"
import { LinearProgress, Link, MenuItem, MenuList, Paper, Popper, Snackbar, SnackbarContent, Tooltip, Typography } from "@material-ui/core";
import CloseIcon from '@material-ui/icons/Close';
// @material-ui/icons
import Search from "@material-ui/icons/Search";
import Info from "@material-ui/icons/Info";

import VocabularyBrowser from "./browse.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";
import QueryStyle from "./queryStyle.jsx";

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
//  searchDefault: Default text to display in search bar when nothing has been entered (default: 'Search')
//  vocabularyFilter: Array of required ancestor elements, of which any term must be a descendent of
//  overrideText: When not undefined, this will overwrite the contents of the search bar
//  defaultValue: Default chosen term ID, which will be converted to the real ID when the vocabulary loads
//  noMargin: Removes the margin from the search wrapper
class VocabularyQuery extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      suggestions: [],
      suggestionsLoading: false,
      suggestionsVisible: false,
      termInfoVisible: false,
      lookupTimer: null,
      browserOpened: false,
      browseID: "",
      browsePath: "",
      inputValue: props.defaultValue,
      // Strings used by the info box
      infoID: "",
      infoName: "",
      infoDefinition: "",
      infoAlsoKnownAs: [],
      infoTypeOf: [],
      infoAnchor: null,
      infoAboveBackground: false,
      // Information about the vocabulary
      infoVocabAcronym: "",
      infoVocabURL: "",
      infoVocabDescription: "",
      infoVocabObtained: "",
      infoVocabTobeObtained: "",
      buttonRefs: {},
      vocabularies: props.vocabularies,
      noResults: false,
    };
  }

  render() {
    const { classes, defaultValue, disabled, inputRef, noMargin, isNested, onInputFocus, placeholder, searchDefault, value, vocabularies } = this.props;

    const inputEl = (<Input
      disabled={disabled}
      variant='outlined'
      inputProps={{
        "aria-label": "Search"
      }, {
        tabindex: isNested ? -1 : undefined
      }}
      onChange={(event) => {
        this.delayLookup(event);
        this.setState({inputValue: event.target.value})
      }}
      inputRef={(node) => {
        this.anchorEl = node;
        if (inputRef != null) {
          inputRef(node);
        }
      }}
      onKeyDown={(event) => {
        if (event.key == 'Enter') {
          this.queryInput(this.anchorEl.value);
        } else if (event.key == 'ArrowDown') {
          // Move the focus to the suggestions list
          if (this.menuRef.children.length > 0) {
            this.menuRef.children[0].focus();
          }
        }
      }}
      onFocus={(status) => {
        if (onInputFocus !== undefined) {
          onInputFocus(status);
        }
        this.delayLookup(status);
        this.anchorEl.select();
      }}
      disabled={disabled}
      className={noMargin ? "" : classes.searchInput}
      multiline={true}
      endAdornment={(
        <InputAdornment position="end" onClick={()=>{this.anchorEl.select();}}>
          <Search />
        </InputAdornment>
      )}
      defaultValue={defaultValue}
      placeholder={placeholder}
      value={value || this.state.inputValue}
      />);

    return (
      <div>
        {this.props.children}

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
              {searchDefault}
            </InputLabel>
            {inputEl}
          </FormControl>}
          <br />
          <LinearProgress className={classes.progressIndicator + " " + (this.state.suggestionsLoading ? "" : classes.inactiveProgress)}/>
        </div>
        {/* Suggestions list using Popper */}
        <Popper
          open={this.state.suggestionsVisible}
          anchorEl={this.anchorEl}
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
          ref = {(ref) => {this.menuPopperRef = ref}}
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
                <ClickAwayListener onClickAway={this.closeAutocomplete}>
                  <MenuList role="menu" ref={(ref)=> {this.menuRef = ref}}>
                    {this.state.suggestions}
                  </MenuList>
                </ClickAwayListener>
              </Paper>
            </Grow>
          )}
        </Popper>
        {/* Info box using Popper */}
        <Popper
          placement="right"
          open={this.state.termInfoVisible}
          anchorEl={this.state.infoAnchor}
          transition
          className={
            classNames({ [classes.popperClose]: !open })
            + " " + classes.popperNav
            + " " + (this.state.infoAboveBackground ? classes.infoAboveBackdrop : classes.popperInfoOnTop)
          }
          ref={(ref) => {this.infoRef = ref}}
          modifiers={{
            keepTogether: {
              enabled: true
            },
            preventOverflow: {
              boundariesElement: 'window',
              escapeWithReference: false,
              enabled: true
            },
            arrow: {
              enabled: false
            }
          }}
        >
          {({ TransitionProps }) => (
            <Grow
              {...TransitionProps}
              id="info-grow"
              style={{
                transformOrigin: "center left",
              }}
            >
              <Card className={classes.infoCard}>
                <ClickAwayListener onClickAway={this.clickAwayInfo}><div>
                   <CardHeader
                     avatar={
                      <Link color="textSecondary"
                        href={this.state.infoVocabURL || ""}  target="_blank"
                        component={this.state.infoVocabURL ? 'a' : 'span'}
                        underline="none"
                        >
                        <Tooltip title={this.state.infoVocabDescription}>
                          <Avatar aria-label="source" className={classes.vocabularyAvatar}>
                              {this.state.infoVocabAcronym}
                          </Avatar>
                        </Tooltip>
                      </Link>
                    }
                    action={
                      <IconButton aria-label="close" onClick={this.closeInfo}>
                        <CloseIcon />
                      </IconButton>
                    }
                    title={this.state.infoName}
                    subheader={this.state.infoID}
                    titleTypographyProps={{variant: 'h5'}}
                  />
                  <CardContent className={classes.infoPaper}>
                    <div className={classes.infoSection}>
                      <Typography className={classes.infoDefinition}>{this.state.infoDefinition}</Typography>
                    </div>
                      {this.state.infoAlsoKnownAs.length > 0 && (
                        <div className={classes.infoSection}>
                          <Typography variant="h6" className={classes.infoHeader}>Also known as</Typography>
                          {this.state.infoAlsoKnownAs.map((name, index) => {
                            return (<Typography className={classes.infoAlsoKnownAs} key={index}>
                                      {name}
                                    </Typography>
                            );
                          })}
                        </div>
                      )}
                      {this.state.infoTypeOf.length > 0 && (
                        <div className={classes.infoSection}>
                          <Typography variant="h6" className={classes.infoHeader}>Is a type of</Typography>
                          {this.state.infoTypeOf.map((name, index) => {
                            return (<Typography className={classes.infoTypeOf} key={index}>
                                      {name}
                                    </Typography>
                            );
                          })}
                        </div>
                      )}
                      </CardContent>
                      {!this.state.browserOpened &&
                        <CardActions className={classes.infoPaper}>
                          <Button size="small" onClick={this.openDialog} variant='contained' color='primary'>Learn more</Button>
                        </CardActions>
                      }
                 </div></ClickAwayListener>
              </Card>
            </Grow>
          )}
        </Popper>
        { /* Browse dialog box */}
        <VocabularyBrowser
          open={this.state.browserOpened}
          id={this.state.browseID}
          path={this.state.browsePath}
          changeTerm={this.changeBrowseTerm}
          onClose={this.closeDialog}
          onError={this.logError}
          registerInfo={this.registerInfoButton}
          getInfo={this.getInfo}
          />
        { /* Error snackbar */}
        <Snackbar
          open={this.state.snackbarVisible}
          onClose={() => {this.setState({snackbarVisible: false});}}
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
              message={this.state.snackbarMessage}
            />
          </Snackbar>
      </div>
    );
  }

  // Lookup the search term after a short interval
  // This will reset the interval if called before the interval hangs up
  delayLookup = (status) => {
    if (this.state.lookupTimer !== null) {
      clearTimeout(this.state.lookupTimer);
    }

    this.setState({
      lookupTimer: setTimeout(this.queryInput, 500, status.target.value),
      suggestionsVisible: true,
      suggestions: [],
    })
  }

  makeMultiRequest = (queue, input, status, prevData) => {
    //Get an vocabulary to search through
    var selectedVocab = queue.pop();
    if (selectedVocab === undefined) {
      this.showSuggestions(status, {rows: prevData.slice(0, MAX_RESULTS)});
      return;
    }
    var url = new URL(`./${selectedVocab}.search.json`, REST_URL);
    url.searchParams.set("suggest", input.replace(/[^\w\s]/g, ' '));

    //Are there any filters that should be associated with this request?
    if (this.props?.questionDefinition?.vocabularyFilters?.[selectedVocab]) {
      var filter = this.props.questionDefinition.vocabularyFilters[selectedVocab].map((category) => {
        return (`term_category:${category}`);
      }).join(" OR ");
      url.searchParams.set("customFilter", `(${filter})`);
    }

    MakeRequest(url, (status, data) => {
      this.makeMultiRequest(queue, input, status, prevData.concat(!status && data && data['rows'] ? data['rows'] : []));
    });
  }

  // Grab suggestions for the given input
  queryInput = (input) => {
    // Empty input? Do not query
    if (input === "") {
      this.setState({
        suggestionsLoading: false,
        termInfoVisible: false,
        lookupTimer: null,
      });
      return;
    }

    // Grab suggestions
    //...Make a queue of vocabularies to search through
    var vocabQueue = this.props.vocabularies.slice();
    this.makeMultiRequest(vocabQueue, input, null, []);

    // Hide the infobox and stop the timer
    this.setState({
      suggestionsLoading: true,
      termInfoVisible: false,
      lookupTimer: null,
    });
  }

  // Callback for queryInput to populate the suggestions bar
  showSuggestions = (status, data) => {
    if (!status) {
        // Populate this.state.suggestions
        var suggestions = [];

        if (data["rows"].length > 0) {
          data["rows"].forEach((element) => {
            var name = element["label"] || element["name"] || element["identifier"];
            suggestions.push(
              <MenuItem
                className={this.props.classes.dropdownItem}
                key={element["@path"]}
                onClick={(e) => {
                  if (e.target.localName === "li") {
                    this.props.onClick(element["@path"], name);
                    this.anchorEl.value = name;
                    this.closeDialog();
                  }}
                }
              >
                {name}
                <Button
                  buttonRef={node => {
                    this.registerInfoButton(element["identifier"], node);
                  }}
                  color="primary"
                  aria-owns={this.state.termInfoVisible ? "menu-list-grow" : null}
                  aria-haspopup={true}
                  onClick={(e) => this.getInfo(element["@path"])}
                  className={this.props.classes.buttonLink + " " + this.props.classes.infoButton}
                >
                  <Info color="primary" />
                </Button>
              </MenuItem>
              );
          });
        } else {
          suggestions.push(
            <MenuItem
              className={this.props.classes.dropdownItem}
              key={NO_RESULTS_TEXT}
              onClick={this.props.onClick}
              disabled={true}
            >
              {NO_RESULTS_TEXT}
            </MenuItem>
          )
        }

        this.setState({
          suggestions: suggestions,
          suggestionsVisible: true,
          suggestionsLoading: false,
        });
    } else {
      this.logError("Failed to search vocabulary term");
    }
  }

  // Event handler for clicking away from the autocomplete while it is open
  closeAutocomplete = event => {
    if ((this.menuPopperRef && this.menuPopperRef.contains(event.target))
      || (this.infoRef && this.infoRef.contains(event.target))
      || this.state.browserOpened) {
      return;
    }

    this.setState({
      suggestionsVisible: false,
      termInfoVisible: false,
    });
  };

  // Register a button reference that the info box can use to align itself to
  registerInfoButton = (id, node) => {
    // List items getting deleted will overwrite new browser button refs, so
    // we must ignore deregistration events
    if (node != null) {
      this.state.buttonRefs[id] = node;
    }
  }

  // Grab information about the given ID and populate the info box
  getInfo = (path) => {
    // If we don't yet know anything about our vocabulary, fill it in
    var vocabPath = `${path.split("/").slice(0, -1).join("/")}.json`;
    if (this.state.infoVocabObtained != vocabPath) {
      var url = new URL(vocabPath, window.location.origin);
      this.setState({
        infoVocabTobeObtained: vocabPath
      });
      MakeRequest(url, this.parseVocabInfo);
    }

    var url = new URL(path + ".info.json", window.location.origin);
    MakeRequest(url, this.showInfo);
  }

  parseVocabInfo = (status, data) => {
    if (status === null) {
      var vocabPath = this.state.infoVocabTobeObtained;
      this.setState({
        infoVocabAcronym: data["identifier"] || vocabPath.split("/")[2]?.split('.')[0] || "",
        infoVocabURL: data["website"] || "",
        infoVocabDescription: data["description"],
        infoVocabObtained: vocabPath
      });
    } else {
      this.logError("Failed to search vocabulary details");
    }
  }

  // callback for getInfo to populate info box
  showInfo = (status, data) => {
    if (status === null) {
      // Use an empty array instead of null if this element has no synonyms
      var synonym = [];
      if ("synonyms" in data)
      {
        synonym = data["synonyms"];
      }

      var typeOf = [];
      if ("parents" in data) {
        typeOf = data["parents"].map(element =>
          element["label"] || element["name"] || element["identifier"] || element["id"]
        ).filter(i => i);
      }

      this.setState({
        infoID: data["identifier"],
        infoPath: data["@path"],
        infoName: data["label"],
        infoDefinition: data["def"] || data["description"] || data["definition"],
        infoAlsoKnownAs: synonym,
        infoTypeOf: typeOf,
        infoAnchor: this.state.buttonRefs[data["identifier"]],
        termInfoVisible: true,
        infoAboveBackground: this.state.browserOpened,
      });
    } else {
      this.logError("Failed to search vocabulary term");
    }
  }

  clickAwayInfo = (event) => {
    if ((this.menuPopperRef && this.menuPopperRef.contains(event.target))
      || (this.infoRef && this.infoRef.contains(event.target))) {
      return;
    }

    this.closeInfo();
  }

  // Event handler for clicking away from the info window while it is open
  closeInfo = (event) => {
    this.setState({
      termInfoVisible: false,
      infoAboveBackground: false,
    });
  };

  openDialog = () => {
    this.setState({
      browserOpened: true,
      browseID: this.state.infoID,
      browsePath: this.state.infoPath,
    })
  }

  closeDialog = () => {
    if (this.props.clearOnClick) {
      this.anchorEl.value = "";
    }
    this.anchorEl.select();
    this.setState({
      browserOpened: false,
      suggestionsVisible: false,
      termInfoVisible: false,
      infoAboveBackground: false,
    })
  }

  changeInfoID = (id) => {
    this.setState({
      infoID: id
    });
  };

  changeBrowseTerm = (id, path) => {
    this.setState({
      browseID: id,
      browsePath: path,
    })
  }

  logError = (message) => {
    this.setState({
      snackbarVisible: true,
      snackbarMessage: message,
      suggestionsLoading: false,
    })
  }

  componentDidUpdate(prevProps) {
    // Check to see if we were disabled/enabled
    if (this.props.disabled != prevProps.disabled) {
      // Alter our text to either the override ("Please select at most X options")
      // or empty it
      this.anchorEl.value = this.props.disabled ? this.props.overrideText : "";
    }
  }
}

VocabularyQuery.propTypes = {
    classes: PropTypes.object.isRequired,
    overrideText: PropTypes.string,
    clearOnClick: PropTypes.bool,
    onInputFocus: PropTypes.func,
    defaultValue: PropTypes.string,
    noMargin: PropTypes.bool
};

VocabularyQuery.defaultProps = {
  vocabularies: ['hpo'],
  searchDefault: 'Search',
  overrideText: '',
  clearOnClick: true
};

export default withStyles(QueryStyle)(VocabularyQuery);
