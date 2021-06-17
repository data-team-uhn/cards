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

import { withStyles, Button, Checkbox, DialogContent, DialogActions, Chip, Radio, Typography } from '@material-ui/core';
import ResponsiveDialog from "../components/ResponsiveDialog";
import VocabularyBranch from "./VocabularyBranch.jsx";
import AnswerInstructions from "../questionnaire/AnswerInstructions.jsx";
import { LABEL_POS, VALUE_POS } from "../questionnaire/Answer";
import BrowseTheme from "./browseStyle.jsx";

import { REST_URL, MakeRequest } from "./util.jsx";

// Component that renders a modal dialog, to browse related terms of an input term.
//
// Required arguments:
//  open: Boolean representing whether or not the tree dialog is open
//  path: Term @path to get the term info
//  registerInfo: Callback to add a possible hook point for the info box
//  getInfo: Callback to change the currently displayed info box term
//  onClose: Callback when this dialog is closed
//  onError: Callback when an error occurs
//  browserRef: Reference to the vocabulary tree node
//
// Optional arguments:
//  onTermClick: Callback to change the term path being looked up
//  vocabulary: Vocabulary info
//  browseRoots: Boolean representing whether or not the vocabulary tree shows roots
//  onCloseInfoBox: Callback to close term info box
//  allowTermSelection: Boolean enabler for term selection from vocabulary tree browser
//  initialSelection: Existing answers
//  questionDefinition: Object describing the Vocabulary Question for which this suggested input is displayed
//
function VocabularyTree(props) {
  const { open, path, onTermClick, registerInfo, getInfo, onClose, onCloseInfoBox, onError, browserRef, classes, vocabulary,
    browseRoots, allowTermSelection, initialSelection, questionDefinition, ...rest } = props;

  const [ lastKnownTerm, setLastKnownTerm ] = useState("");
  const [ parentNode, setParentNode ] = useState();
  const [ currentNode, setCurrentNode ] = useState();
  const [ roots, setRoots ] = useState(vocabulary.roots);
  const maxAnswers = questionDefinition?.maxAnswers;
  const selectorComponent = allowTermSelection ? (maxAnswers == 1 ? Radio : Checkbox) : undefined;

  const [selectedTerms, setSelectedTerms] = useState(initialSelection);
  const [removedTerms, setRemovedTerms] = useState([]);

  useEffect(() => {
    if (browseRoots && !vocabulary.roots) {
      // if vocab was just installed -> grab the info to get the roots for browser population
      var url = new URL(`${vocabulary.acronym}.json`, REST_URL);
      MakeRequest(url, getRoots);
    } else {
      rebuildBrowser();
    }
  }, [path, vocabulary])

  useEffect(() => {
    rebuildBrowser();
  }, [roots])

  let getRoots = (status, data) => {
    setRoots(data?.roots);
  }

  // Rebuild the browser tree centered around the given term.
  let rebuildBrowser = () => {
    // if we are building for roots for the first time
    if (roots && !parentNode) {
      let rootBranches = roots.map((row, index) => {
        return row["identifier"] ? constructBranch(row["identifier"], row["@path"], row["label"], true, (roots.length == 1), false, true) : false;
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

  let addOption = (name, path) => {
    if (maxAnswers == 1) {
      setSelectedTerms([[name, path]]);
      return;
    }
    setSelectedTerms(old => {
      let newTerms = old.slice();
      newTerms.push([name, path]);
      return newTerms;
    });
    // remove from removed
    setRemovedTerms(old => {
      return old.filter(item => item[VALUE_POS] != path);
    });
  }

  let removeOption = (name, path) => {
    setSelectedTerms(old => {
      return old.filter(item => item[VALUE_POS] != path);
    });
    if (initialSelection.some(item => item[VALUE_POS] == path)) {
      // they were selected before, add to removed
      setRemovedTerms(old => {
        let newRTerms = old.slice();
        newRTerms.push([name, path]);
        return newRTerms;
      });
    }
    // This event is needed to pass on to all branches so they un-check selected term
    var removedEvent = new CustomEvent('term-unselected', {
          bubbles: true,
          cancelable: true,
          detail: [name, path]
        });
    document.dispatchEvent(removedEvent);
  }

  let onDone = () => {
    onClose(selectedTerms, removedTerms);
    setRemovedTerms([]);
  }

  let onCancel = () => {
    onClose();
    setRemovedTerms([]);
  }

  // Construct a branch element for rendering
  let constructBranch = (id, path, name, ischildnode, defaultexpanded, focused, hasChildren) => {
    return(
      <VocabularyBranch
        id={id}
        path={path}
        name={name.trim()}
        onTermClick={onTermClick}
        onCloseInfoBox={onCloseInfoBox}
        registerInfo={registerInfo}
        getInfo={getInfo}
        expands={ischildnode}
        defaultOpen={defaultexpanded}
        key={id}
        headNode={!ischildnode}
        focused={focused}
        onError={onError}
        knownHasChildren={!!hasChildren}
        selectorComponent={selectorComponent}
        onTermSelected={addOption}
        onTermUnselected={removeOption}
        currentSelection={selectedTerms?.map(item => item[VALUE_POS])}
        maxAnswers={maxAnswers}
      />
    );
  }

  return (
    <ResponsiveDialog
      title={`${vocabulary.name} (${vocabulary.acronym})` || "Related terms"}
      withCloseButton={!allowTermSelection}
      open={open}
      ref={browserRef}
      onClose={onCancel}
      className={classes.dialog}
      classes={{
        paper: classes.dialogPaper,
        root: classes.infoDialog
      }}
      {...rest}
    >
      { allowTermSelection && <>
        <div className={classes.selectionContainer}>
          <Typography variant="body2" component="span">{questionDefinition?.text}:</Typography>
          { selectedTerms?.filter(i => i[LABEL_POS]).map(s =>
             <Chip
               key={s[VALUE_POS]}
               variant="outlined"
               size="small"
               color="primary"
               label={s[LABEL_POS]}
               onClick={() => onTermClick(s[VALUE_POS])}
               onDelete={() => removeOption(...s)}
               className={classes.selectionChips}
             />
           )}
        </div>
        <div className={classes.browserAnswerInstrustions}>
          <AnswerInstructions
            className={classes.answerInstrustions}
            currentAnswers={selectedTerms.length}
            {...questionDefinition}
          />
        </div>
      </>
      }
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
      { allowTermSelection &&
        <DialogActions>
          <Button color="primary"
                  onClick={onDone}
                  variant="contained"
                  disabled={maxAnswers > 0 && selectedTerms.length > maxAnswers
                         || removedTerms.length == 0
                            && selectedTerms.every(([a1,a2]) => initialSelection.find(([s1,s2]) => a2 === s2))}
                  className={classes.browseAction} >
              Done
          </Button>
          <Button color="default"
                  onClick={onCancel}
                  variant="contained"
                  className={classes.browseAction} >
            Cancel
          </Button>
        </DialogActions>
      }
    </ResponsiveDialog>
  );
}

VocabularyTree.propTypes = {
  open: PropTypes.bool.isRequired,
  path: PropTypes.string.isRequired,
  onTermClick: PropTypes.func,
  registerInfo: PropTypes.func.isRequired,
  getInfo: PropTypes.func.isRequired,
  onClose: PropTypes.func.isRequired,
  onCloseInfoBox: PropTypes.func,
  onError: PropTypes.func.isRequired,
  browserRef: PropTypes.object.isRequired,
  browseRoots: PropTypes.bool,
  vocabulary: PropTypes.object,
  allowTermSelection: PropTypes.bool,
  initialSelection: PropTypes.array,
  questionDefinition: PropTypes.object,
  classes: PropTypes.object.isRequired
};

export default withStyles(BrowseTheme)(VocabularyTree);
