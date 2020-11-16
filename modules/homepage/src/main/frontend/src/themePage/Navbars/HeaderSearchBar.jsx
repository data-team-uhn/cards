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

import { ListItemText, ListItemAvatar, Typography, withStyles }  from "@material-ui/core";
import HeaderStyle from "../../headerStyle.jsx";
import SearchBar from "../../SearchBar.jsx"; // In the commons module
import { getEntityIdentifierLink } from "../EntityIdentifier.jsx";
import { QuickSearchMatch, MatchAvatar } from "./QuickSearchIdentifier.jsx";

// Location of the quick search result metadata in a node, outlining what needs to be highlighted
const LFS_QUERY_MATCH_KEY = "lfs:queryMatch";

function HeaderSearchBar(props) {
  const { classes, doNotEscapeQuery, ...rest } = props;

  // Display a quick search result
  // If it's a resource, show avatar, category, and title
  // Otherwise, if it's a generic entry, simply display the name
  function quickSearchResult(props) {
    let { resultData } = props;
    return <React.Fragment>
              <ListItemAvatar>
                {MatchAvatar(resultData, classes)}
              </ListItemAvatar>
              <ListItemText
                primary={getEntityIdentifierLink(resultData)}
                secondary={QuickSearchMatch(resultData[LFS_QUERY_MATCH_KEY], classes)}
                className={classes.dropdownItem}
              />
           </React.Fragment>
  }

  return(
    <SearchBar
      resultConstructor={quickSearchResult}
      {...rest}
      />
  );
}

HeaderSearchBar.propTypes = {
  invertColors: PropTypes.bool,
  doNotEscapeQuery: PropTypes.bool
}

export default withStyles(HeaderStyle)(withRouter(HeaderSearchBar));
