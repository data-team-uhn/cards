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
import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import { withStyles, DialogContent } from '@material-ui/core';
import ResponsiveDialog from "../components/ResponsiveDialog";
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
//  vocabulary: Vocabulary info
//  browseRoots: Boolean representing whether or not the vocabulary tree shows roots
//
function VocabularyTree(props) {
  const { open, path, onTermClick, registerInfo, getInfo, onClose, onError, browserRef, classes, vocabulary, browseRoots, ...rest } = props;

  const [ lastKnownTerm, setLastKnownTerm ] = useState("");
  const [ parentNode, setParentNode ] = useState();
  const [ currentNode, setCurrentNode ] = useState();
  const [ roots, setRoots ] = useState(vocabulary.roots);

  useEffect(() => {
    if (browseRoots && !vocabulary.roots) {
      // if vocab was just installed -> grab the info to get the roots for browser population
      var url = new URL(`${vocabulary.acronym}.json`, REST_URL);
      MakeRequest(url, getRoots);
    } else {
      rebuildBrowser();
    }
  }, [path, vocabulary])

  let getRoots = (status, data) => {
    setRoots(data.roots);
    rebuildBrowser();
  }

  // Rebuild the browser tree centered around the given term.
  let rebuildBrowser = () => {
    // if we are building for roots for the first time
    if (roots && !parentNode) {
      let rootBranches = roots.map((row, index) => {
        return row["identifier"] ? constructBranch(row["identifier"], row["@path"], row["label"], true, false, false, true) : false;
      }).filter(i => i);
      setParentNode(rootBranches);
      return;
    }

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

  return (
    <ResponsiveDialog
      title={`${vocabulary.name} (${vocabulary.acronym})` || "Related terms"}
      withCloseButton
      open={open}
      ref={browserRef}
      onClose={onClose}
      className={classes.dialog}
      classes={{
        paper: classes.dialogPaper,
        root: classes.infoDialog
      }}
      {...rest}
    >
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
    </ResponsiveDialog>
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
  browseRoots: PropTypes.bool,
  vocabulary: PropTypes.object,
  classes: PropTypes.object.isRequired
};

export default withStyles(BrowseTheme)(VocabularyTree);
