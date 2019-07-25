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
import { withStyles } from "@material-ui/core/styles";
import { Typography } from '@material-ui/core';
// material-dashboard-react
import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";
// @material-ui/icons
import Info from "@material-ui/icons/Info";

import BrowseTheme from "./browseStyle.jsx";

class ListChild extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      loadedChildren: false,
      checkedForChildren: false,
      hasChildren: false,
      children: [],
      expanded: props.defaultopen,
    };
  }

  updateWithChildren = (event, data) => {
    if (event === null) {
      var children = data["rows"].map((row, index) => {
        return (<BrowseListChild
                  id={row["id"]}
                  name={row["name_translated"]}
                  changeid={this.props.changeid}
                  registerinfo={this.props.registerinfo}
                  getinfo={this.props.getinfo}
                  expands={true}
                  defaultopen={false}
                  key={index}
                  headnode={false}
                />);
      })
      this.setState({
        loadedChildren: true,
        children: children,
      });
    } else {
      console.log("Error: children lookup failed with code " + event.ToString());
    }
  }

  loadChildren = () => {
    // Prevent ourselves from loading children if we've already loaded children
    if (this.state.loadedChildren || !this.state.hasChildren) {
      return;
    }

    // Determine the children of this node
    var id = this.props.id;
    var escapedId = id.replace(":", "%5C%3A");
    var URL = "https://services.phenotips.org/rest/vocabularies/hpo/suggest?sort=nameSort%20asc&maxResults=10000&input=" + id
            + "&customFilter=is_a:" + escapedId;
    var xhr = window.Sling.getXHR();
    xhr.open('GET', URL, true);
    xhr.responseType = 'json';
    xhr.onload = () => {
      var status = xhr.status;
      if (status === 200) {
        this.updateWithChildren(null, xhr.response);
      } else {
        this.updateWithChildren(status, xhr.response);
      }
    };
    xhr.send();
  }

  updateChildrenStatus = (event, data) => {
    if (event === null) {
      this.setState({
        hasChildren: (data["rows"].length > 0),
        checkedForChildren: true,
      });
    } else {
      console.log("Error: children lookup failed with code " + event.ToString());
    }
  }

  checkForChildren = () => {
    // Prevent ourselves for checking for children if we've already checked for children
    if (this.state.checkedForChildren) {
      return;
    }

    // Determine if this node has children
    var id = this.props.id;
    var escapedId = id.replace(":", "%5C%3A");
    var URL = "https://services.phenotips.org/rest/vocabularies/hpo/suggest?sort=nameSort%20asc&maxResults=10000&input=" + id
            + "&customFilter=is_a:" + escapedId;
    var xhr = window.Sling.getXHR();
    xhr.open('GET', URL, true);
    xhr.responseType = 'json';
    xhr.onload = () => {
      var status = xhr.status;
      if (status === 200) {
        this.updateChildrenStatus(null, xhr.response);
      } else {
        this.updateChildrenStatus(status, xhr.response);
      }
    };
    xhr.send();
  }

  render() {
    const { classes, id, name, changeid, registerinfo, getinfo, expands, headnode, bolded } = this.props;
    if (expands) {
      this.checkForChildren();
      if (this.state.expanded) {
        this.loadChildren();
      }
    }

    return(
      <div key={id} className={headnode ? "" : classes.branch}>
        {/* Expand button ▼ */}
        <div className={classes.arrowDiv}>
          {(expands && this.state.hasChildren) ?
            <Button
              onClick={() => {this.setState({expanded: !this.state.expanded})}}
              variant="text"
              simple={true}
              color="info"
              className={classes.browseitem}
              >
              {this.state.expanded ? "▼" : "▶"}
            </Button>
            : ""
          }
        </div>

        {/* Listitem button */}
        <Button
          onClick={() => changeid(id)}
          variant="text"
          simple={true}
          color="info"
          className={classes.browseitem}
          >
          <Typography inline className={classes.infoDataSource}>{id}&nbsp;</Typography>
          <Typography inline className={classes.infoName + (bolded ? (" " + classes.boldedName) : " ")}> {name}</Typography>
        </Button>

        {/* Button to open info page */}
        <Button
          buttonRef={(node) => {registerinfo(id, node)}}
          color="info"
          justIcon={true}
          simple={true}
          onClick={() => {getinfo(id)}}
          className={classes.buttonLink + " " + classes.infoButton}
        >
          <Info color="primary" fontSize="18px"/>
        </Button>
        <br />

        {/* Children */}
        <div className={classes.childDiv + ((expands && this.state.expanded) ? " " : (" " + classes.hiddenDiv)) }> {this.state.children} </div>
      </div>
    );
  }
}

ListChild.propTypes = {
    classes: PropTypes.object.isRequired
};

const BrowseListChild = withStyles(BrowseTheme)(ListChild);

export default BrowseListChild;