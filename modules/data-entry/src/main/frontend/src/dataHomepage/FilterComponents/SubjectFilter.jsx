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

import React, { forwardRef, useState } from "react";
import { Avatar, InputAdornment, ListItemAvatar, ListItemText, Tooltip, withStyles } from "@material-ui/core";
import DescriptionIcon from "@material-ui/icons/Description";
import ErrorIcon from "@material-ui/icons/Error";
import PropTypes from "prop-types";

import SearchBar from "../../SearchBar.jsx";
import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS } from "./FilterComparators.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS);

/**
 * Display a filter on the associated subject of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 * 
 * @param {func} onChangeInput Function to call when this filter has chosen a new subject
 * @param {func} questionDefinition Unused, here to stop a warning when it is passed to the SearchBar component
 * Other props will be forwarded to the SearchBar component
 */
const SubjectFilter = forwardRef((props, ref) => {
  const { classes, onChangeInput, questionDefinition, ...rest } = props;
  const [ error, setError ] = useState();
  const [ hasSelectedValidSubject, setHasSelectedValidSubject ] = useState(true); // Default true since having nothing entered is valid

  let invalidateInput = (event) => {
    // The results are only valid after new text has been typed if they have emptied the input box
    setHasSelectedValidSubject(event.target.value == "");
  }

  // Pass information about a selected subject upwards
  let selectSubject = (event, row) => {
    onChangeInput(row["jcr:uuid"], row["identifier"]);
    setHasSelectedValidSubject(true);
    setError(false);
  }

  let constructQuery = (query, requestID) => {
    let url = new URL("/query", window.location.origin);
    let sqlquery = "SELECT s.* FROM [lfs:Subject] as s" + (query.search ? ` WHERE CONTAINS(s.'identifier', '*${query}*')` : "");
    url.searchParams.set("query", sqlquery);
    url.searchParams.set("limit", query.pageSize);
    url.searchParams.set("offset", query.page*query.pageSize);
    url.searchParams.set("req", requestID);
    return(url);
  }

  // Close the popper, and ensure that the currently entered field is correct
  let closePopper = () => {
    if (!hasSelectedValidSubject) {
      setError("Invalid subject selected");
    }
  }

  // Generate a human-readable info about the subject matching the query
  function QuickSearchResultHeader(props) {
    const {resultData} = props;
    return resultData && (
      <div>
        {resultData?.identifier || resultData["jcr:uuid"] || ''}
      </div>
    ) || null
  }

  // Display a quick search result
  // If it's a resource, show avatar, category, and title
  // Otherwise, if it's a generic entry, simply display the name
  let QuickSearchResult = (props) => (
    <React.Fragment>
      <ListItemAvatar><Avatar className={classes.searchResultAvatar}><DescriptionIcon /></Avatar></ListItemAvatar>
      <ListItemText
        primary={(<QuickSearchResultHeader resultData={props.resultData} />)}
        className={classes.dropdownItem}
      />
    </React.Fragment>
  )

  return (
    <SearchBar
      onChange={invalidateInput}
      onPopperClose={closePopper}
      onSelect={selectSubject}
      queryConstructor={constructQuery}
      resultConstructor={QuickSearchResult}
      error={!!error /* Turn into a boolean to prevent PropTypes warnings */}
      className={(hasSelectedValidSubject ? "" : classes.invalidSubjectText)}
      startAdornment={
        error && <InputAdornment position="end">
          <Tooltip title={error}>
            <ErrorIcon />
          </Tooltip>
        </InputAdornment> || undefined
        }
      {...rest}
      />
  )
});

SubjectFilter.propTypes = {
  onChangeComparator: PropTypes.func,
  onChangeInput: PropTypes.func
}

const StyledSubjectFilter = withStyles(QuestionnaireStyle)(SubjectFilter)

export default StyledSubjectFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType == 'subject') {
    return [COMPARATORS, StyledSubjectFilter, 50];
  }
});
