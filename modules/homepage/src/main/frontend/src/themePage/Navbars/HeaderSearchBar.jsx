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
import PropTypes from "prop-types";
import React from "react";
import { withRouter } from "react-router-dom";

import withStyles from '@material-ui/styles/withStyles';
import HeaderStyle from "../../headerStyle.jsx";
import SearchBar from "../../SearchBar.jsx"; // In the commons module
import { QuickSearchIdentifier } from "./QuickSearchIdentifier.jsx";

function HeaderSearchBar(props) {
  const { classes, doNotEscapeQuery, ...rest } = props;

  return(
    <SearchBar
      resultConstructor={QuickSearchIdentifier}
      onSelect={() => {}}
      showAllResultsLink={true}
      {...rest}
      />
  );
}

HeaderSearchBar.propTypes = {
  invertColors: PropTypes.bool,
  doNotEscapeQuery: PropTypes.bool
}

export default withStyles(HeaderStyle)(withRouter(HeaderSearchBar));
