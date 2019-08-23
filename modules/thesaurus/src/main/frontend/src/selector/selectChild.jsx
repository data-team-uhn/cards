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
import { Button, Checkbox, ListItem, withStyles } from "@material-ui/core"
import { Close } from "@material-ui/icons"
import SelectorStyle from "./selectorStyle.jsx"

// Child element that will be inserted to the target DOM
class VocabularyChild extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      checked: false,
    }
  }

  render() {
    const {name, id, isPreselected, onClick} = this.props;
    return (
      <React.Fragment>
        <ListItem key={name}>
          { /* This is either a Checkbox if this is a suggestion, or a button otherwise */
          isPreselected ?
          (
            <Checkbox
              checked={this.state.checked}
              onChange={() => {onClick(name); this.toggleCheck()}}
            />
          ) : (
            <Button onClick={() => {onClick(name)}}>
              <Close />
            </Button>
          )
          }
          {name}
        </ListItem>
        {
          /* Add the hidden inputs if this is a user input selection (i.e. !isPreselected)
             or if this is a suggestion that is checked */
          (!isPreselected || this.state.checked) ?
          (
          <React.Fragment>
            <input type="hidden" name="name" value={name} />
            <input type="hidden" name="id" value={id} />
          </React.Fragment>
          ) : ""
        }
      </React.Fragment>
    );
  }

  toggleCheck = () => {
    this.setState({checked: !this.state.checked});
  }
};

VocabularyChild.defaultProps = {
  isPreselected: false,
  source: "hpo",
  max: 999,
};

export default withStyles(SelectorStyle)(VocabularyChild);
