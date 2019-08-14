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
import { withStyles } from "@material-ui/core/styles";
import { MenuItem, MenuList, Grow, Paper, ClickAwayListener, CircularProgress, Popper, Snackbar, SnackbarContent, Typography } from "@material-ui/core"
// MaterialDashboardReact
import { Button, Card, CardHeader, CardBody, CustomInput, QueryStyle } from "MaterialDashboardReact";
// @material-ui/icons
import Search from "@material-ui/icons/Search";
import Info from "@material-ui/icons/Info";

import VocabularyBrowser from "./browse.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";

class Thesaurus extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      title: "LFS Patients",
      subtitle: "",
      suggestions: [],
      suggestionsLoading: false,
      suggestionsVisible: false,
      termInfoVisible: false,
      lookupTimer: null,
      browserOpened: false,
      browseID: "",
      // Strings used by the info box
      infoID: "",
      infoName: "",
      infoDefinition: "",
      infoAlsoKnownAs: [],
      infoTypeOf: "",
      infoAnchor: null,
      buttonRefs: {},
      vocabulary: props.Vocabulary,
    };
  }

  render() {
    const { classes, onSelect } = this.props;

    return (
      <div>
        <Card>
          <CardHeader color="warning">
            <h4 className={classes.cardTitleWhite}>{this.state.title}</h4>
            <p className={classes.cardCategoryWhite}>{this.state.subtitle}</p>
          </CardHeader>
          <CardBody>
            <div className={classes.searchWrapper}>
              <CustomInput
                formControlProps={{
                  className: classes.margin + " " + classes.search
                }}
                inputProps={{
                  placeholder: "Search",
                  inputProps: {
                    "aria-label": "Search"
                  },
                  onChange: this.delayLookup,
                  inputRef: node => {
                    this.anchorEl = node;
                  },
                  onKeyPress: (event) => {
                    if (event.key == 'Enter') {
                      this.queryInput(this.anchorEl.value);
                    }
                  },
                  onFocus: this.delayLookup,
                }}
              />
              <div className={classes.searchWrapper}>
                <Button
                  color="white"
                  aria-label="edit"
                  justIcon
                  round
                  onClick={() => this.queryInput(this.anchorEl.value)}
                >
                  <Search />
                </Button>
                {this.state.suggestionsLoading ? <CircularProgress size={50} className={classes.suggestionProgress} /> : ""}
              </div>
            </div>
          </CardBody>
        </Card>
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
                  <MenuList role="menu">
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
            + " " + classes.popperInfoOnTop
          }
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
                <Paper className={classes.infoPaper}>
                  <ClickAwayListener onClickAway={this.closeInfo}>
                    <div>
                      <a
                        href="http://human-phenotype-ontology.github.io/"
                        target="_blank"
                        title="The Human Phenotype Ontology project: linking molecular biology and disease through phenotype data. Sebastian Köhler, Sandra C Doelken, Christopher J. Mungall, Sebastian Bauer, Helen V. Firth, et al. Nucl. Acids Res. (1 January 2014) 42 (D1): D966-D974 doi:10.1093/nar/gkt1026. Current version: releases/2018-10-09"
                      >
                        <Typography inline className={classes.infoDataSource}>The Human Phenotype Ontology (HPO)</Typography>
                      </a>
                      <Button
                        className={classes.closeButton}
                        onClick={this.closeInfo}
                        variant="outlined"
                        justIcon={true}
                        simple={true}
                      >
                        x
                      </Button>
                      <br />
                      <Typography inline className={classes.infoIDTypography}>{this.state.infoID} </Typography>
                      <Typography inline className={classes.infoName}>{this.state.infoName}</Typography> <br />
                      <Typography className={classes.infoDefinition}>{this.state.infoDefinition}</Typography> <br />
                      {this.state.infoAlsoKnownAs.length > 0 && (
                        <div>
                          <Typography className={classes.infoHeader}>Also known as</Typography> <br />
                          {this.state.infoAlsoKnownAs.map((name, index) => {
                            return (<Typography className={classes.infoAlsoKnownAs} key={index}>
                                      {name}
                                    </Typography>
                            );
                          })} <br />
                        </div>
                      )}
                      {this.state.infoTypeOf !== "" && (
                        <div>
                          <Typography className={classes.infoHeader}>Is a type of</Typography> <br />
                          {this.state.infoTypeOf.map((name, index) => {
                            return (<Typography className={classes.infoTypeOf} key={index}>
                                      {name}
                                    </Typography>
                            );
                          })}
                        </div>
                      )}
                      {!this.state.browserOpened && <Button onClick={this.openDialog}>
                        See more
                      </Button>}
                    </div>
                  </ClickAwayListener>
                </Paper>
              </Card>
            </Grow>
          )}
        </Popper>
        { /* Browse dialog box */}
        <VocabularyBrowser
          open={this.state.browserOpened}
          term={this.state.browseID}
          changeId={this.changeBrowseID}
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
            vertical: 'top',
            horizontal: 'right',
          }}
          variant="error"
          >
            <SnackbarContent
              className={classes.errorSnack}
              variant="error"
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
    input = encodeURIComponent(input);
    var URL = `${REST_URL}/${this.props.Vocabulary}/suggest?input=${input}`;
    MakeRequest(URL, this.showSuggestions);

    // Hide the infobox and stop the timer
    this.setState({
      suggestionsLoading: true,
      termInfoVisible: false,
      lookupTimer: null,
    });
  }

  // Callback for queryInput to populate the suggestions bar
  showSuggestions = (status, data) => {
    if (status === null) {
        // Populate this.state.suggestions
        var suggestions = [];

        data["rows"].forEach((element) => {
          suggestions.push(
            <MenuItem
              className={this.props.classes.dropdownItem}
              key={element["name"]}
              onClick={this.props.onClick}
            >
              {element["name"]}
              <Button
                buttonRef={node => {
                  this.registerInfoButton(element["id"], node);
                }}
                color="info"
                justIcon={true}
                simple={true}
                aria-owns={this.state.termInfoVisible ? "menu-list-grow" : null}
                aria-haspopup={true}
                onClick={(e) => this.getInfo(element["id"])}
                className={this.props.classes.buttonLink}
              >
                <Info color="primary" />
              </Button>
            </MenuItem>
            );
        });

        this.setState({
          suggestions: suggestions,
          suggestionsVisible: true,
          suggestionsLoading: false,
        });
    } else {
      this.logError("Error: Thesaurus lookup failed with code " + status);
    }
  }

  // Event handler for clicking away from the autocomplete while it is open
  closeAutocomplete = event => {
    if (this.anchorEl.contains(event.target)) {
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
  getInfo = (id) => {
    var URL = `${REST_URL}/${this.props.Vocabulary}/${id}`;
    MakeRequest(URL, this.showInfo);
  }

  // callback for getInfo to populate info box
  showInfo = (status, data) => {
    if (status === null) {
      // Use an empty array instead of null if this element has no synonyms
      var synonym = [];
      if ("synonym" in data)
      {
        synonym = data["synonym"];
      }

      const typeOf = data["parents"].map((parent, index) => {
        return parent["name"];
      })

      this.setState({
        infoID: data["id"],
        infoName: data["name"],
        infoDefinition: data["def"],
        infoAlsoKnownAs: synonym,
        infoTypeOf: typeOf,
        infoAnchor: this.state.buttonRefs[data["id"]],
        termInfoVisible: true,
      });
    } else {
      this.logError("Error: term lookup failed with code " + status);
    }
  }

  // Event handler for clicking away from the info window while it is open
  closeInfo = (event) => {
    if (this.anchorEl.contains(event.target)) {
      return;
    }

    this.setState({ termInfoVisible: false });
  };

  openDialog = () => {
    this.setState({
      browserOpened: true,
      browseID: this.state.infoID,
      suggestionsVisible: false,
      termInfoVisible: false,
    })
  }

  closeDialog = () => {
    this.setState({
      browserOpened: false,
      suggestionsVisible: false,
      termInfoVisible: false,
    })
  }

  changeInfoID = (id) => {
    this.setState({
      infoID: id
    });
  };

  changeBrowseID = (id) => {
    this.setState({
      browseID: id,
    })
  }

  logError = (message) => {
    this.setState({
      snackbarVisible: true,
      snackbarMessage: message,
    })
  }
}

Thesaurus.propTypes = {
    classes: PropTypes.object.isRequired
};

Thesaurus.defaultProps = {
  Vocabulary: 'hpo'
};

export default withStyles(QueryStyle)(Thesaurus);
