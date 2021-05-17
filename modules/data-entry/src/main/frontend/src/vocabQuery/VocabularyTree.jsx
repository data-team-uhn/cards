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

import { withStyles } from "@material-ui/core";
import { Button, Dialog, DialogContent, DialogTitle, IconButton } from '@material-ui/core';
import CloseIcon from '@material-ui/icons/Close';

import VocabularyBranch from "./VocabularyBranch.jsx";
import BrowseTheme from "./browseStyle.jsx";

import { REST_URL, MakeRequest } from "./util.jsx";

// Component that renders a modal dialog, to browse related terms of an input term.
//
// Required arguments:
//  open: Boolean representing whether or not the tree dialog is open
//  path: Term @path to get the term info
//  onTermClick: Callback to change the term path being looked up
//  registerInfo: Callback to add a possible hook point for the info box
//  getInfo: Callback to change the currently displayed info box term
//  onClose: Callback when this dialog is closed
//  onError: Callback when an error occurs
//  browserRef: Reference to the vocabulary tree node
//
// Optional arguments:
//  fullscreen: Boolean representing whether or not the dialog is fullscreen (default: false)
//
function VocabularyTree(props) {
  const { open, path, onTermClick, registerInfo, getInfo, onClose, onError, browserRef, fullscreen, classes, ...rest } = props;

  const [ lastKnownTerm, setLastKnownTerm ] = useState("");
  const [ parentNode, setParentNode ] = useState();
  const [ currentNode, setCurrentNode ] = useState();

  // Rebuild the browser tree centered around the given term.
  let rebuildBrowser = () => {
    // Do not re-grab suggestions for the same term, or if our lookup has failed (to prevent infinite loops)
    if (path === lastKnownTerm) {
      return;
    }

    // If the search is empty, remove every component
    if (!path) {
      setParentNode(null);
      setCurrentNode(null);
      setLastKnownTerm(path);
      return;
    }

    // Create the XHR request
    var url = new URL(path + ".info.json", window.location.origin);
    MakeRequest(url, rebuildTree);
    setLastKnownTerm(path);
  }

  // Callback from an onload to generate the tree from a /suggest query about the parent
  let rebuildTree = (status, data) => {
    if (status === null) {
      // Construct parent elements, if they exist
      var parentBranches = null;
      if ("parents" in data) {
        parentBranches = data["parents"].map((row, index) => {
          return row["identifier"] ? constructBranch(row["identifier"], row["@path"], row["label"], false, false, false, row["lfs:hasChildren"]) : false;
        }).filter(i => i);
      }

      setParentNode(parentBranches);
      setCurrentNode(constructBranch(data["identifier"], data["@path"], data["label"], true, true, true, data["lfs:hasChildren"]));
    } else {
      onError("Error: initial term lookup failed with code " + status);
    }
  }

  // Construct a branch element for rendering
  let constructBranch = (id, path, name, ischildnode, defaultexpanded, focused, hasChildren) => {
    return(
      <VocabularyBranch
        id={id}
        path={path}
        name={name.trim()}
        onTermClick={onTermClick}
        registerInfo={registerInfo}
        getInfo={getInfo}
        expands={ischildnode}
        defaultOpen={defaultexpanded}
        key={id}
        headNode={!ischildnode}
        focused={focused}
        onError={onError}
        knownHasChildren={!!hasChildren}
      />
    );
  }

  rebuildBrowser();

  return (
    <Dialog
      open={open}
      ref={browserRef}
      fullWidth
      maxWidth="sm"
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
        {parentNode?.length ?
        <div className={classes.treeRoot}>
          {parentNode}
        </div>
        : ""}
        <div className={parentNode?.length ? classes.treeNode : undefined}>
          {currentNode}
        </div>
      </DialogContent>
    </Dialog>
  );
}

VocabularyTree.propTypes = {
  open: PropTypes.bool.isRequired,
  path: PropTypes.string.isRequired,
  onTermClick: PropTypes.func.isRequired,
  registerInfo: PropTypes.func.isRequired,
  getInfo: PropTypes.func.isRequired,
  onClose: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired,
  browserRef: PropTypes.object.isRequired,
  fullscreen: PropTypes.bool,
  classes: PropTypes.object.isRequired
};

VocabularyTree.defaultProps = {
  fullscreen: true
}

export default withStyles(BrowseTheme)(VocabularyTree);
