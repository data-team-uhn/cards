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
import { MenuItem, MenuList, Grow, Paper, ClickAwayListener, CircularProgress, Popper, Typography } from "@material-ui/core"
// material-dashboard-
import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
import CustomInput from "material-dashboard-react/dist/components/CustomInput/CustomInput.js";
// @material-ui/icons
import Search from "@material-ui/icons/Search";
import Info from "@material-ui/icons/Info";


import thesaurusStyle from "./queryStyle.jsx";

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
      // Strings used by the info box
      infoID: "",
      infoName: "",
      infoDefinition: "",
      infoAlsoKnownAs: [],
      infoTypeOf: "",
      infoAnchor: null,
      buttonRefs: {},
    };
  }

  showInfo = (event, data) => {
    if (event === null) {
      // Use an empty array instead of null if this element has no synonyms
      var synonym = [];
      if ("synonym" in data)
      {
        synonym = data["synonym"];
      }

      this.setState({
        infoID: data["id"],
        infoName: data["name_translated"],
        infoDefinition: data["def_translated"],
        infoAlsoKnownAs: synonym,
        infoTypeOf: data["parents"][0]["name_translated"],  // TODO: Multiple parents?
        infoAnchor: this.state.buttonRefs[data["id"]],
        termInfoVisible: true,
      });
    } else {
      console.log("Error: term lookup failed with code " + event.ToString());
    }
  }

  getInfo = (id) => {
    var URL = "https://services.phenotips.org/rest/vocabularies/hpo/" + id;
    var xhr = window.Sling.getXHR();
    xhr.open('GET', URL, true);
    xhr.responseType = 'json';
    xhr.onload = () => {
      var status = xhr.status;
      if (status === 200) {
        this.showInfo(null, xhr.response);
      } else {
        this.showInfo(status, xhr.response);
      }
    };
    xhr.send();
  }

  showSuggestions = (event, data) => {
    if (event === null) {
        // Show suggestions
        var suggestions = [];

        data["rows"].forEach((element) => {
          suggestions.push(
            <MenuItem
              className={this.props.classes.dropdownItem}
              key={element["name"]}
            >
              {element["name"]}
              <Button
                buttonRef={node => {
                  this.state.buttonRefs[element["id"]] = node;
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
      console.log("Error: thesaurus lookup failed with code " + event.ToString());
    }
  }

  queryInput = (input) => {
    var URL = "https://services.phenotips.org/rest/vocabularies/hpo/suggest?input=" + input;
    var xhr = window.Sling.getXHR();

    xhr.open('GET', URL, true);
    xhr.responseType = 'json';
    xhr.onload = () => {
      var status = xhr.status;
      if (status === 200) {
        this.showSuggestions(null, xhr.response);
      } else {
        this.showSuggestions(status, xhr.response);
      }
    };
    xhr.send();

    // Hide the infobox and stop the timer
    this.setState({
      suggestionsLoading: true,
      termInfoVisible: false,
      lookupTimer: null,
    });
  }

  // Lookup the search term after a short interval
  // This will reset the interval if called before the interval hangs up
  delayLookup = (event) => {
    if (this.state.lookupTimer !== null) {
      clearTimeout(this.state.lookupTimer);
    }

    this.setState({
      lookupTimer: setTimeout(this.queryInput, 500, event.target.value),
      suggestionsVisible: true,
      suggestions: [],
    })
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

  // Event handler for clicking away from the info window while it is open
  closeInfo = event => {
    if (this.anchorEl.contains(event.target)) {
      return;
    }

    this.setState({ termInfoVisible: false });
  };

  render() {
    const { classes } = this.props;

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
                        <Typography className={classes.infoDataSource}>The Human Phenotype Ontology (HPO)</Typography>
                      </a>
                      <Typography inline className={classes.infoIDTypography}>{this.state.infoID} </Typography>
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
                          <Typography className={classes.infoTypeOf}>{this.state.infoTypeOf}</Typography> <br />
                        </div>
                      )}
                    </div>
                  </ClickAwayListener>
                </Paper>
              </Card>
            </Grow>
          )}
        </Popper>
      </div>
    );
  }
}

Thesaurus.propTypes = {
    classes: PropTypes.object.isRequired
};

export default withStyles(thesaurusStyle)(Thesaurus);
