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

import Thesaurus from "../query/query.jsx";
import SelectorStyle from "./selectorStyle.jsx";

class VocabularySelector extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        title: "LFS Patients",
      };
    }

    render() {
        const {name, source, suggestionCategories, max, selectionContainer, ...rest} = this.props;
        return (
            <Thesaurus
                onClick = {this.addSelection}
                {...rest}
            ></Thesaurus>
        );
    }

    // Create a new child from the selection with parent
    addSelection = (event) => {
        // Create a new child based on what was clicked
        var selection = event.target.innerText;
        console.log(event.target);
    }
}

VocabularySelector.propTypes = {
    classes: PropTypes.object.isRequired,
    name: PropTypes.string,
    source: PropTypes.string.isRequired,
    max: PropTypes.number.isRequired,
};

VocabularySelector.defaultProps = {
    name: "VocabularySelector",
    source: "hpo",
    max: 999,
};

export default withStyles(SelectorStyle)(VocabularySelector);
