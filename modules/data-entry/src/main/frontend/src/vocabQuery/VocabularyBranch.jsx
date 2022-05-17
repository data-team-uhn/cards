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

import withStyles from '@mui/styles/withStyles';
import { Button, CircularProgress, IconButton, Tooltip, Typography } from '@mui/material';

import Info from "@mui/icons-material/Info";
import ArrowDown from "@mui/icons-material/KeyboardArrowDown";
import ArrowRight from "@mui/icons-material/KeyboardArrowRight";
import More from "@mui/icons-material/MoreHoriz";

import BrowseTheme from "./browseStyle.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";
import { LABEL_POS, VALUE_POS } from "../questionnaire/Answer";

// Component that renders an element of the VocabularyTree, with expandable children.
//
// Required arguments:
//  defaultOpen: Boolean representing whether or not the branch is open
//  id: Term id to search
//  path: Term @path to get the term info
//  name: Text to display
//  registerInfo: callback to add a possible hook point for the info box
//  getInfo: callback to change the currently displayed info box term
//  expands: boolean determining whether or not to allow this child to display its children
//  headNode: boolean determining whether or not this node is the topmost node in the browser
//  onError: callback when an error occurs
//  knownHasChildren: Boolean representing whether or not the term has children
//
// Optional arguments:
//  onTermClick: callback when a term's label is clicked
//  onCloseInfoBox: Callback to close term info box
//  focused: boolean determining whether this entry is focused and should be visually emphasized
//           (a focused term entry is displayed as a root of a subtree, with only its parents above and its descendants below)
//  selectorComponent: term selector component: Checkbox or Radio control
//  onTermSelected: Function to process term selected in browser
//  currentSelection: The ids of the terms that have been marked as selected do far in the vocabulary browser
//  onTermUnselected: Function to remove added answer
//  maxAnswers: maximum answers allowed for the vocabulary question
//  parentId: id of a parent brunch term
//
function VocabularyBranch(props) {
  const { defaultOpen, id, path, name, onTermClick, onCloseInfoBox, registerInfo, getInfo, expands, headNode, focused, onError,
    knownHasChildren, selectorComponent, onTermSelected, onTermUnselected, currentSelection, maxAnswers, parentId, classes } = props;

  const [ lastKnownID, setLastKnownID ] = useState();
  const [ currentlyLoading, setCurrentlyLoading ] = useState(typeof knownHasChildren === "undefined" && expands);
  const [ loadedChildren, setLoadedChildren ] = useState(false);
  const [ hasChildren, setHasChildren ] = useState(knownHasChildren);
  const [ childrenData, setChildrenData ] = useState();
  const [ children, setChildren ] = useState([]);
  const [ expanded, setExpanded ] = useState(defaultOpen);
  const [ selectedPaths, setSelectedPaths] = useState(currentSelection || []);
  const SelectorComponent = selectorComponent;

  useEffect(() => {
    // Add path to selectedPaths upon term selection from other branches
    window.addEventListener('term-selected', addPath);
    // Remove path from selectedPaths upon term removal from chips list
    window.addEventListener('term-unselected', removePath);
    // Update selected path and radio buttons state upon term selection change if single answer question
    maxAnswers == 1 && window.addEventListener('term-changed', updatePath);

    return () => {
      window.removeEventListener('term-selected', addPath);
      window.removeEventListener('term-unselected', removePath);
      maxAnswers == 1 && window.removeEventListener('term-changed', updatePath);
    };
  });

  let updatePath = (evt) => {
    let path = evt.detail[VALUE_POS];
    setSelectedPaths([path]);
  }

  let addPath = (evt) => {
    let path = evt.detail[VALUE_POS];
    setSelectedPaths(old => {
        let newPaths = old.slice();
        newPaths.push(path);
        return newPaths;
    });
  }

  let removePath = (evt) => {
    let path = evt.detail[VALUE_POS];
    setSelectedPaths(old => {
        let newPaths = old.filter(item => item != path);
        return newPaths;
    });
  }

  let loadTerm = (id, path) => {
    if (focused) return;
    if (onTermClick) {
      setCurrentlyLoading(true);
      onTermClick(path);
    } else {
      toggleShowChildren();
      onCloseInfoBox && onCloseInfoBox();
    }
  }

  let checkForChildren = () => {
    setLastKnownID(id);
    setCurrentlyLoading(true);
    // Determine if this node has children
    var url = new URL(path + ".info.json", window.location.origin);
    MakeRequest(url, updateChildrenData);
  }

  // Callback from checkForChildren to update whether or not this node has children
  // This does not recreate the child elements
  let updateChildrenData = (status, data) => {
    setCurrentlyLoading(false);
    if (status === null) {
      setHasChildren(data["cards:children"].length > 0);
      setChildrenData(data["cards:children"]);
      buildChildren(data);
    } else {
      onError("Error: children lookup failed with code " + status);
    }
  }

  // Given information about our children, create elements to display their data
  let buildChildren = (data) => {
    var children = data["cards:children"].map((row, index) =>
      (<VocabularyBranch
        classes={classes}
        id={row["identifier"]}
        path={row["@path"]}
        name={row["label"].trim()}
        onTermClick={onTermClick}
        onCloseInfoBox={onCloseInfoBox}
        registerInfo={registerInfo}
        getInfo={getInfo}
        expands={true}
        defaultOpen={false}
        key={index}
        headNode={false}
        onError={onError}
        knownHasChildren={row["cards:hasChildren"]}
        selectorComponent={selectorComponent}
        onTermSelected={onTermSelected}
        onTermUnselected={onTermUnselected}
        currentSelection={selectedPaths}
        maxAnswers={maxAnswers}
        parentId={data["identifier"]}
      />)
      );
    setLoadedChildren(true);
    setChildren(children);
    setExpanded(true);
  }

  // Update state with children elements
  let loadChildren = () => {
    // Prevent ourselves from reloading children if we've already loaded children or if we're
    // in the middle of grabbing data
    if (loadedChildren || !hasChildren || currentlyLoading) {
      return;
    }

    // See if we have the data necessary to build the children yet or not
    if (childrenData) {
      buildChildren(childrenData);
    } else {
      checkForChildren();
    }
  }

  let expandAction = (icon, title, clickHandler) => {
    let button = (
      <IconButton
        color={clickHandler ? "primary" : "default"}
        size="small"
        className={currentlyLoading ? ' ' + classes.loadingBranch : undefined}
        onClick={clickHandler}
        disabled={!clickHandler}
      >
        { icon }
        { currentlyLoading && <CircularProgress size={12} /> }
      </IconButton>
    );
    return (
      <div className={classes.expandAction}>
        { title && clickHandler ?
          <Tooltip title={title}>{button}</Tooltip>
          :
          button
        }
      </div>
    );
  }

  let toggleShowChildren = () => {
    // Prevent a race condition when rapidly opening/closing
    // by loading children here, and stopping it from loading
    // children again
    if (!loadedChildren) {
      loadChildren();
    } else {
      setExpanded(!expanded);
    }
  }

  let onSelectionChanged = (evt) => {
    evt.stopPropagation();
    if (evt.target.checked) {
      if (maxAnswers == 1) {
        setSelectedPaths([path]);
        onTermSelected(name, path);
        // This event is needed to pass on to all branches so they update radio buttons states
        var changedEvent = new CustomEvent('term-changed', {
          bubbles: true,
          cancelable: true,
          detail: [name, path]
        });
        document.dispatchEvent(changedEvent);
        return;
      } else {
        let newPaths = selectedPaths.slice();
        newPaths.push(path);
        setSelectedPaths(newPaths);
        onTermSelected(name, path);
      }
    } else {
      let newPaths = selectedPaths.filter(item => item != path);
      setSelectedPaths(newPaths);
      onTermUnselected(name, path);
    }
  }

  // Ensure we know whether or not we have children, if this is expandable
  if (expands) {
    // Ensure our child list entries are built, if this is currently expanded
    if (expanded) {
      // If our ID has changed, we need to fully reload our children
      if (lastKnownID != id) {
        checkForChildren();
      } else {
        loadChildren();
      }
    }
  }

  return(
    <div key={id} className={headNode ? "" : classes.branch}>
      {/* Expand button ▼ */}
      { hasChildren ?
        expandAction(
          expanded ? <ArrowDown/> : <ArrowRight/>,
          expanded ? "Collapse" : "Expand",
          toggleShowChildren
        )
        :
        ( expands ?
          expandAction(<ArrowRight/>)
          :
          expandAction(<More/>, "Show parent categories", () => loadTerm(id, path))
        )
      }
      {/* Browser term select tools */}
        { SelectorComponent && <SelectorComponent
          checked={selectedPaths.includes(path)}
          color="secondary"
          onChange={onSelectionChanged}
          onClick={event => event.stopPropagation()}
          className={classes.termSelector}
        /> }
      {/* Term name */}
      <Typography onClick={() => loadTerm(id, path)}
                  className={classes.infoName + (focused ? (" " + classes.focusedTermName) : " ")}
                  component="div">
        {name.split(" ").length > 1 ? name.split(" ").slice(0,-1).join(" ") + " " : ''}
        <span className={classes.infoIcon}>
          {name.split(" ").pop()}&nbsp;
          {/* Button to open info page */}
          <IconButton
            size="small"
            color="primary"
            buttonRef={(node) => {registerInfo(id + parentId, node)}}
            onClick={(event) => {event.stopPropagation(); getInfo(path, parentId)}}
            className={classes.infoButton}
          >
            <Info color="primary" fontSize="small" className={classes.infoButton}/>
          </IconButton>
        </span>
      </Typography>

      {/* Children */}
      <div className={classes.childDiv + ((expands && expanded) ? " " : (" " + classes.hiddenDiv)) }> {children} </div>
    </div>
  );
}

VocabularyBranch.propTypes = {
  defaultOpen: PropTypes.bool.isRequired,
  id: PropTypes.string.isRequired,
  path: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired,
  onTermClick: PropTypes.func,
  onCloseInfoBox: PropTypes.func,
  registerInfo: PropTypes.func.isRequired,
  getInfo: PropTypes.func.isRequired,
  expands: PropTypes.bool.isRequired,
  headNode: PropTypes.bool.isRequired,
  focused: PropTypes.bool,
  onError: PropTypes.func.isRequired,
  knownHasChildren: PropTypes.bool.isRequired,
  selectorComponent: PropTypes.object,
  onTermSelected: PropTypes.func,
  onTermUnselected: PropTypes.func,
  currentSelection: PropTypes.array,
  maxAnswers: PropTypes.number,
  parentId: PropTypes.string,
  classes: PropTypes.object.isRequired
};

VocabularyBranch.defaultProps = {
  parentId: ""
};

export default withStyles(BrowseTheme)(VocabularyBranch);
