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
import { Button, CircularProgress, Typography } from '@material-ui/core';
// @material-ui/icons
import Info from "@material-ui/icons/Info";

import BrowseTheme from "./browseStyle.jsx";
import { MakeChildrenFindingRequest } from "./util.jsx";

// Component that renders an element of the VocabularyBrowser, with expandable children.
//
// Required arguments:
//  id: Term to search
//  name: Text to display
//  changeId: callback to change the term being looked up
//  registerInfo: callback to add a possible hook point for the info box
//  getInfo: callback to change the currently displayed info box term
//  expands: boolean determining whether or not to allow this child to display its children
//  headNode: boolean determining whether or not this node is the topmost node in the browser
//  bolded: boolean determining whether or not to bold this entry
//  onError: callback when an error occurs
//  vocabulary: Name of the vocabulary to use to look up terms
//
// Optional arguments:
//  fullscreen: whether or not the dialog is fullscreen (default: false)
function ListChild(props) {
  const { classes, defaultOpen, id, name, changeId, registerInfo, getInfo, expands, headNode, bolded, onError, vocabulary } = props;

  const [ currentlyLoading, setCurrentlyLoading ] = useState(true);
  const [ loadedChildren, setLoadedChildren ] = useState(false);
  const [ checkedForChildren, setCheckedForChildren ] = useState(false);
  const [ hasChildren, setHasChildren ] = useState(false);
  const [ childrenData, setChildrenData ] = useState();
  const [ children, setChildren ] = useState([]);
  const [ expanded, setExpanded ] = useState(defaultOpen);

  let checkForChildren = () => {
    // Prevent ourselves for checking for children if we've already checked for children
    if (checkedForChildren) {
      return;
    }

    // Determine if this node has children
    MakeChildrenFindingRequest(
      vocabulary,
      {input: id},
      updateChildrenStatus
      );
  }

  // Callback from checkForChildren to update whether or not this node has children
  // This does not recreate the child elements
  let updateChildrenStatus = (status, data) => {
    if (status === null) {
      setHasChildren(data["rows"].length > 0);
      setChildrenData(data["rows"]);
      setCheckedForChildren(true);
      setCurrentlyLoading(false);
      if (expanded && !loadedChildren) {
        loadChildren(data["rows"]);
      }
    } else {
      onError("Error: children lookup failed with code " + status);
    }
  }

  // Update state with children elements
  let loadChildren = (data) => {
    // Prevent ourselves from loading children if we've already loaded children
    if (loadedChildren || !hasChildren) {
      return;
    }

    var children = data.map((row, index) => {
      return (<BrowseListChild
                id={row["id"]}
                name={row["name"]}
                changeId={changeId}
                registerInfo={registerInfo}
                getInfo={getInfo}
                expands={true}
                defaultOpen={false}
                key={index}
                headNode={false}
                onError={onError}
                vocabulary={vocabulary}
              />);
    });
    setLoadedChildren(true);
    setChildren(children);
  }

  if (expands) {
    checkForChildren();
  }

  return(
    <div key={id} className={headNode ? "" : classes.branch}>
      {/* Expand button ▼ */}
      <div className={classes.arrowDiv}>
        {(expands && hasChildren) ?
          <Button
            onClick={() => {
              // Prevent a race condition when rapidly opening/closing
              // by loading children here, and stopping it from loading
              // children again
              if (!loadedChildren) {
                loadChildren(childrenData);
              }

              setExpanded(!expanded);
              setLoadedChildren(true);
            }}
            variant="text"
            className={classes.browseitem + " " + classes.arrowButton}
            >
            {expanded ? "▼" : "►"}
          </Button>
          : ""
        }
        {(expands && currentlyLoading) ?
          <CircularProgress size={10} />
          : ""
        }
      </div>

      {/* Listitem button */}
      <Button
        onClick={() => changeId(id)}
        className={classes.browseitem}
        >
        <Typography className={classes.infoDataSource}>{id}&nbsp;</Typography>
        <Typography className={classes.infoName + (bolded ? (" " + classes.boldedName) : " ")}> {name}</Typography>
      </Button>

      {/* Button to open info page */}
      <Button
        buttonRef={(node) => {registerInfo(id, node)}}
        onClick={() => {getInfo(id)}}
        className={classes.buttonLink + " " + classes.infoButton}
      >
        <Info color="primary" fontSize="small" className={classes.infoButton}/>
      </Button>
      <br />

      {/* Children */}
      <div className={classes.childDiv + ((expands && expanded) ? " " : (" " + classes.hiddenDiv)) }> {children} </div>
    </div>
  );
}

ListChild.propTypes = {
    classes: PropTypes.object.isRequired
};

const BrowseListChild = withStyles(BrowseTheme)(ListChild);

export default BrowseListChild;