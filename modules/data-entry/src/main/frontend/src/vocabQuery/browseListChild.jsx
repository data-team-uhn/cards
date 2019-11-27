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
import { Button, CircularProgress, Typography } from '@material-ui/core';
// @material-ui/icons
import Info from "@material-ui/icons/Info";

import BrowseTheme from "./browseStyle.jsx";
import { MakeChildrenFindingRequest } from "./util.jsx";

class ListChild extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      currentlyLoading: true,
      loadedChildren: false,
      checkedForChildren: false,
      hasChildren: false,
      childrenData: null,
      children: [],
      expanded: props.defaultOpen,
    };
  }

  render() {
    const { classes, id, name, changeId, registerInfo, getInfo, expands, headNode, bolded, onError, vocabulary } = this.props;
    if (expands) {
      this.checkForChildren();
    }

    return(
      <div key={id} className={headNode ? "" : classes.branch}>
        {/* Expand button ▼ */}
        <div className={classes.arrowDiv}>
          {(expands && this.state.hasChildren) ?
            <Button
              onClick={() => {
                // Prevent a race condition when rapidly opening/closing
                // by loading children here, and stopping it from loading
                // children again
                if (!this.state.loadedChildren) {
                  this.loadChildren(this.state.childrenData);
                }

                this.setState({
                  expanded: !this.state.expanded,
                  loadedChildren: true,
                });
              }}
              variant="text"
              className={classes.browseitem + " " + classes.arrowButton}
              >
              {this.state.expanded ? "▼" : "►"}
            </Button>
            : ""
          }
          {(expands && this.state.currentlyLoading) ?
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
        <div className={classes.childDiv + ((expands && this.state.expanded) ? " " : (" " + classes.hiddenDiv)) }> {this.state.children} </div>
      </div>
    );
  }

  checkForChildren = () => {
    // Prevent ourselves for checking for children if we've already checked for children
    if (this.state.checkedForChildren) {
      return;
    }

    // Determine if this node has children
    MakeChildrenFindingRequest(
      this.props.vocabulary,
      {input: this.props.id},
      this.updateChildrenStatus
      );
  }

  // Callback from checkForChildren to update whether or not this node has children
  // This does not recreate the child elements
  updateChildrenStatus = (status, data) => {
    if (status === null) {
      this.setState({
        hasChildren: (data["rows"].length > 0),
        childrenData: (data["rows"]),
        checkedForChildren: true,
        currentlyLoading: false,
      });
      if (this.state.expanded && !this.state.loadedChildren) {
        this.loadChildren(data["rows"]);
      }
    } else {
      this.props.onError("Error: children lookup failed with code " + status);
    }
  }

  // Update this.state.children with children elements
  loadChildren = (data) => {
    // Prevent ourselves from loading children if we've already loaded children
    if (this.state.loadedChildren || !this.state.hasChildren) {
      return;
    }

    var children = data.map((row, index) => {
      return (<BrowseListChild
                id={row["id"]}
                name={row["name"]}
                changeId={this.props.changeId}
                registerInfo={this.props.registerInfo}
                getInfo={this.props.getInfo}
                expands={true}
                defaultOpen={false}
                key={index}
                headNode={false}
                onError={this.props.onError}
                vocabulary={this.props.vocabulary}
              />);
    });
    this.setState({
      loadedChildren: true,
      children: children,
    });
  }
}

ListChild.propTypes = {
    classes: PropTypes.object.isRequired
};

const BrowseListChild = withStyles(BrowseTheme)(ListChild);

export default BrowseListChild;