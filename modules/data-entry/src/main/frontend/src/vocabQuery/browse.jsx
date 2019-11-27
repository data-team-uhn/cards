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
import { withStyles } from "@material-ui/core";
import { Button, Dialog, DialogContent, DialogTitle, Typography } from '@material-ui/core';

import BrowseListChild from "./browseListChild.jsx";
import BrowseTheme from "./browseStyle.jsx";

import { REST_URL, MakeRequest } from "./util.jsx";

class VocabularyBrowser extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      lastKnownTerm: "",
      parentNode: null,
      currentNode: null,
    };
  }

  render() {
    const { classes, term, changeId, registerInfo, getInfo, onClose, onError, ...rest } = this.props;
    const fullscreen = false;
    this.rebuildBrowser(term);

    return (
      <Dialog
        fullscreen={fullscreen.toString()}
        className={classes.dialog}
        onClose={onClose}
        classes={{paper: classes.dialogPaper}}
        {...rest}
      >
        <DialogTitle className={classes.headbar}>
          <Typography className={classes.headbarText}>Related terms</Typography>
          <Button
            className={classes.closeButton}
            onClick={onClose}
            variant="outlined"
          >
            ×
          </Button>
        </DialogTitle>
        <DialogContent className={classes.treeContainer}>
          <div className={classes.treeRoot}>
            {this.state.parentNode}
          </div>
          <div className={classes.treeNode}>
            {this.state.currentNode}
          </div>
        </DialogContent>
      </Dialog>
    );
  }

  // Rebuild the browser tree centered around the given term.
  rebuildBrowser = (id) => {
    // Do not re-grab suggestions for the same term
    if (id === this.state.lastKnownTerm) {
      return;
    }

    // If the search is empty, remove every component
    if (id === "" || id === null) {
      this.setState({
        parentNode: null,
        currentNode: null,
        lastKnownTerm: id,
      })
      return;
    }

    // Create the XHR request
    var URL = REST_URL + `/${this.props.vocabulary}/${id}/`;
    MakeRequest(URL, this.rebuildTree);
  }

  // Callback from an onload to generate the tree from a /suggest query about the parent
  rebuildTree = (status, data) => {
    if (status === null) {
      // Construct parent elements, if they exist
      var parentBranches = null;
      if ("parents" in data) {
        parentBranches = data["parents"].map((row, index) => {
          return this.constructBranch(row["id"], row["name"], false, false, false);
        });
      }

      this.setState({
        parentNode: parentBranches,
        currentNode: this.constructBranch(data["id"], data["name"], true, true, true),
        lastKnownTerm: this.props.term,
      })
    } else {
      this.props.onError("Error: initial term lookup failed with code " + status);
    }
  }

  // Construct a branch element for rendering
  constructBranch = (id, name, ischildnode, defaultexpanded, bolded) => {
    return(
      <BrowseListChild
        id={id}
        name={name}
        changeId={this.props.changeId}
        registerInfo={this.props.registerInfo}
        getInfo={this.props.getInfo}
        expands={ischildnode}
        defaultOpen={defaultexpanded}
        key={id}
        headNode={!ischildnode}
        bolded={bolded}
        onError={this.props.onError}
        vocabulary={this.props.vocabulary}
      />
    );
  }
}

VocabularyBrowser.propTypes = {
  classes: PropTypes.object.isRequired
};

export default withStyles(BrowseTheme)(VocabularyBrowser);