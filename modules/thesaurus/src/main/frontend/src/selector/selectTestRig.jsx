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
// @material-ui/core
import VocabularySelector from "./select.jsx";

class VocabularyTester extends React.Component {
  constructor(props) {
    super(props);
    this.outsideRef = React.createRef();

    this.state = {
      outsideNode: undefined
    }
  }

  render() {
    return(
      <React.Fragment>
        <div>
            Inside div (Skin-related vocab only):
            <VocabularySelector
              selectionContainer = {this.state.outsideNode}
              suggestionCategories = {["HP:0000951"]}
              defaultSuggestions = {{"abc": "given", "HP:0031840": undefined}}
              max = {3}
            ></VocabularySelector>
        </div>
        <div
          ref = {this.outsideRef}
          onClick = {this.movePortal}
        >
          Outside div (click this label to toggle portal):
        </div>
      </React.Fragment>
    );
  }

  // Debug function to move the portal around the DOM
  movePortal = () => {
    if (typeof this.state.outsideNode === "undefined") {
      this.setState({outsideNode: this.outsideRef.current});
    } else {
      this.setState({outsideNode: undefined});
    }
  }
}

export default VocabularyTester