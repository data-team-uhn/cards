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
import React, { useState } from "react";
import PropTypes from "prop-types";
// @material-ui/core
import { withStyles } from "@material-ui/core";
import { Button, Dialog, DialogContent, DialogTitle, IconButton } from '@material-ui/core';
import CloseIcon from '@material-ui/icons/Close';

import BrowseListChild from "./browseListChild.jsx";
import BrowseTheme from "./browseStyle.jsx";

import { REST_URL, MakeRequest } from "./util.jsx";

// Component that renders a modal dialog, to browse related terms of an input term.
//
// Required arguments:
//  term: Term to search
//  vocabulary: Name of the vocabulary to use to look up terms
//  changeId: callback to change the term being looked up
//  registerInfo: callback to add a possible hook point for the info box
//  getInfo: callback to change the currently displayed info box term
//  onClose: callback when this dialog is closed
//  onError: callback when an error occurs
//
// Optional arguments:
//  fullscreen: whether or not the dialog is fullscreen (default: false)
function VocabularyBrowser(props) {
  const { classes, fullscreen, term, changeId, registerInfo, getInfo, onClose, onError, vocabulary, ...rest } = props;

  const [ lastKnownTerm, setLastKnownTerm ] = useState("");
  const [ parentNode, setParentNode ] = useState();
  const [ currentNode, setCurrentNode ] = useState();

  // Rebuild the browser tree centered around the given term.
  let rebuildBrowser = (id) => {
    // Do not re-grab suggestions for the same term, or if our lookup has failed (to prevent infinite loops)
    if (id === lastKnownTerm) {
      return;
    }

    // If the search is empty, remove every component
    if (id === "" || id === null) {
      setParentNode(null);
      setCurrentNode(null);
      setLastKnownTerm(id);
      return;
    }

    // Create the XHR request
    var escapedID = id.replace(":", "");  // JCR nodes do not have colons in their names
    var url = new URL(`./${vocabulary}/${escapedID}.info.json`, REST_URL);
    MakeRequest(url, rebuildTree);
    setLastKnownTerm(id);
  }

  // Callback from an onload to generate the tree from a /suggest query about the parent
  let rebuildTree = (status, data) => {
    if (status === null) {
      // Construct parent elements, if they exist
      var parentBranches = null;
      if ("parents" in data) {
        parentBranches = data["parents"].map((row, index) => {
          return constructBranch(row["id"], row["name"], false, false, false, row["lfs:hasChildren"]);
        });
      }

      setParentNode(parentBranches);
      setCurrentNode(constructBranch(data["id"], data["name"], true, true, true, data["lfs:hasChildren"]));
    } else {
      onError("Error: initial term lookup failed with code " + status);
    }
  }

  // Construct a branch element for rendering
  let constructBranch = (id, name, ischildnode, defaultexpanded, bolded, hasChildren) => {
    return(
      <BrowseListChild
        id={id}
        name={name}
        changeId={changeId}
        registerInfo={registerInfo}
        getInfo={getInfo}
        expands={ischildnode}
        defaultOpen={defaultexpanded}
        key={id}
        headNode={!ischildnode}
        bolded={bolded}
        onError={onError}
        vocabulary={vocabulary}
        knownHasChildren={hasChildren}
      />
    );
  }

  rebuildBrowser(term);

  return (
    <Dialog
      fullscreen={fullscreen.toString()}
      className={classes.dialog}
      onClose={onClose}
      classes={{
        paper: classes.dialogPaper,
        root: classes.infoDialog
      }}
      {...rest}
    >
      <DialogTitle>
        Related terms
        <IconButton aria-label="close" className={classes.closeButton} onClick={onClose}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent className={classes.treeContainer} dividers>
        <div className={classes.treeRoot}>
          {parentNode}
        </div>
        <div className={classes.treeNode}>
          {currentNode}
        </div>
      </DialogContent>
    </Dialog>
  );
}

VocabularyBrowser.propTypes = {
  classes: PropTypes.object.isRequired,
  fullscreen: PropTypes.bool,
  term: PropTypes.string.isRequired,
  vocabulary: PropTypes.string.isRequired,
  changeId: PropTypes.func.isRequired,
  registerInfo: PropTypes.func.isRequired,
  getInfo: PropTypes.func.isRequired,
  onClose: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired
};

VocabularyBrowser.defaultProps = {
  fullscreen: true
}

export default withStyles(BrowseTheme)(VocabularyBrowser);
