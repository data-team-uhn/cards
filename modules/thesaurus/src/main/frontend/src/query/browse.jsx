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
import classNames from "classnames";
import React from "react";
import PropTypes from "prop-types";
// @material-ui/core
import { withStyles } from "@material-ui/core/styles";
import { Dialog, Typography } from '@material-ui/core';
// material-dashboard-react
import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
// @material-ui/icons
import Info from "@material-ui/icons/Info";

import BrowseTheme from "./browseStyle.jsx";

class BrowseDialog extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      title: "Term browser",
      lastKnownTerm: "",
      parentNode: null,
      currentNode: null,
      childNodes: [],
      childTermsToLookup: [],
    };
  }

  changeID = (id) => {
    this.setState({
      id: id,
    });
  }

  // Construct a branch element for rendering
  constructBranch = (id, name) => {
    return(
      <div key={id} className={this.props.classes.branch}>
        {/* Listitem button ▼ */}
        <Button
          onClick={() => this.props.changeid(id)}
          variant="text"
          simple={true}
          color="info"
          className={this.props.classes.browseitem}
          >
          <Typography inline className={this.props.classes.infoDataSource}>{id}&nbsp;</Typography>
          <Typography inline className={this.props.classes.infoName}> {name}</Typography>
        </Button>

        {/* Button to open info page */}
        <Button
          buttonRef={(node) => {this.props.registerinfo(id, node)}}
          color="info"
          justIcon={true}
          simple={true}
          onClick={() => {this.props.getinfo(id)}}
          className={this.props.classes.buttonLink}
        >
          <Info color="primary" />
        </Button>
        <br />
      </div>
    );
  }

  rebuildChildren = (event, data) => {
    if (event === null) {
      // We have the children of our parent
      const childBranches = data["rows"].map((row, index) => {
        return this.constructBranch(row["id"], row["name_translated"]);
      })

      this.setState({
        childNodes: childBranches
      })
    } else {
      console.log("Error: term lookup failed with code " + event.ToString());
    }
  }

  rebuildTree = (event, data) => {
    if (event === null) {
      // We have the node we're looking at, and its parent.
      var currentNodeData = data["rows"][0];
      var id = currentNodeData["id"];
      var escapedId = id.replace(":", "%5C%3A");

      // Next, look up every child of this node
      var URL = "https://services.phenotips.org/rest/vocabularies/hpo/suggest?sort=nameSort%20asc&maxResults=10000&input=" + id
              + "&customFilter=is_a:" + escapedId;
      var xhr = window.Sling.getXHR();
      xhr.open('GET', URL, true);
      xhr.responseType = 'json';
      xhr.onload = () => {
        var status = xhr.status;
        if (status === 200) {
          this.rebuildChildren(null, xhr.response);
        } else {
          this.rebuildChildren(status, xhr.response);
        }
      };
      xhr.send();

      const parentBranches = currentNodeData["parents"].map((row, index) => {
        return this.constructBranch(row["id"], row["name_translated"]);
      });

      this.setState({
        parentNode: parentBranches,
        currentNode: this.constructBranch(currentNodeData["id"], currentNodeData["name_translated"]),
        lastKnownTerm: id,
      })
    } else {
      console.log("Error: initial term lookup failed with code " + event.ToString());
    }
  }

  getSuggestions = (id) => {
    // Do not re-grab suggestions for the same term
    if (id === this.state.lastKnownTerm) {
      return;
    }

    // If we are empty, remove everything
    if (id === "" || id === null) {
      this.setState({
        parentNode: null,
        currentNode: null,
        childNodes: [],
        childTermsToLookup: [],
        lastKnownTerm: id,
      })
      return;
    }

    var URL = "https://services.phenotips.org/rest/vocabularies/hpo/suggest?sort=nameSort%20asc&maxResults=10000&input=" + id;

    var xhr = window.Sling.getXHR();
    xhr.open('GET', URL, true);
    xhr.responseType = 'json';
    xhr.onload = () => {
      // Immediately refresh lastKnownTerm to prevent infinite loops
      this.setState({
        lastKnownTerm: id,
      })

      var status = xhr.status;
      if (status === 200) {
        this.rebuildTree(null, xhr.response);
      } else {
        this.rebuildTree(status, xhr.response);
      }
    };
    xhr.send();
  }

  render() {
    const { classes, term, changeid, registerinfo, getinfo, changeinfoid, ...rest } = this.props;
    const fullscreen = false;
    this.getSuggestions(term);

    return (
      <Dialog fullscreen={fullscreen.toString()} {...rest}>
        <div className={classes.treeRoot}>
          {this.state.parentNode}
        </div>
        <div className={classes.treeNode}>
          {this.state.currentNode}
        </div>
        <div className={classes.childrenNodes}>
          {this.state.childNodes}
        </div>
      </Dialog>
    );
  }
}

BrowseDialog.propTypes = {
  classes: PropTypes.object.isRequired
};

export default withStyles(BrowseTheme)(BrowseDialog);