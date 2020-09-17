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
//  id: Term ID to search
//  path: Term @path to get the term info
//  changeTerm: callback to change the term id and path being looked up
//  registerInfo: callback to add a possible hook point for the info box
//  getInfo: callback to change the currently displayed info box term
//  onClose: callback when this dialog is closed
//  onError: callback when an error occurs
//
// Optional arguments:
//  fullscreen: whether or not the dialog is fullscreen (default: false)
function VocabularyBrowser(props) {
  const { classes, fullscreen, id, path, changeTerm, registerInfo, getInfo, onClose, onError, ...rest } = props;

  const [ lastKnownTerm, setLastKnownTerm ] = useState("");
  const [ parentNode, setParentNode ] = useState();
  const [ currentNode, setCurrentNode ] = useState();

  // Rebuild the browser tree centered around the given term.
  let rebuildBrowser = () => {
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
    var url = new URL(path + ".info.json", window.location.origin);
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
  let constructBranch = (id, path, name, ischildnode, defaultexpanded, bolded, hasChildren) => {
    return(
      <BrowseListChild
        id={id}
        path={path}
        name={name}
        changeTerm={changeTerm}
        registerInfo={registerInfo}
        getInfo={getInfo}
        expands={ischildnode}
        defaultOpen={defaultexpanded}
        key={id}
        headNode={!ischildnode}
        bolded={bolded}
        onError={onError}
        knownHasChildren={hasChildren}
      />
    );
  }

  rebuildBrowser();

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
  id: PropTypes.string.isRequired,
  path: PropTypes.string.isRequired,
  changeTerm: PropTypes.func.isRequired,
  registerInfo: PropTypes.func.isRequired,
  getInfo: PropTypes.func.isRequired,
  onClose: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired
};

VocabularyBrowser.defaultProps = {
  fullscreen: true
}

export default withStyles(BrowseTheme)(VocabularyBrowser);
