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
import { Button, Checkbox, CircularProgress, IconButton, Radio, Tooltip, Typography } from '@material-ui/core';

import Info from "@material-ui/icons/Info";
import ArrowDown from "@material-ui/icons/KeyboardArrowDown";
import ArrowRight from "@material-ui/icons/KeyboardArrowRight";
import More from "@material-ui/icons/MoreHoriz";

import BrowseTheme from "./browseStyle.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";

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
//  addCheckbox: whether or not to add a checkbox control for term selection
//  addRadio: whether or not to add a radio control for term selection
//  addOption: Function to process term selected in browser
//  initialSelection: Existing answers
//  removeOption: Function to remove added answer
//
function VocabularyBranch(props) {
  const { defaultOpen, id, path, name, onTermClick, onCloseInfoBox, registerInfo, getInfo, expands, headNode, focused, onError,
    knownHasChildren, addCheckbox, addRadio, addOption, removeOption, initialSelection, classes } = props;

  const [ lastKnownID, setLastKnownID ] = useState();
  const [ currentlyLoading, setCurrentlyLoading ] = useState(typeof knownHasChildren === "undefined" && expands);
  const [ loadedChildren, setLoadedChildren ] = useState(false);
  const [ hasChildren, setHasChildren ] = useState(knownHasChildren);
  const [ childrenData, setChildrenData ] = useState();
  const [ children, setChildren ] = useState([]);
  const [ expanded, setExpanded ] = useState(defaultOpen);
  const [ selectedPaths, setSelectedPaths] = useState(initialSelection ? initialSelection.map(item => item[1]) : []);

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
      setHasChildren(data["lfs:children"].length > 0);
      setChildrenData(data["lfs:children"]);
      buildChildren(data["lfs:children"]);
    } else {
      onError("Error: children lookup failed with code " + status);
    }
  }

  // Given information about our children, create elements to display their data
  let buildChildren = (data) => {
    var children = data.map((row, index) =>
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
        knownHasChildren={row["lfs:hasChildren"]}
        addCheckbox={addCheckbox}
        addRadio={addRadio}
        addOption={addOption}
        removeOption={removeOption}
        initialSelection={initialSelection}
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

  let onTermSelect = (evt, path, name) => {
    evt.stopPropagation();
    if (evt.target.checked) {
      let newPaths = selectedPaths.slice();
      newPaths.push(path);
      setSelectedPaths(newPaths);
      addOption(name, path);
    } else {
      let newPaths = selectedPaths.filter(item => item!= path);
      setSelectedPaths(newPaths);
      removeOption(name, path);
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

      {/* Term name */}
      <Typography onClick={(evt) => {(evt.target.type != "checkbox") && loadTerm(id, path)}}
                  className={classes.infoName + (focused ? (" " + classes.focusedTermName) : " ")}
                  component="div">
        {/* Browser term select tools */}
	    { addCheckbox &&
	      <Checkbox
	        color="secondary"
	        checked={selectedPaths.includes(path)}
	        onClick={(evt) => {event.stopPropagation(); onTermSelect(evt, path, name);}}
	      /> }
	    { addRadio &&
	       <Radio
	         checked={selectedPaths.includes(path)}
	         color="secondary"
	         onChange={(evt) => {event.stopPropagation(); onTermSelect(evt, path, name);}}
	       /> }
        {name.split(" ").length > 1 ? name.split(" ").slice(0,-1).join(" ") + " " : ''}
        <span className={classes.infoIcon}>
          {name.split(" ").pop()}&nbsp;
          {/* Button to open info page */}
          <IconButton
            size="small"
            color="primary"
            buttonRef={(node) => {registerInfo(id, node)}}
            onClick={(event) => {event.stopPropagation(); getInfo(path)}}
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
  addCheckbox: PropTypes.bool,
  addRadio: PropTypes.bool,
  addOption: PropTypes.func,
  removeOption: PropTypes.func,
  initialSelection: PropTypes.array,
  classes: PropTypes.object.isRequired
};

export default withStyles(BrowseTheme)(VocabularyBranch);
