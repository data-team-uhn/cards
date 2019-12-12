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

import { IconButton, Input, withStyles, InputAdornment } from "@material-ui/core";
import Search from "@material-ui/icons/Search";
import HeaderStyle from "./headerStyle.jsx";

const QUERY_URL = "/query?native=";

function SearchBar(props) {
  const { classes } = props;
  const [ search, setSearch ] = useState("");
  const [ timer, setTimer ] = useState();
  const [ error, setError ] = useState();

  let changeSearch = (query) => {
    // Reset the timer if it exists
    if (timer !== null) {
      clearTimeout(timer);
    }

    setTimer(setTimeout(runQuery, 500));
    setSearch(query);
  }

  let runQuery = () => {
    let new_url = QUERY_URL + encodeURIComponent(
      "select * from [lfs:Form] WHERE native('lucene', " + search.replace(/['\\]/g, "\\$1") + ")");
    fetch(new_url)
      .then(response => response.ok ? response.json() : Promise.reject(response))
      .then(displayResults)
      .catch(handleError)
  }

  let displayResults = (json) => {
    // Parse out the top 5 and display in popper?
  }

  let handleError = (response) => {
    setError(response)
  }

  return(
    <Input
      type="text"
      placeholder="Search"
      value={search}
      onChange={(event) => changeSearch(event.target.value)}
      endAdornment={
        <InputAdornment position="end">
          <IconButton>
            <Search />
          </IconButton>
        </InputAdornment>
      }
      className={classes.search}
      />
  );
}

export default withStyles(HeaderStyle)(SearchBar);