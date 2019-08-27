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
import React from "react";
import PropTypes from "prop-types";
// @material-ui/core
import { List, withStyles } from "@material-ui/core";

import Thesaurus from "../query/query.jsx";
import SelectorStyle from "./selectorStyle.jsx";
import VocabularyChild from "./selectChild.jsx";
import SelectionResults from "./selectionResults.jsx";
import { MakeRequest, REST_URL } from "../query/util.jsx";

const NAME_POS = 0;
const ID_POS = 1;
const IS_PRESELECT_POS = 2;

class VocabularySelector extends React.Component {
  constructor(props) {
    super(props);
    var listChildren = [];

    this.state = {
      title: props.name,
      listChildren: listChildren,
      selected: 0,
    };
  }

  render() {
    const {name, source, suggestionCategories, max, selectionContainer, defaultSuggestionIDs, defaultSuggestionNames, classes, ...rest} = this.props;
    var disabled = max > 1 && this.state.selected >= max;

    return (
      <React.Fragment>
        <Thesaurus
          onClick = {this.addSelection}
          suggestionCategories = {suggestionCategories}
          Vocabulary = {source}
          ref = {(ref) => {this.thresaurusRef = ref;}}
          disabled = {disabled}
          overrideText = {disabled ? `Please select at most ${max} options.` : undefined }
          {...rest}
        >
          {
            // If we don't have an external container, add results here
            typeof selectionContainer === "undefined" &&
            (
              <List className={classes.selectionList}>
                {this.generateListChildren(disabled)}
              </List>
            )
          }
        </Thesaurus>
        {
          // If we have an external container, open a portal there
          typeof selectionContainer !== "undefined" &&
          (
            <SelectionResults
              root = {selectionContainer}
              >
              <List className={classes.selectionList}>
                {this.generateListChildren()}
              </List>
            </SelectionResults>
          )
        }
      </React.Fragment>
    );
  }

  /* Since we need to enable/disable children on the fly, we also generate them
      on the fly */
  generateListChildren = (disabled) => {
    return this.state.listChildren.map( (childData) => {
      return (
        <VocabularyChild
          id={childData[ID_POS]}
          key={childData[ID_POS]}
          name={childData[NAME_POS]}
          onClick={this.removeSelection}
          disabled={disabled}
          isPreselected={childData[IS_PRESELECT_POS]}
        ></VocabularyChild>
      );
    });
  }

  // Create a new child from the selection with parent
  addSelection = (id, name) => {
    // Also do not add anything if we are at our maximum number of selections
    if (this.state.selected >= this.props.max && this.props.max > 1 ) {
      return;
    }

    // Also do not add duplicates
    if (this.state.listChildren.some(element => {return element[ID_POS] === id})) {
      return;
    }

    var newChildren;
    var numSelected = 0;
    if (this.props.max == 1) {
      // If only 1 child is allowed, replace it instead of copying our array
      newChildren = [];
      numSelected = 1;
    } else {
      // As per React specs, we do not modify the state array directly, but slice and add
      newChildren = this.state.listChildren.slice();
      numSelected = this.state.selected + 1;
    }
    newChildren.push([name, id, false]);
    this.setState({listChildren: newChildren, selected: numSelected});
  }

  componentDidMount() {
    this.populateDefaults();
  }

  populateDefaults() {
    var newChildren = this.state.listChildren.slice();
    for (var id in this.props.defaultSuggestions) {
      // If we are given a name, use it
      if (typeof this.props.defaultSuggestions[id] !== "undefined") {
        newChildren.push([this.props.defaultSuggestions[id], id, true]);
        continue;
      }

      // Determine the name from our vocab
      var testId = id;
      var escapedId = id.replace(":", "\\:"); // URI Escape the : from HP: for SolR
      var customFilter = encodeURIComponent(`id:${escapedId}`);
      var URL = `${REST_URL}/${this.props.source}/suggest?sort=nameSort%20asc&maxResults=1&input=${id}&customFilter=${customFilter}`
      MakeRequest(URL, (status, data) => this.addDefaultSuggestion(status, data, testId));
    };
    this.setState({
      listChildren: newChildren,
    });
  }

  addDefaultSuggestion = (status, data, id) => {
    if (status === null) {
      var name = id;
      // Determine if we can find the name from here
      if (data["rows"].length > 0) {
        name = data["rows"][0]["name"];
      }
      // If the name could not be found, use the ID as the name

      // Possible race condition here?
      var newChildren = this.state.listChildren.slice();
      newChildren.push([name, id, true]);
      this.setState({listChildren: newChildren});
    } else {
      console.log("Error: Thesaurus lookup failed with code " + status);
    }
  }

  componentDidUpdate(prevProps) {
    // Use JSON.stringify to compare the values of defaultSuggestions rather than their refs
    if (JSON.stringify(prevProps.defaultSuggestions) !== JSON.stringify(this.props.defaultSuggestions)) {
      this.populateDefaults();
    }
  }

  removeSelection = (id, name, wasSelected=false) => {
    // Do not remove this element if it is in our default suggestions
    // Instead, just update the number of items selected
    if (typeof this.props.defaultSuggestions !== "undefined" && id in this.props.defaultSuggestions) {
      if (wasSelected) {
        this.setState({selected: this.state.selected - 1});
      } else {
        this.setState({selected: this.state.selected + 1});
      }
      return;
    }

    var newChildren = this.state.listChildren.filter(element => element[ID_POS] != id);
    this.setState({
      listChildren: newChildren,
      selected: this.state.selected - 1
    });
  }
}

VocabularySelector.propTypes = {
    classes: PropTypes.object.isRequired,
    name: PropTypes.string,
    source: PropTypes.string.isRequired,
    max: PropTypes.number.isRequired,
    requiredAncestors: PropTypes.array,
    defaultSuggestions: PropTypes.object,
    searchDefault: PropTypes.string,
};

VocabularySelector.defaultProps = {
    name: "VocabularySelector",
    source: "hpo",
    max: 0,
    searchDefault: 'Other (specify here)'
};

export default withStyles(SelectorStyle)(VocabularySelector);
